package com.nalan.animation.core;

import android.os.SystemClock;

import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringListener;

import java.util.concurrent.CopyOnWriteArraySet;

/*
    阻尼效果是在一个点附近进行振荡。物体以velocity运动，必须知道end position然后才能在它附近振荡。
    不像Scroller，阻尼效果无法实现fling效果
 */
public class SpringCalculator {
    // maximum amount of time to simulate per physics iteration in seconds (4 frames at 60 FPS)
    private static final double MAX_DELTA_TIME_SEC = 0.064;
    // fixed timestep to use in the physics solver in seconds
    private static final double SOLVER_TIMESTEP_SEC = 0.001;

    // storage for the current and prior physics state while integration is occurring
    private static class PhysicsState {
        double position;
        double velocity;
    }

    private double mFriction;
    private double mTension;

    // all physics simulation objects are final and reused in each processing pass
    private final PhysicsState mCurrentState = new PhysicsState();
    private final PhysicsState mPreviousState = new PhysicsState();
    private final PhysicsState mTempState = new PhysicsState();

    private double mStartValue;
    private double mEndValue;

    private double mTimeAccumulator = 0;

    // thresholds for determining when the spring is at rest
    private boolean mOvershootClampingEnabled = false;
    private double mRestSpeedThreshold = 0.005;
    private double mDisplacementFromRestThreshold = 0.005;

    public SpringCalculator(){
        this(40,3);
    }

    public SpringCalculator(double tension,double friction){
        mTension = tension;
        mFriction = friction;
    }

    public void setConfig(double tension,double friction){
        mTension = tension;
        mFriction = friction;
    }

    public void start(double startValue,double endValue){
        mStartValue = startValue;
        mEndValue = endValue;

        mCurrentState.position = startValue;
        mTempState.position = startValue;
    }

    public boolean advance(double realDeltaTime){
        realDeltaTime = realDeltaTime/1000.0;

        boolean isFinished = isFinished();

        if (isFinished) {
            return false;
        }

        // clamp the amount of realTime to simulate to avoid stuttering in the UI. We should be able
        // to catch up in a subsequent advance if necessary.
        double adjustedDeltaTime = realDeltaTime;
        if (realDeltaTime > MAX_DELTA_TIME_SEC) {
            adjustedDeltaTime = MAX_DELTA_TIME_SEC;
        }

        mTimeAccumulator += adjustedDeltaTime;

        double tension = mTension;
        double friction = mFriction;

        double position = mCurrentState.position;
        double velocity = mCurrentState.velocity;
        double tempPosition = mTempState.position;
        double tempVelocity = mTempState.velocity;

        double aVelocity, aAcceleration;
        double bVelocity, bAcceleration;
        double cVelocity, cAcceleration;
        double dVelocity, dAcceleration;

        double dxdt, dvdt;

        // iterate over the true time
        while (mTimeAccumulator >= SOLVER_TIMESTEP_SEC) {
            mTimeAccumulator -= SOLVER_TIMESTEP_SEC;

            if (mTimeAccumulator < SOLVER_TIMESTEP_SEC) {
                // This will be the last iteration. Remember the previous state in case we need to
                // interpolate
                mPreviousState.position = position;
                mPreviousState.velocity = velocity;
            }

            // Perform an RK4 integration to provide better detection of the acceleration curve via
            // sampling of Euler integrations at 4 intervals feeding each derivative into the calculation
            // of the next and taking a weighted sum of the 4 derivatives as the final output.

            // This math was inlined since it made for big performance improvements when advancing several
            // springs in one pass of the BaseSpringSystem.

            // The initial derivative is based on the current velocity and the calculated acceleration
            aVelocity = velocity;
            aAcceleration = (tension * (mEndValue - tempPosition)) - friction * velocity;

            // Calculate the next derivatives starting with the last derivative and integrating over the
            // timestep
            tempPosition = position + aVelocity * SOLVER_TIMESTEP_SEC * 0.5;
            tempVelocity = velocity + aAcceleration * SOLVER_TIMESTEP_SEC * 0.5;
            bVelocity = tempVelocity;
            bAcceleration = (tension * (mEndValue - tempPosition)) - friction * tempVelocity;

            tempPosition = position + bVelocity * SOLVER_TIMESTEP_SEC * 0.5;
            tempVelocity = velocity + bAcceleration * SOLVER_TIMESTEP_SEC * 0.5;
            cVelocity = tempVelocity;
            cAcceleration = (tension * (mEndValue - tempPosition)) - friction * tempVelocity;

            tempPosition = position + cVelocity * SOLVER_TIMESTEP_SEC;
            tempVelocity = velocity + cAcceleration * SOLVER_TIMESTEP_SEC;
            dVelocity = tempVelocity;
            dAcceleration = (tension * (mEndValue - tempPosition)) - friction * tempVelocity;

            // Take the weighted sum of the 4 derivatives as the final output.
            dxdt = 1.0/6.0 * (aVelocity + 2.0 * (bVelocity + cVelocity) + dVelocity);
            dvdt = 1.0/6.0 * (aAcceleration + 2.0 * (bAcceleration + cAcceleration) + dAcceleration);

            position += dxdt * SOLVER_TIMESTEP_SEC;
            velocity += dvdt * SOLVER_TIMESTEP_SEC;
        }

        mTempState.position = tempPosition;
        mTempState.velocity = tempVelocity;

        mCurrentState.position = position;
        mCurrentState.velocity = velocity;

        if (mTimeAccumulator > 0) {
            interpolate(mTimeAccumulator / SOLVER_TIMESTEP_SEC);
        }

        // End the spring immediately if it is overshooting and overshoot clamping is enabled.
        // Also make sure that if the spring was considered within a resting threshold that it's now
        // snapped to its end value.
        if (isFinished() || (mOvershootClampingEnabled && isOvershooting())) {
//            if (tension > 0)
//                mCurrentState.position = mEndValue;
//            else
//                mEndValue = mCurrentState.position;
            mCurrentState.position = mEndValue;
            mCurrentState.velocity = 0;
        }

        return true;
    }

    /**
     * linear interpolation between the previous and current physics state based on the amount of
     * timestep remaining after processing the rendering delta time in timestep sized chunks.
     * @param alpha from 0 to 1, where 0 is the previous state, 1 is the current state
     */
    private void interpolate(double alpha) {
        mCurrentState.position = mCurrentState.position * alpha + mPreviousState.position *(1-alpha);
        mCurrentState.velocity = mCurrentState.velocity * alpha + mPreviousState.velocity *(1-alpha);
    }

    public boolean isFinished() {
        return Math.abs(mCurrentState.velocity) <= mRestSpeedThreshold &&
                (getDisplacementDistanceForState(mCurrentState) <= mDisplacementFromRestThreshold ||
                        mTension == 0);
    }

    public double getCurrentDisplacementDistance() {
        return getDisplacementDistanceForState(mCurrentState);
    }

    private double getDisplacementDistanceForState(PhysicsState state) {
        return Math.abs(mEndValue - state.position);
    }

    public boolean isOvershooting() {
        return mTension > 0 &&
                ((mStartValue < mEndValue && getCurrentValue() > mEndValue) ||
                        (mStartValue > mEndValue && getCurrentValue() < mEndValue));
    }

    public double getCurrentValue() {
        return mCurrentState.position;
    }

}

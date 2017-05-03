package com.nalan.animation.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.nalan.animation.gesture.ViewTransHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Administrator on 2017-05-03.
 */

public class SpringCurveView extends View implements ViewTransHelper.Callback{
    private List<Point> pointList;
    private long startTime;
    private double endValue;
    private Paint paint;
    private Path path;

    private Matrix matrix;
    private float[] tempValues;
    private ViewTransHelper transHelper;

    public SpringCurveView(Context context) {
        this(context,null);
    }

    public SpringCurveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        pointList = new ArrayList<>();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);

        path = new Path();
        matrix = new Matrix();
        tempValues = new float[9];
        transHelper = new ViewTransHelper(this,this);
    }

    public void start(long startTime,double endValue){
        this.startTime = startTime;
        this.endValue = endValue;
        pointList.clear();
        invalidate();
    }

    public void add(long time,double value){
        pointList.add(new Point(time,value));
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev){
        return transHelper.processTouchEvent(ev);
    }

    @Override
    public void onDraw(Canvas canvas){
        super.onDraw(canvas);

        int contentHeight = getHeight()-getPaddingTop()-getPaddingBottom();
        int contentWidth = getWidth()-getPaddingLeft()-getPaddingRight();
        canvas.save();
        canvas.translate(getPaddingLeft(),getPaddingTop()+contentHeight/2);

        matrix.getValues(tempValues);
        canvas.translate(tempValues[Matrix.MTRANS_X],tempValues[Matrix.MTRANS_Y]);
        canvas.scale(tempValues[Matrix.MSCALE_X],tempValues[Matrix.MSCALE_Y]);

        path.reset();
        for(int i=0;i<pointList.size();i++){
            Point pt = pointList.get(i);
            float x = i;//pt.time-startTime;
            float y = (float) ((pt.value-endValue)*50);
            if(i==0){
                path.moveTo(x,y);
            }else
                path.lineTo(x,y);
        }
        canvas.drawPath(path,paint);
        canvas.restore();
    }

    @Override
    public boolean canDragHorizontal() {
        return true;
    }

    @Override
    public boolean canDragVertical() {
        return true;
    }

    @Override
    public boolean canScaleHorizontal() {
        return true;
    }

    @Override
    public boolean canScaleVertical() {
        return true;
    }

    @Override
    public float getScaleLevel() {
        return 1.2f;
    }

    @Override
    public void onScale(float sx, float sy, float px, float py) {
        matrix.postScale(sx, sy, px, py);
        invalidate();
    }

    @Override
    public void onDrag(int dx, int dy) {
        matrix.postTranslate(dx,dy);
        invalidate();
    }

    @Override
    public boolean onFling(int dx, int dy) {
        matrix.postTranslate(dx,dy);
        invalidate();
        return false;
    }

    private class Point{
        long time;
        double value;

        Point(long time,double value){
            this.time = time;
            this.value = value;
        }

    }

}

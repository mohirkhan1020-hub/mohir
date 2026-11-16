package com.wevlo.amohir;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DoodleView extends View {

    private static class Stroke {
        Path path;
        int color;
        float width;
        Stroke(Path path, int color, float width) {
            this.path = path;
            this.color = color;
            this.width = width;
        }
    }

    private final List<Stroke> strokes = new ArrayList<>();
    private Path currentPath;
    private int currentColor = Color.parseColor("#25D366");
    private float strokeWidth = 10f;
    private final Paint paint = new Paint();

    public DoodleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setColor(int color) {
        this.currentColor = color;
    }

    public void undo() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            invalidate();
        }
    }

    public boolean hasStrokes() {
        return !strokes.isEmpty();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Stroke s : strokes) {
            paint.setColor(s.color);
            paint.setStrokeWidth(s.width);
            canvas.drawPath(s.path, paint);
        }
        if (currentPath != null) {
            paint.setColor(currentColor);
            paint.setStrokeWidth(strokeWidth);
            canvas.drawPath(currentPath, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentPath = new Path();
                currentPath.moveTo(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (currentPath != null) {
                    currentPath.lineTo(x, y);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (currentPath != null) {
                    currentPath.lineTo(x, y);
                    strokes.add(new Stroke(currentPath, currentColor, strokeWidth));
                    currentPath = null;
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    public Bitmap applyDoodleOn(Bitmap baseBitmap) {
        if (baseBitmap == null) return null;
        Bitmap result = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);

        float scaleX = (float) result.getWidth() / getWidth();
        float scaleY = (float) result.getHeight() / getHeight();
        canvas.scale(scaleX, scaleY);

        for (Stroke s : strokes) {
            paint.setColor(s.color);
            paint.setStrokeWidth(s.width);
            canvas.drawPath(s.path, paint);
        }
        return result;
    }
}

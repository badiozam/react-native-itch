package com.como.RNTScratchView;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.annotation.Nullable;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;

public class ScratchView extends View implements View.OnTouchListener {

    boolean imageTakenFromView = false;
    float threshold = 0;
    float brushSize = 0;
    String imageUrl = null;
    String resourceName = null;
    String resizeMode = "stretch";
    Bitmap image;
    Path path;
    float minDimension;
    float gridSize;
    ArrayList<ArrayList<Boolean>> grid;
    boolean cleared;
    int clearPointsCounter;
    float scratchProgress;
    int placeholderColor = -1;

    boolean criticalCleared;
    float totalCriticalPoints;
    int clearedCriticalPoints;
    float criticalProgress;
    float criticalRadius = 0;
    float criticalRadiusSq = 0;
    float criticalCenterX = 0;
    float criticalCenterY = 0;

    Paint criticalPaint = new Paint();
    Paint imagePaint = new Paint();
    Paint pathPaint = new Paint();

    boolean inited = false;

    Rect imageRect = null;

    public ScratchView(Context context) {
        super(context);
        init();
    }

    public ScratchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScratchView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnTouchListener(this);

        imagePaint.setAntiAlias(true);
        imagePaint.setFilterBitmap(true);

        pathPaint.setAlpha(0);
        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        pathPaint.setAntiAlias(true);

        criticalPaint.setStyle(Paint.Style.FILL);
        criticalPaint.setColor(Color.GRAY);

        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setPlaceholderColor(@Nullable String placeholderColor) {
        if (placeholderColor != null) {
            try {
                this.placeholderColor = Color.parseColor(placeholderColor);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void setCriticalRadius(float criticalRadius) {
        this.criticalRadius = criticalRadius;
        this.criticalRadiusSq = criticalRadius * criticalRadius;
    }

    public void setCriticalCenterX(float criticalCenterX) {
        this.criticalCenterX = criticalCenterX;
    }

    public void setCriticalCenterY(float criticalCenterY) {
        this.criticalCenterY = criticalCenterY;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    public void setBrushSize(float brushSize) {
        this.brushSize = brushSize;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public void setResizeMode(String resizeMode) {
        if (resizeMode != null) {
            this.resizeMode = resizeMode.toLowerCase();
        }
    }

    // From: https://stackoverflow.com/a/41470146
    //
    private static Bitmap getBitmap(VectorDrawable vectorDrawable) {
        Bitmap bitmap = Bitmap.createBitmap(
            vectorDrawable.getIntrinsicWidth(),
            vectorDrawable.getIntrinsicHeight(),
            Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vectorDrawable.draw(canvas);
        return bitmap;
    }

    private void loadImage() {
        path = null;
        if (imageUrl != null) {
            Thread thread = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            InputStream is = (InputStream) new URL(imageUrl).getContent();
                            image = BitmapFactory.decodeStream(is).copy(Bitmap.Config.ARGB_8888, true);
                            reportImageLoadFinished(true);
                            invalidate();
                        } catch (Exception e) {
                            reportImageLoadFinished(false);
                            e.printStackTrace();
                        }
                    }
                }
            );
            thread.start();
        } else if (resourceName != null) {
            Log.d("ReactItch", "Loading resource with supplied name '" + resourceName + "' w/ context " + getContext().getPackageName());
            int imageResourceId = getResources().getIdentifier(resourceName, "drawable", getContext().getPackageName());
            Log.d("ReactItch", "Received imageResourceId: " + imageResourceId);
            Drawable drawable = ContextCompat.getDrawable(getContext(), imageResourceId);
            if (drawable instanceof BitmapDrawable) {
                image = BitmapFactory.decodeResource(getContext().getResources(), imageResourceId);
            } else if (drawable instanceof VectorDrawable) {
                image = getBitmap((VectorDrawable) drawable);
            }
            Log.d("ReactItch", "Done loading image");
            reportImageLoadFinished(true);
            invalidate();
        }
    }

    public void reset() {
        minDimension = getWidth() > getHeight() ? getHeight() : getWidth();
        brushSize = brushSize > 0 ? brushSize : (minDimension / 10.0f);
        brushSize = Math.max(1, Math.min(minDimension / 4.0f, brushSize));
        threshold = threshold > 0 ? threshold : 50;

        loadImage();
        initGrid();
        reportScratchProgress();
        reportScratchState();
        reportCriticalProgress();
        reportCriticalScratchState();
    }

    public void initGrid() {
        gridSize = (float) Math.max(Math.min(Math.ceil(minDimension / brushSize), 29), 9);
        totalCriticalPoints = (float) (Math.PI * criticalRadiusSq) / brushSize;

        grid = new ArrayList();
        for (int x = 0; x < gridSize; x++) {
            grid.add(new ArrayList<Boolean>());
            for (int y = 0; y < gridSize; y++) {
                grid.get(x).add(true);
            }
        }
        clearPointsCounter = 0;
        cleared = false;
        scratchProgress = 0;

        criticalCleared = false;
        criticalProgress = 0;
        clearedCriticalPoints = 0;
    }

    public void updateGrid(int x, int y) {
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        int pointInGridX = Math.round((Math.max(Math.min(x, viewWidth), 0) / viewWidth) * (gridSize - 1.0f));
        int pointInGridY = Math.round((Math.max(Math.min(y, viewHeight), 0) / viewHeight) * (gridSize - 1.0f));
        if (grid.get(pointInGridX).get(pointInGridY) == true) {
            grid.get(pointInGridX).set(pointInGridY, false);
            clearPointsCounter++;
            scratchProgress = ((float) clearPointsCounter) / (gridSize * gridSize) * 100.0f;
            reportScratchProgress();

            if (criticalRadiusSq > 0) {
                // This is the distance to the center of the circle at
                // centerX, centerY
                //
                float offsetX = criticalCenterX - x;
                float offsetY = criticalCenterY - y;
                float distSquared = offsetX * offsetX + offsetY * offsetY;

                Log.d(
                    "ReactItch",
                    "Received point @ (" +
                    x +
                    ", " +
                    y +
                    ") Offset X = " +
                    offsetX +
                    ", offsetY = " +
                    offsetY +
                    ", distSquared = " +
                    distSquared +
                    ", criticalRadiusSq = " +
                    criticalRadiusSq
                );

                // If that distance is less than the radius, then we're
                // inside the circle and we count the point as being
                // critical
                //
                if (distSquared <= criticalRadiusSq) {
                    clearedCriticalPoints++;
                    criticalProgress = ((float) clearedCriticalPoints * brushSize * brushSize) / totalCriticalPoints * 100.0f;
                    Log.d(
                        "ReactItch",
                        "Critical progress: " +
                        clearedCriticalPoints +
                        " @ brush size " +
                        brushSize +
                        " => " +
                        (clearedCriticalPoints * brushSize * brushSize) +
                        " / " +
                        totalCriticalPoints +
                        " = " +
                        criticalProgress
                    );
                    reportCriticalProgress();
                }

                reportCriticalScratchState();
            }

            if (!cleared && scratchProgress > threshold) {
                cleared = true;
                reportScratchState();
            }
        }
    }

    public void reportImageLoadFinished(boolean success) {
        final Context context = getContext();
        if (context instanceof ReactContext) {
            WritableMap event = Arguments.createMap();
            event.putBoolean("success", success);
            ((ReactContext) context).getJSModule(RCTEventEmitter.class)
                .receiveEvent(getId(), RNTScratchViewManager.EVENT_IMAGE_LOAD, event);
        }
    }

    public void reportTouchState(boolean state) {
        final Context context = getContext();
        if (context instanceof ReactContext) {
            WritableMap event = Arguments.createMap();
            event.putBoolean("touchState", state);
            ((ReactContext) context).getJSModule(RCTEventEmitter.class)
                .receiveEvent(getId(), RNTScratchViewManager.EVENT_TOUCH_STATE_CHANGED, event);
        }
    }

    public void reportScratchProgress() {
        final Context context = getContext();
        if (context instanceof ReactContext) {
            WritableMap event = Arguments.createMap();
            event.putDouble("progressValue", Math.round(scratchProgress * 100.0f) / 100.0);
            ((ReactContext) context).getJSModule(RCTEventEmitter.class)
                .receiveEvent(getId(), RNTScratchViewManager.EVENT_SCRATCH_PROGRESS_CHANGED, event);
        }
    }

    public void reportScratchState() {
        final Context context = getContext();
        if (context instanceof ReactContext) {
            WritableMap event = Arguments.createMap();
            event.putBoolean("isScratchDone", cleared);
            ((ReactContext) context).getJSModule(RCTEventEmitter.class)
                .receiveEvent(getId(), RNTScratchViewManager.EVENT_SCRATCH_DONE, event);
        }
    }

    public void reportCriticalProgress() {
        final Context context = getContext();
        if (context instanceof ReactContext) {
            WritableMap event = Arguments.createMap();
            event.putDouble("progressValue", Math.round(criticalProgress * 100.0f) / 100.0);
            ((ReactContext) context).getJSModule(RCTEventEmitter.class)
                .receiveEvent(getId(), RNTScratchViewManager.EVENT_CRITICAL_PROGRESS_CHANGED, event);
        }
    }

    public void reportCriticalScratchState() {
        final Context context = getContext();
        if (context instanceof ReactContext) {
            WritableMap event = Arguments.createMap();
            event.putBoolean("isScratchDone", cleared);
            ((ReactContext) context).getJSModule(RCTEventEmitter.class)
                .receiveEvent(getId(), RNTScratchViewManager.EVENT_CRITICAL_SCRATCH_DONE, event);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!inited && getWidth() > 0) {
            inited = true;
            reset();
        }

        if (!imageTakenFromView && this.placeholderColor != -1) {
            canvas.drawColor(this.placeholderColor);
        }

        if (image == null) {
            return;
        }

        if (imageRect == null) {
            int offsetX = 0;
            int offsetY = 0;
            float viewWidth = (float) getWidth();
            float viewHeight = (float) getHeight();
            float imageAspect = (float) image.getWidth() / (float) image.getHeight();
            float viewAspect = viewWidth / viewHeight;
            switch (resizeMode) {
                case "cover":
                    if (imageAspect > viewAspect) {
                        offsetX = (int) (((viewHeight * imageAspect) - viewWidth) / 2.0f);
                    } else {
                        offsetY = (int) (((viewWidth / imageAspect) - viewHeight) / 2.0f);
                    }
                    break;
                case "contain":
                    if (imageAspect < viewAspect) {
                        offsetX = (int) (((viewHeight * imageAspect) - viewWidth) / 2.0f);
                    } else {
                        offsetY = (int) (((viewWidth / imageAspect) - viewHeight) / 2.0f);
                    }
                    break;
            }
            imageRect = new Rect(-offsetX, -offsetY, getWidth() + offsetX, getHeight() + offsetY);
        }

        canvas.drawBitmap(image, new Rect(0, 0, image.getWidth(), image.getHeight()), imageRect, imagePaint);

        if (!imageTakenFromView && criticalRadius > 0) {
            canvas.drawCircle(criticalCenterX, criticalCenterY, criticalRadius, criticalPaint);
        }

        if (path != null) {
            canvas.drawPath(path, pathPaint);
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        int x = (int) motionEvent.getX();
        int y = (int) motionEvent.getY();

        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                image = createBitmapFromView();
                reportTouchState(true);
                float strokeWidth = brushSize > 0 ? brushSize : ((getHeight() < getWidth() ? getHeight() : getWidth()) / 10f);
                imageRect = new Rect(0, 0, getWidth(), getHeight());
                pathPaint.setStrokeWidth(strokeWidth);
                path = new Path();
                path.moveTo(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                if (path != null) {
                    path.lineTo(x, y);
                    updateGrid(x, y);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                reportTouchState(false);
                image = createBitmapFromView();
                path = null;
                break;
        }
        invalidate();
        return true;
    }

    public Bitmap createBitmapFromView() {
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        draw(c);
        imageTakenFromView = true;
        return bitmap;
    }
}

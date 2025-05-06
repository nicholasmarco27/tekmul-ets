package com.example.aremotionfilters; // Replace with your actual package name

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FaceOverlayView extends View {

    private final Paint boxPaint;
    private final Paint textPaint; // For debugging or showing emotion text
    private List<FaceData> facesData = new ArrayList<>();
    private int imageWidth;
    private int imageHeight;
    private boolean isFrontCamera = true; // Assume front camera by default

    private Bitmap sparklesBitmap;
    private Bitmap zzzBitmap;
    private final Matrix matrix = new Matrix(); // For rotating bitmaps

    private static final String TAG = "FaceOverlayView";

    public FaceOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5.0f);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40.0f);

        // Load effect bitmaps
        try {
            sparklesBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sparkles);
            zzzBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.zzz);
        } catch (Exception e) {
            Log.e(TAG, "Error loading bitmaps: " + e.getMessage());
            // You might want to use placeholder colors or shapes if bitmaps fail to load
        }
    }

    public void updateFaces(List<FaceData> faces, int imageWidth, int imageHeight, boolean isFrontCamera) {
        this.facesData = faces;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.isFrontCamera = isFrontCamera;
        postInvalidate(); // Request a redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (facesData == null || facesData.isEmpty() || imageWidth == 0 || imageHeight == 0) {
            return;
        }

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        // These scale factors map the ML Kit image coordinates to the view coordinates.
        // This assumes the PreviewView and ImageAnalysis are using similar aspect ratios
        // or that the PreviewView is scaling to fill.
        // For a more robust solution, you'd use PreviewView's transformation info.
        float scaleX = viewWidth / imageWidth;
        float scaleY = viewHeight / imageHeight;


        for (FaceData faceData : facesData) {
            Rect originalBox = faceData.getBoundingBox();
            RectF scaledBox = new RectF();

            // Mirror X coordinates for front camera
            if (isFrontCamera) {
                scaledBox.left = viewWidth - (originalBox.right * scaleX);
                scaledBox.right = viewWidth - (originalBox.left * scaleX);
            } else {
                scaledBox.left = originalBox.left * scaleX;
                scaledBox.right = originalBox.right * scaleX;
            }
            scaledBox.top = originalBox.top * scaleY;
            scaledBox.bottom = originalBox.bottom * scaleY;

            // Optionally draw bounding box for debugging
            // canvas.drawRect(scaledBox, boxPaint);

            // Draw emotion text for debugging
            // canvas.drawText(faceData.getEmotion(), scaledBox.left, scaledBox.top - 10, textPaint);

            Bitmap effectBitmap = null;
            if ("SMILING".equals(faceData.getEmotion()) && sparklesBitmap != null) {
                effectBitmap = sparklesBitmap;
            } else if ("EYES_CLOSED".equals(faceData.getEmotion()) && zzzBitmap != null) {
                effectBitmap = zzzBitmap;
            }

            if (effectBitmap != null) {
                // Calculate position for the effect (e.g., above the head)
                float effectX = scaledBox.centerX() - (effectBitmap.getWidth() / 2f);
                float effectY = scaledBox.top - effectBitmap.getHeight() - 10; // 10px padding

                // Ensure effect is within bounds (simple check)
                if (effectY < 0) effectY = scaledBox.bottom + 10;
                if (effectX < 0) effectX = 0;
                if (effectX + effectBitmap.getWidth() > viewWidth) effectX = viewWidth - effectBitmap.getWidth();


                // Handle rotation of the bitmap based on head Euler Z angle
                // This is a simplified rotation around the center of the bitmap
                matrix.reset();
                matrix.postRotate(faceData.getHeadEulerAngleZ(), effectBitmap.getWidth() / 2f, effectBitmap.getHeight() / 2f);
                matrix.postTranslate(effectX, effectY);
                canvas.drawBitmap(effectBitmap, matrix, null);
            }
        }
    }
}

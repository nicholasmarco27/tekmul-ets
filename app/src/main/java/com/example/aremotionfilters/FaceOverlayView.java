package com.example.aremotionfilters; // Ganti dengan nama paket Anda yang sebenarnya

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
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
    // private final Paint textPaint;

    private List<FaceData> allDetectionData = new ArrayList<>();
    private int imageWidth; // Width of the image from ImageAnalysis
    private int imageHeight; // Height of the image from ImageAnalysis
    private boolean isFrontCameraForOverlay = true; // Status for drawing logic

    private Bitmap sparklesBitmap;
    private Bitmap zzzBitmap;
    private Bitmap leftWinkBitmap;
    private Bitmap rightWinkBitmap;
    private Bitmap thumbsUpBitmap;

    private final Matrix matrix = new Matrix();

    private static final float FACE_EFFECT_WIDTH_PERCENTAGE_OF_FACE = 0.9f;
    private static final float FACE_EFFECT_HORIZONTAL_OFFSET_FACTOR = 0.1f;
    private static final float FACE_EFFECT_VERTICAL_OFFSET_FACTOR = 0.6f;
    private static final float GESTURE_EFFECT_HEIGHT_PERCENTAGE_OF_VIEW = 0.15f; // This might need to be relative to image if used in drawFiltersOnCanvas
    private static final float GESTURE_EFFECT_VERTICAL_OFFSET_PIXELS = -20;

    private static final String TAG = "FaceOverlayView";

    public FaceOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint();
        boxPaint.setColor(Color.MAGENTA);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(3.0f);

        // textPaint = new Paint();
        // textPaint.setColor(Color.CYAN);
        // textPaint.setTextSize(30.0f);

        try {
            sparklesBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sparkles);
            zzzBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.zzz);
            leftWinkBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.left_wink_effect);
            rightWinkBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.right_wink_effect);
            thumbsUpBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.thumbs_up_effect);
        } catch (Exception e) {
            Log.e(TAG, "Error loading bitmaps: " + e.getMessage(), e);
        }
    }

    public void updateFaces(List<FaceData> detectionData, int imageWidth, int imageHeight, boolean isFrontCamera) {
        this.allDetectionData = new ArrayList<>(detectionData); // Use a copy
        this.imageWidth = imageWidth; // This is from ImageAnalysis
        this.imageHeight = imageHeight; // This is from ImageAnalysis
        this.isFrontCameraForOverlay = isFrontCamera;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Call the common drawing logic, using View's dimensions as target
        drawOverlayLogic(canvas, getWidth(), getHeight(), this.isFrontCameraForOverlay, true);
    }

    /**
     * New method to draw filters directly onto a provided canvas (e.g., for a captured bitmap).
     * @param canvas The canvas to draw on.
     * @param targetCanvasWidth The width of the target canvas/bitmap.
     * @param targetCanvasHeight The height of the target canvas/bitmap.
     * @param isFrontCamera True if the context is for a front camera image.
     */
    public void drawFiltersOnCanvas(Canvas canvas, int targetCanvasWidth, int targetCanvasHeight, boolean isFrontCamera) {
        Log.d(TAG, "drawFiltersOnCanvas called. Target W: " + targetCanvasWidth + " H: " + targetCanvasHeight +
                ". Analysis W: " + this.imageWidth + " H: " + this.imageHeight + ". Data size: " + allDetectionData.size());
        if (this.imageWidth == 0 || this.imageHeight == 0) {
            Log.w(TAG, "ImageAnalysis dimensions not set in FaceOverlayView, cannot draw filters on canvas.");
            return;
        }
        // Call the common drawing logic, using the provided canvas and its dimensions
        drawOverlayLogic(canvas, targetCanvasWidth, targetCanvasHeight, isFrontCamera, false);
    }

    /**
     * Common logic for drawing overlays, used by both onDraw and drawFiltersOnCanvas.
     * @param canvas The canvas to draw on.
     * @param effectiveViewWidth The width of the target drawing area (View width or captured image width).
     * @param effectiveViewHeight The height of the target drawing area (View height or captured image height).
     * @param isFrontCam True if the context is for a front camera image.
     * @param isLivePreview True if this is for live preview (onDraw), false if for static bitmap (drawFiltersOnCanvas).
     */
    private void drawOverlayLogic(Canvas canvas, int effectiveViewWidth, int effectiveViewHeight, boolean isFrontCam, boolean isLivePreview) {
        boolean noFaceBitmaps = sparklesBitmap == null && zzzBitmap == null && leftWinkBitmap == null && rightWinkBitmap == null;
        boolean noGestureBitmaps = thumbsUpBitmap == null;

        if (allDetectionData == null || allDetectionData.isEmpty() || this.imageWidth == 0 || this.imageHeight == 0) {
            if (isLivePreview) { // Only clear canvas if it's the live preview view's onDraw
                // canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); // Clear previous drawings if needed
            }
            return;
        }

        // Scale factor from ImageAnalysis coordinates to the target canvas coordinates
        float scaleX = (float) effectiveViewWidth / this.imageWidth;
        float scaleY = (float) effectiveViewHeight / this.imageHeight;

        // Log.d(TAG, "Drawing overlay. EffectiveW: " + effectiveViewWidth + " EffectiveH: " + effectiveViewHeight +
        //         " AnalysisW: " + this.imageWidth + " AnalysisH: " + this.imageHeight +
        //         " ScaleX: " + scaleX + " ScaleY: " + scaleY);


        for (FaceData data : allDetectionData) {
            String dataTypeOrEmotion = data.getEmotion();

            if (FaceData.GESTURE_THUMBS_UP.equals(dataTypeOrEmotion) && thumbsUpBitmap != null) {
                PointF anchor = data.getGestureAnchorPoint();
                if (anchor != null) {
                    // Transform gesture anchor from ImageAnalysis coordinates to target canvas coordinates
                    float anchorXInView = isFrontCam ? effectiveViewWidth - (anchor.x * scaleX) : anchor.x * scaleX;
                    float anchorYInView = anchor.y * scaleY;

                    // Use a fixed percentage of the *target canvas height* for gesture effect size
                    float desiredEffectHeight = effectiveViewHeight * GESTURE_EFFECT_HEIGHT_PERCENTAGE_OF_VIEW;
                    float aspectRatio = (float) thumbsUpBitmap.getWidth() / (float) thumbsUpBitmap.getHeight();
                    float desiredEffectWidth = desiredEffectHeight * aspectRatio;

                    if (desiredEffectWidth <= 0 || desiredEffectHeight <= 0) continue;

                    float targetEffectX = anchorXInView - (desiredEffectWidth / 2f);
                    float targetEffectY = anchorYInView - desiredEffectHeight + GESTURE_EFFECT_VERTICAL_OFFSET_PIXELS;

                    RectF srcRectF = new RectF(0, 0, thumbsUpBitmap.getWidth(), thumbsUpBitmap.getHeight());
                    RectF dstRectF = new RectF(targetEffectX, targetEffectY, targetEffectX + desiredEffectWidth, targetEffectY + desiredEffectHeight);

                    matrix.reset();
                    matrix.setRectToRect(srcRectF, dstRectF, Matrix.ScaleToFit.CENTER);
                    matrix.postRotate(data.getGestureRotation(), dstRectF.centerX(), dstRectF.centerY());
                    canvas.drawBitmap(thumbsUpBitmap, matrix, null);
                }
            } else if (data.getBoundingBox() != null) { // Face data
                Rect originalBox = data.getBoundingBox(); // In ImageAnalysis coordinates
                RectF scaledBox = new RectF(); // Will be in target canvas coordinates

                if (isFrontCam) {
                    scaledBox.left = effectiveViewWidth - (originalBox.right * scaleX);
                    scaledBox.right = effectiveViewWidth - (originalBox.left * scaleX);
                } else {
                    scaledBox.left = originalBox.left * scaleX;
                    scaledBox.right = originalBox.right * scaleX;
                }
                scaledBox.top = originalBox.top * scaleY;
                scaledBox.bottom = originalBox.bottom * scaleY;

                Bitmap effectBitmap = null;
                if (FaceData.EMOTION_SMILING.equals(dataTypeOrEmotion) && sparklesBitmap != null) {
                    effectBitmap = sparklesBitmap;
                } else if (FaceData.EMOTION_EYES_CLOSED.equals(dataTypeOrEmotion) && zzzBitmap != null) {
                    effectBitmap = zzzBitmap;
                } else if (FaceData.EMOTION_LEFT_WINK.equals(dataTypeOrEmotion) && leftWinkBitmap != null) {
                    effectBitmap = leftWinkBitmap;
                } else if (FaceData.EMOTION_RIGHT_WINK.equals(dataTypeOrEmotion) && rightWinkBitmap != null) {
                    effectBitmap = rightWinkBitmap;
                }

                if (effectBitmap != null) {
                    float faceActualWidthInView = scaledBox.width();
                    if (faceActualWidthInView <= 0) continue;

                    float desiredEffectWidth = faceActualWidthInView * FACE_EFFECT_WIDTH_PERCENTAGE_OF_FACE;
                    float effectAspectRatio = (float) effectBitmap.getHeight() / (float) effectBitmap.getWidth(); // Corrected aspect ratio
                    float desiredEffectHeight = desiredEffectWidth * effectAspectRatio;

                    if (desiredEffectWidth <= 0 || desiredEffectHeight <= 0) continue;

                    // Calculate center for effect placement relative to the scaled face box
                    // Note: Original logic for offset might need review if it was based on View properties not general face box
                    float effectCenterX = scaledBox.right + (faceActualWidthInView * FACE_EFFECT_HORIZONTAL_OFFSET_FACTOR); // Example: to the right of the face
                    float effectCenterY = scaledBox.top - (desiredEffectHeight * FACE_EFFECT_VERTICAL_OFFSET_FACTOR);     // Example: above the face

                    float targetEffectX = effectCenterX - (desiredEffectWidth / 2f);
                    float targetEffectY = effectCenterY - (desiredEffectHeight / 2f);

                    RectF srcRectF = new RectF(0, 0, effectBitmap.getWidth(), effectBitmap.getHeight());
                    RectF dstRectF = new RectF(targetEffectX, targetEffectY, targetEffectX + desiredEffectWidth, targetEffectY + desiredEffectHeight);

                    matrix.reset();
                    matrix.setRectToRect(srcRectF, dstRectF, Matrix.ScaleToFit.CENTER);
                    // Rotate effect around its own center on the target canvas
                    matrix.postRotate(data.getHeadEulerAngleZ(), dstRectF.centerX(), dstRectF.centerY());
                    canvas.drawBitmap(effectBitmap, matrix, null);
                }
            }
        }
    }
}

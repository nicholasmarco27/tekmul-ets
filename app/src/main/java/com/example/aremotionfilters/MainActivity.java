package com.example.aremotionfilters; // Replace with your actual package name

import android.Manifest;
import android.annotation.SuppressLint;
// import android.content.ContentValues; // Not used in this version for saving
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
// import android.media.Image; // Not directly used, ImageProxy is
// import android.os.Build; // Not directly used
import android.os.Bundle;
// import android.provider.MediaStore; // Not used in this version for saving
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.example.aremotionfilters.databinding.ActivityMainBinding;

// import java.io.OutputStream; // Not used in this version for saving
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"; // Can be used if saving files
    private static final int MAX_PHOTOS = 4;


    private PreviewView previewView;
    private FaceOverlayView faceOverlayView;
    private ActivityMainBinding binding;

    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private boolean isFrontCamera = true;

    private Button captureButton;
    private ImageView photoStripImageView;
    // private LinearLayout thumbnailContainer; // Not directly used after initialization
    private ImageView[] thumbnailImageViews = new ImageView[MAX_PHOTOS];

    private ImageCapture imageCapture;
    private List<Bitmap> capturedImages = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        previewView = binding.previewView;
        faceOverlayView = binding.faceOverlayView;

        captureButton = binding.captureButton;
        photoStripImageView = binding.photoStripImageView;
        // thumbnailContainer = binding.thumbnailContainer; // Assignment not strictly needed here
        thumbnailImageViews[0] = binding.thumbnail1;
        thumbnailImageViews[1] = binding.thumbnail2;
        thumbnailImageViews[2] = binding.thumbnail3;
        thumbnailImageViews[3] = binding.thumbnail4;

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        captureButton.setOnClickListener(v -> takePhoto());
        updateCaptureButtonText();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Error starting camera: " + e.getMessage(), e);
                Toast.makeText(this, getString(R.string.error_starting_camera_toast), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("RestrictedApi")
    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            Log.e(TAG, "Camera provider is null");
            return;
        }

        cameraProvider.unbindAll();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(isFrontCamera ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480)) // Keep this consistent if FaceOverlayView relies on its specific dimensions
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        // Pass the PreviewView's dimensions to FaceOverlayView if it needs to know its own display size for onDraw
        // However, FaceOverlayView uses getWidth()/getHeight() which is fine.
        // The crucial part is that FaceOverlayView knows the imageAnalysis dimensions (imageWidth, imageHeight)
        imageAnalysis.setAnalyzer(cameraExecutor, new FaceEmotionAnalyzer(faceOverlayView, isFrontCamera));


        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
                // Consider setting target resolution for ImageCapture if consistency with ImageAnalysis is critical
                // .setTargetResolution(new Size(640, 480)) // Or higher for better quality captures
                .build();

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);
            Log.d(TAG, "Camera use cases bound successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
            Toast.makeText(this, getString(R.string.could_not_bind_camera_use_cases_toast), Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture use case is not initialized.");
            Toast.makeText(this, getString(R.string.camera_not_ready_toast), Toast.LENGTH_SHORT).show();
            return;
        }

        if (capturedImages.size() >= MAX_PHOTOS) {
            resetPhotoStrip();
            return;
        }

        imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        Log.d(TAG, "Photo capture succeeded. Image dimensions: " + imageProxy.getWidth() + "x" + imageProxy.getHeight() + ", Rotation: " + imageProxy.getImageInfo().getRotationDegrees());
                        Bitmap cameraBitmap = imageProxyToBitmap(imageProxy); // This handles rotation

                        if (cameraBitmap != null) {
                            Bitmap finalBitmapWithFilter;

                            // Create a mutable bitmap to draw on
                            Bitmap mutableCameraBitmap = cameraBitmap.copy(Bitmap.Config.ARGB_8888, true);
                            Canvas canvas = new Canvas(mutableCameraBitmap);

                            // Flip the canvas for front camera before drawing the overlay if the overlay itself isn't flipped
                            // However, it's better to flip the cameraBitmap itself if needed, then draw overlay.
                            // The imageProxyToBitmap already handles rotation.
                            // Flipping for front camera should apply to the base image.

                            Bitmap baseBitmapForOverlay = mutableCameraBitmap;
                            if (isFrontCamera) {
                                Matrix matrix = new Matrix();
                                matrix.preScale(-1.0f, 1.0f); // Horizontal flip
                                // CORRECTED TYPO HERE: mutableCameraBaps -> mutableCameraBitmap
                                baseBitmapForOverlay = Bitmap.createBitmap(mutableCameraBitmap, 0, 0, mutableCameraBitmap.getWidth(), mutableCameraBitmap.getHeight(), matrix, true);
                                // Redraw the flipped base onto the canvas if we are reusing mutableCameraBitmap
                                // The canvas is associated with mutableCameraBitmap. If baseBitmapForOverlay is a new bitmap (which it is after createBitmap),
                                // we should draw onto the canvas associated with the bitmap we intend to use or create a new canvas for the new bitmap.
                                // For simplicity, let's ensure the canvas for drawing the overlay is on the correctly oriented bitmap.
                                canvas.setBitmap(baseBitmapForOverlay); // Set canvas to draw on the (potentially new) flipped bitmap
                            }


                            // Draw the overlay using the captured image's dimensions as the target.
                            // FaceOverlayView will use its internally stored FaceData.
                            // The coordinates in FaceData are relative to the ImageAnalysis stream dimensions.
                            // FaceOverlayView's new method needs to scale from ImageAnalysis dimensions to CapturedImage dimensions.
                            Log.d(TAG, "Drawing filters on canvas. Target (Canvas) W: " + canvas.getWidth() + " H: " + canvas.getHeight());
                            faceOverlayView.drawFiltersOnCanvas(canvas, canvas.getWidth(), canvas.getHeight(), isFrontCamera);

                            finalBitmapWithFilter = baseBitmapForOverlay;

                            capturedImages.add(finalBitmapWithFilter);
                            updateThumbnails();
                            updateCaptureButtonText();

                            if (capturedImages.size() == MAX_PHOTOS) {
                                generateAndDisplayPhotoStrip();
                            }
                        } else {
                            Log.e(TAG, "Failed to convert ImageProxy to Bitmap.");
                            Toast.makeText(MainActivity.this, getString(R.string.failed_to_process_image_toast), Toast.LENGTH_SHORT).show();
                        }
                        imageProxy.close();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                        Toast.makeText(MainActivity.this, String.format(getString(R.string.photo_capture_failed_toast), exception.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }


    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy planeProxy = image.getPlanes()[0];
        ByteBuffer buffer = planeProxy.getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        // Apply rotation to make the image upright
        Matrix matrix = new Matrix();
        matrix.postRotate(image.getImageInfo().getRotationDegrees());
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }


    private void updateThumbnails() {
        for (int i = 0; i < MAX_PHOTOS; i++) {
            if (i < capturedImages.size()) {
                thumbnailImageViews[i].setImageBitmap(capturedImages.get(i));
                thumbnailImageViews[i].setVisibility(View.VISIBLE);
            } else {
                thumbnailImageViews[i].setImageBitmap(null);
                thumbnailImageViews[i].setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            }
        }
    }

    private void updateCaptureButtonText() {
        int count = capturedImages.size();
        if (count < MAX_PHOTOS) {
            captureButton.setText(getString(R.string.take_picture_count, count, MAX_PHOTOS));
        } else {
            captureButton.setText(getString(R.string.clear_photostrip));
        }
    }

    private void generateAndDisplayPhotoStrip() {
        if (capturedImages.size() != MAX_PHOTOS) {
            return;
        }

        int stripWidth = 0;
        int totalHeight = 0;
        // Let's aim for a consistent width for the photostrip images, e.g., 150dp or calculate based on screen.
        // For simplicity, let's use a fixed width for each image in the strip.
        int singleImageDisplayWidth = 300; // pixels, adjust as needed
        List<Bitmap> scaledImages = new ArrayList<>();

        for (Bitmap originalBitmap : capturedImages) {
            if (originalBitmap == null) continue;

            int originalWidth = originalBitmap.getWidth();
            int originalHeight = originalBitmap.getHeight();
            float aspectRatio = (float) originalHeight / originalWidth; // height / width

            int scaledWidth = singleImageDisplayWidth;
            int scaledHeight = (int) (scaledWidth * aspectRatio);

            Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true);
            scaledImages.add(scaledBitmap);

            if (scaledBitmap.getWidth() > stripWidth) { // Should be same if using fixed width
                stripWidth = scaledBitmap.getWidth();
            }
            totalHeight += scaledBitmap.getHeight();
        }

        if (scaledImages.isEmpty()) {
            Log.e(TAG, "No images to create photostrip");
            return;
        }

        Bitmap photoStripBitmap = Bitmap.createBitmap(stripWidth, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(photoStripBitmap);
        canvas.drawColor(Color.WHITE);

        int currentY = 0;
        for (Bitmap img : scaledImages) {
            if (img == null) continue;
            float leftOffset = (stripWidth - img.getWidth()) / 2f; // Should be 0 if all same width
            canvas.drawBitmap(img, leftOffset, currentY, null);
            currentY += img.getHeight();
        }

        photoStripImageView.setImageBitmap(photoStripBitmap);
        photoStripImageView.setVisibility(View.VISIBLE);
        Toast.makeText(this, getString(R.string.photostrip_created_toast), Toast.LENGTH_SHORT).show();
    }

    private void resetPhotoStrip() {
        for (Bitmap bmp : capturedImages) {
            if (bmp != null && !bmp.isRecycled()) {
                bmp.recycle();
            }
        }
        capturedImages.clear();

        for(ImageView iv : thumbnailImageViews) {
            iv.setImageBitmap(null);
            iv.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        }
        photoStripImageView.setImageBitmap(null);
        photoStripImageView.setVisibility(View.GONE);
        updateCaptureButtonText();
        Log.d(TAG, "Photostrip reset.");
    }


    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, getString(R.string.permissions_not_granted_toast), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        for (Bitmap bmp : capturedImages) {
            if (bmp != null && !bmp.isRecycled()) {
                bmp.recycle();
            }
        }
        capturedImages.clear();
    }
}

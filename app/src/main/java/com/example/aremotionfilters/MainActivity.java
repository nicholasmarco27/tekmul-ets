package com.example.aremotionfilters; // Replace with your actual package name

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
// import android.widget.LinearLayout; // Not directly referenced after binding
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 11;

    private static String[] REQUIRED_PERMISSIONS;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
        } else {
            REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }
    }

    private static final String FILENAME_FORMAT = "yyyyMMdd_HHmmss_SSS";
    private static final int MAX_PHOTOS = 4;

    private PreviewView previewView;
    private FaceOverlayView faceOverlayView;
    private ActivityMainBinding binding;

    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private boolean isFrontCamera = true;

    private Button captureButton;
    private ImageView photoStripImageView;
    private Button downloadButton;
    private ImageView[] thumbnailImageViews = new ImageView[MAX_PHOTOS];

    private ImageCapture imageCapture;
    private List<Bitmap> capturedImages = new ArrayList<>();
    private Bitmap currentPhotoStripBitmap;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        previewView = binding.previewView;
        faceOverlayView = binding.faceOverlayView;
        captureButton = binding.captureButton;
        photoStripImageView = binding.photoStripImageView;
        downloadButton = binding.downloadButton;
        thumbnailImageViews[0] = binding.thumbnail1;
        thumbnailImageViews[1] = binding.thumbnail2;
        thumbnailImageViews[2] = binding.thumbnail3;
        thumbnailImageViews[3] = binding.thumbnail4;

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (allRequiredPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        captureButton.setOnClickListener(v -> takePhoto());
        downloadButton.setOnClickListener(v -> downloadPhotoStrip());
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
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, new FaceEmotionAnalyzer(faceOverlayView, isFrontCamera));
        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
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
                        Bitmap cameraBitmap = imageProxyToBitmap(imageProxy);
                        if (cameraBitmap != null) {
                            Bitmap finalBitmapWithFilter;
                            Bitmap mutableCameraBitmap = cameraBitmap.copy(Bitmap.Config.ARGB_8888, true);
                            Canvas canvas = new Canvas(mutableCameraBitmap);
                            Bitmap baseBitmapForOverlay = mutableCameraBitmap;
                            if (isFrontCamera) {
                                Matrix matrix = new Matrix();
                                matrix.preScale(-1.0f, 1.0f);
                                baseBitmapForOverlay = Bitmap.createBitmap(mutableCameraBitmap, 0, 0, mutableCameraBitmap.getWidth(), mutableCameraBitmap.getHeight(), matrix, true);
                                canvas.setBitmap(baseBitmapForOverlay);
                            }
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
        int singleImageDisplayWidth = 300;
        List<Bitmap> scaledImages = new ArrayList<>();
        for (Bitmap originalBitmap : capturedImages) {
            if (originalBitmap == null) continue;
            int originalWidth = originalBitmap.getWidth();
            int originalHeight = originalBitmap.getHeight();
            float aspectRatio = (float) originalHeight / originalWidth;
            int scaledWidth = singleImageDisplayWidth;
            int scaledHeight = (int) (scaledWidth * aspectRatio);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true);
            scaledImages.add(scaledBitmap);
            if (scaledBitmap.getWidth() > stripWidth) {
                stripWidth = scaledBitmap.getWidth();
            }
            totalHeight += scaledBitmap.getHeight();
        }
        if (scaledImages.isEmpty()) {
            Log.e(TAG, "No images to create photostrip");
            currentPhotoStripBitmap = null;
            downloadButton.setVisibility(View.GONE);
            return;
        }
        currentPhotoStripBitmap = Bitmap.createBitmap(stripWidth, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(currentPhotoStripBitmap);
        canvas.drawColor(Color.WHITE);
        int currentY = 0;
        for (Bitmap img : scaledImages) {
            if (img == null) continue;
            float leftOffset = (stripWidth - img.getWidth()) / 2f;
            canvas.drawBitmap(img, leftOffset, currentY, null);
            currentY += img.getHeight();
        }
        photoStripImageView.setImageBitmap(currentPhotoStripBitmap);
        photoStripImageView.setVisibility(View.VISIBLE);
        downloadButton.setVisibility(View.VISIBLE);
        Toast.makeText(this, getString(R.string.photostrip_created_toast), Toast.LENGTH_SHORT).show();
    }

    private void resetPhotoStrip() {
        for (Bitmap bmp : capturedImages) {
            if (bmp != null && !bmp.isRecycled()) {
                bmp.recycle();
            }
        }
        capturedImages.clear();
        if (currentPhotoStripBitmap != null && !currentPhotoStripBitmap.isRecycled()) {
            currentPhotoStripBitmap.recycle();
        }
        currentPhotoStripBitmap = null;
        for(ImageView iv : thumbnailImageViews) {
            iv.setImageBitmap(null);
            iv.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        }
        photoStripImageView.setImageBitmap(null);
        photoStripImageView.setVisibility(View.GONE);
        downloadButton.setVisibility(View.GONE);
        updateCaptureButtonText();
        Log.d(TAG, "Photostrip reset.");
    }

    private void downloadPhotoStrip() {
        if (currentPhotoStripBitmap == null) {
            Toast.makeText(this, getString(R.string.photostrip_saved_failed) + " (No image)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_STORAGE_PERMISSION);
        } else {
            savePhotoStripToGallery();
        }
    }

    private void savePhotoStripToGallery() {
        if (currentPhotoStripBitmap == null) {
            Toast.makeText(this, getString(R.string.photostrip_saved_failed) + " (Bitmap is null)", Toast.LENGTH_SHORT).show();
            return;
        }
        String imageFileName = "PhotoStrip_" + new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, imageFileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "AREmotionFilters");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }
        Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (imageUri != null) {
            try (OutputStream outputStream = getContentResolver().openOutputStream(imageUri)) {
                if (outputStream != null) {
                    currentPhotoStripBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                    Toast.makeText(this, getString(R.string.photostrip_saved_success), Toast.LENGTH_LONG).show();
                } else {
                    throw new IOException("Failed to get output stream.");
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(imageUri, values, null, null);
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to save photostrip: " + e.getMessage(), e);
                Toast.makeText(this, getString(R.string.photostrip_saved_failed), Toast.LENGTH_LONG).show();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        getContentResolver().delete(imageUri, null, null);
                    } catch (Exception ex) {
                        Log.e(TAG, "Failed to delete pending image entry: " + ex.getMessage());
                    }
                }
            }
        } else {
            Log.e(TAG, "Failed to create MediaStore entry.");
            Toast.makeText(this, getString(R.string.photostrip_saved_failed), Toast.LENGTH_LONG).show();
        }
    }

    private boolean allRequiredPermissionsGranted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (Arrays.asList(REQUIRED_PERMISSIONS).contains(Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allRequiredPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, getString(R.string.permissions_not_granted_toast), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                savePhotoStripToGallery();
            } else {
                Toast.makeText(this, getString(R.string.storage_permission_required), Toast.LENGTH_LONG).show();
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
        if (currentPhotoStripBitmap != null && !currentPhotoStripBitmap.isRecycled()) {
            currentPhotoStripBitmap.recycle();
        }
        currentPhotoStripBitmap = null;
    }
}

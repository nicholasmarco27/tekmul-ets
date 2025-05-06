package com.example.aremotionfilters; // Replace with your actual package name

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.ArrayList;
import java.util.List;

public class FaceEmotionAnalyzer implements ImageAnalysis.Analyzer {

    private static final String TAG = "FaceEmotionAnalyzer";
    private final FaceDetector detector;
    private final FaceOverlayView faceOverlayView;
    private final boolean isFrontCamera;

    // Thresholds for classification
    private static final float SMILING_THRESHOLD = 0.7f;
    private static final float EYE_CLOSED_THRESHOLD = 0.3f; // If open prob is less than this

    public FaceEmotionAnalyzer(FaceOverlayView overlayView, boolean isFrontCamera) {
        this.faceOverlayView = overlayView;
        this.isFrontCamera = isFrontCamera;

        // High-accuracy landmark detection and face classification
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE) // No landmarks needed for this simple version
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // Detect smiling and eye open
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                        .setMinFaceSize(0.15f) // Detect faces that are at least 15% of the image
                        .build();

        detector = FaceDetection.getClient(options);
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        @SuppressLint("UnsafeOptInUsageError")
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            detector.process(image)
                    .addOnSuccessListener(
                            new OnSuccessListener<List<Face>>() {
                                @Override
                                public void onSuccess(List<Face> faces) {
                                    List<FaceData> faceDataList = new ArrayList<>();
                                    for (Face face : faces) {
                                        Rect boundingBox = face.getBoundingBox();
                                        String emotion = "NEUTRAL";

                                        // Check for smiling
                                        if (face.getSmilingProbability() != null && face.getSmilingProbability() > SMILING_THRESHOLD) {
                                            emotion = "SMILING";
                                        }
                                        // Check for eyes closed (both)
                                        else if (face.getLeftEyeOpenProbability() != null && face.getLeftEyeOpenProbability() < EYE_CLOSED_THRESHOLD &&
                                                face.getRightEyeOpenProbability() != null && face.getRightEyeOpenProbability() < EYE_CLOSED_THRESHOLD) {
                                            emotion = "EYES_CLOSED";
                                        }
                                        // Add more conditions if needed (e.g., only one eye closed)

                                        float headEulerAngleZ = face.getHeadEulerAngleZ(); // Get Z rotation for effect orientation
                                        faceDataList.add(new FaceData(boundingBox, emotion, headEulerAngleZ));
                                    }
                                    faceOverlayView.updateFaces(faceDataList, image.getWidth(), image.getHeight(), isFrontCamera);
                                    imageProxy.close();
                                }
                            })
                    .addOnFailureListener(
                            new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Log.e(TAG, "Face detection failed: " + e.getMessage());
                                    imageProxy.close();
                                }
                            });
        } else {
            imageProxy.close(); // Make sure to close the proxy if mediaImage is null
        }
    }
}

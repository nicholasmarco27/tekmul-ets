package com.example.aremotionfilters; // Ganti dengan nama paket Anda yang sebenarnya

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

    // Ambang batas untuk klasifikasi
    private static final float SMILING_THRESHOLD = 0.7f; // Probabilitas di atas ini dianggap tersenyum
    private static final float EYE_OPEN_THRESHOLD = 0.6f; // Probabilitas mata terbuka di atas ini, dianggap terbuka
    private static final float EYE_CLOSED_THRESHOLD = 0.4f; // Probabilitas mata terbuka di bawah ini, dianggap tertutup

    public FaceEmotionAnalyzer(FaceOverlayView overlayView, boolean isFrontCamera) {
        this.faceOverlayView = overlayView;
        this.isFrontCamera = isFrontCamera;

        // Konfigurasi FaceDetector
        // Mode performa cepat, klasifikasi untuk senyum dan mata terbuka/tertutup diaktifkan.
        // Landmark dan kontur tidak diaktifkan untuk menjaga kesederhanaan dan kecepatan.
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                        .setMinFaceSize(0.15f) // Deteksi wajah yang ukurannya minimal 15% dari gambar
                        .build();

        detector = FaceDetection.getClient(options);
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        @SuppressLint("UnsafeOptInUsageError")
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            // Membuat InputImage dari mediaImage untuk diproses oleh ML Kit
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            detector.process(image)
                    .addOnSuccessListener(
                            new OnSuccessListener<List<Face>>() {
                                @Override
                                public void onSuccess(List<Face> faces) {
                                    List<FaceData> faceDataList = new ArrayList<>();
                                    for (Face face : faces) {
                                        Rect boundingBox = face.getBoundingBox(); // Kotak pembatas wajah
                                        String emotion = FaceData.EMOTION_NEUTRAL; // Emosi default

                                        // Dapatkan probabilitas dari ML Kit
                                        Float leftEyeOpenProb = face.getLeftEyeOpenProbability();
                                        Float rightEyeOpenProb = face.getRightEyeOpenProbability();
                                        Float smilingProb = face.getSmilingProbability();

                                        // Tentukan status berdasarkan probabilitas dan ambang batas
                                        boolean isSmiling = (smilingProb != null && smilingProb > SMILING_THRESHOLD);
                                        boolean isLeftEyeLikelyOpen = (leftEyeOpenProb != null && leftEyeOpenProb > EYE_OPEN_THRESHOLD);
                                        boolean isLeftEyeLikelyClosed = (leftEyeOpenProb != null && leftEyeOpenProb < EYE_CLOSED_THRESHOLD);
                                        boolean isRightEyeLikelyOpen = (rightEyeOpenProb != null && rightEyeOpenProb > EYE_OPEN_THRESHOLD);
                                        boolean isRightEyeLikelyClosed = (rightEyeOpenProb != null && rightEyeOpenProb < EYE_CLOSED_THRESHOLD);

                                        // Logika untuk menentukan emosi
                                        // Prioritas: Senyum > Kedua Mata Tertutup > Kedip Kiri > Kedip Kanan > Netral
                                        if (isSmiling) {
                                            emotion = FaceData.EMOTION_SMILING;
                                        } else if (isLeftEyeLikelyClosed && isRightEyeLikelyClosed) {
                                            // Jika tidak tersenyum, dan kedua mata kemungkinan tertutup
                                            emotion = FaceData.EMOTION_EYES_CLOSED;
                                        } else if (isLeftEyeLikelyClosed && isRightEyeLikelyOpen) {
                                            // Jika tidak tersenyum, mata kiri tertutup dan mata kanan terbuka
                                            emotion = FaceData.EMOTION_LEFT_WINK;
                                        } else if (isRightEyeLikelyClosed && isLeftEyeLikelyOpen) {
                                            // Jika tidak tersenyum, mata kanan tertutup dan mata kiri terbuka
                                            emotion = FaceData.EMOTION_RIGHT_WINK;
                                        }
                                        // Jika tidak ada kondisi di atas, emosi tetap NEUTRAL

                                        float headEulerAngleZ = face.getHeadEulerAngleZ(); // Dapatkan rotasi Z untuk orientasi efek
                                        faceDataList.add(new FaceData(boundingBox, emotion, headEulerAngleZ));
                                    }
                                    // Perbarui FaceOverlayView dengan data wajah yang baru dideteksi
                                    faceOverlayView.updateFaces(faceDataList, image.getWidth(), image.getHeight(), isFrontCamera);
                                    imageProxy.close(); // Penting untuk menutup ImageProxy setelah selesai
                                }
                            })
                    .addOnFailureListener(
                            new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Log.e(TAG, "Deteksi wajah gagal: " + e.getMessage());
                                    imageProxy.close(); // Tutup ImageProxy jika terjadi kegagalan
                                }
                            });
        } else {
            imageProxy.close(); // Pastikan untuk menutup proxy jika mediaImage null
        }
    }
}

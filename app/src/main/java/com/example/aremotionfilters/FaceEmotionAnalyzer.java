package com.example.aremotionfilters; // Ganti dengan nama paket Anda yang sebenarnya

import android.annotation.SuppressLint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
// Import untuk Pose Detection
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;


import java.util.ArrayList;
import java.util.List;
// Executor tidak lagi digunakan dalam contoh ini, bisa dihapus jika tidak ada penggunaan lain
// import java.util.concurrent.Executor;
// import java.util.concurrent.Executors;

public class FaceEmotionAnalyzer implements ImageAnalysis.Analyzer {

    private static final String TAG = "FaceEmotionAnalyzer";
    private final FaceDetector faceDetector;
    private final PoseDetector poseDetector;
    private final FaceOverlayView faceOverlayView;
    private final boolean isFrontCamera;

    // Ambang batas untuk klasifikasi wajah
    private static final float SMILING_THRESHOLD = 0.7f;
    private static final float EYE_OPEN_THRESHOLD = 0.6f;
    private static final float EYE_CLOSED_THRESHOLD = 0.4f;

    // Ambang batas untuk gestur thumbs up yang disederhanakan
    // Jarak Y antara jempol dan pergelangan tangan, dinormalisasi dengan tinggi bounding box pose (jika ada)
    // atau jarak absolut. Anda perlu menyesuaikan ini.
    private static final float THUMBS_UP_WRIST_Y_DIFFERENCE_THRESHOLD = 50; // Contoh nilai absolut dalam piksel gambar


    public FaceEmotionAnalyzer(FaceOverlayView overlayView, boolean isFrontCamera) {
        this.faceOverlayView = overlayView;
        this.isFrontCamera = isFrontCamera;

        // Konfigurasi Face Detector
        FaceDetectorOptions faceOptions =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                        .setMinFaceSize(0.15f)
                        .build();
        faceDetector = FaceDetection.getClient(faceOptions);

        // Konfigurasi Pose Detector
        AccuratePoseDetectorOptions poseOptions =
                new AccuratePoseDetectorOptions.Builder()
                        .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                        .build();
        poseDetector = PoseDetection.getClient(poseOptions);
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        @SuppressLint("UnsafeOptInUsageError")
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            Task<List<Face>> faceTask = faceDetector.process(image);
            Task<Pose> poseTask = poseDetector.process(image);

            Tasks.whenAllComplete(faceTask, poseTask)
                    .addOnCompleteListener(task -> {
                        List<FaceData> allDetectionData = new ArrayList<>();
                        try {
                            // Proses hasil deteksi wajah
                            List<Face> faces = faceTask.isSuccessful() ? faceTask.getResult() : null;
                            if (faces != null) {
                                for (Face face : faces) {
                                    Rect boundingBox = face.getBoundingBox();
                                    String emotion = FaceData.EMOTION_NEUTRAL;

                                    Float leftEyeOpenProb = face.getLeftEyeOpenProbability();
                                    Float rightEyeOpenProb = face.getRightEyeOpenProbability();
                                    Float smilingProb = face.getSmilingProbability();

                                    boolean isSmiling = (smilingProb != null && smilingProb > SMILING_THRESHOLD);
                                    boolean isLeftEyeLikelyOpen = (leftEyeOpenProb != null && leftEyeOpenProb > EYE_OPEN_THRESHOLD);
                                    boolean isLeftEyeLikelyClosed = (leftEyeOpenProb != null && leftEyeOpenProb < EYE_CLOSED_THRESHOLD);
                                    boolean isRightEyeLikelyOpen = (rightEyeOpenProb != null && rightEyeOpenProb > EYE_OPEN_THRESHOLD);
                                    boolean isRightEyeLikelyClosed = (rightEyeOpenProb != null && rightEyeOpenProb < EYE_CLOSED_THRESHOLD);

                                    if (isSmiling) {
                                        emotion = FaceData.EMOTION_SMILING;
                                    } else if (isLeftEyeLikelyClosed && isRightEyeLikelyClosed) {
                                        emotion = FaceData.EMOTION_EYES_CLOSED;
                                    } else if (isLeftEyeLikelyClosed && isRightEyeLikelyOpen) {
                                        emotion = FaceData.EMOTION_LEFT_WINK;
                                    } else if (isRightEyeLikelyClosed && isLeftEyeLikelyOpen) {
                                        emotion = FaceData.EMOTION_RIGHT_WINK;
                                    }
                                    float headEulerAngleZ = face.getHeadEulerAngleZ();
                                    allDetectionData.add(new FaceData(boundingBox, emotion, headEulerAngleZ));
                                }
                            }

                            // Proses hasil deteksi pose
                            Pose pose = poseTask.isSuccessful() ? poseTask.getResult() : null;
                            if (pose != null) {
                                PointF thumbsUpAnchor = checkForThumbsUpSimplified(pose, image.getHeight()); // Kirim tinggi gambar untuk normalisasi potensial
                                if (thumbsUpAnchor != null) {
                                    PoseLandmark wrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST); // Atau LEFT_WRIST
                                    PoseLandmark elbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);
                                    float gestureRotation = 0f;
                                    if (wrist != null && elbow != null) {
                                        gestureRotation = (float) Math.toDegrees(Math.atan2(
                                                wrist.getPosition().y - elbow.getPosition().y,
                                                wrist.getPosition().x - elbow.getPosition().x
                                        ));
                                        gestureRotation += 90; // Sesuaikan orientasi
                                    }
                                    allDetectionData.add(new FaceData(FaceData.GESTURE_THUMBS_UP, thumbsUpAnchor, gestureRotation));
                                }
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "Error processing detection results: " + e.getMessage());
                        } finally {
                            faceOverlayView.updateFaces(allDetectionData, image.getWidth(), image.getHeight(), isFrontCamera);
                            imageProxy.close();
                        }
                    });
        } else {
            imageProxy.close();
        }
    }

    // Metode untuk memeriksa gestur "thumbs up" yang disederhanakan
    private PointF checkForThumbsUpSimplified(Pose pose, int imageHeight) {
        // Coba deteksi untuk tangan kanan
        PoseLandmark thumbTip = pose.getPoseLandmark(PoseLandmark.RIGHT_THUMB);
        PoseLandmark wrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
        PoseLandmark elbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW); // Opsional, untuk konteks

        if (thumbTip == null || wrist == null) {
            return null; // Landmark penting tidak terdeteksi
        }

        PointF thumbPos = thumbTip.getPosition();
        PointF wristPos = wrist.getPosition();

        // Logika yang sangat sederhana:
        // 1. Ujung jempol (thumbPos.y) harus secara signifikan lebih kecil (di atas) dari pergelangan tangan (wristPos.y).
        //    Karena koordinat Y meningkat ke bawah.
        // 2. Mungkin, pergelangan tangan (wristPos.y) harus di atas siku (elbowPos.y) untuk gestur yang lebih jelas.

        float yDifference = wristPos.y - thumbPos.y; // Harus positif dan besar jika jempol di atas pergelangan tangan

        // Normalisasi sederhana: bandingkan perbedaan Y dengan persentase tinggi gambar
        // float normalizedThreshold = imageHeight * 0.05f; // Misalnya, 5% dari tinggi gambar
        // boolean thumbIsSignificantlyUp = yDifference > normalizedThreshold;

        // Atau gunakan ambang batas absolut (perlu disesuaikan berdasarkan resolusi gambar analisis)
        boolean thumbIsSignificantlyUp = yDifference > THUMBS_UP_WRIST_Y_DIFFERENCE_THRESHOLD;


        // Kondisi tambahan (opsional): Siku di bawah pergelangan tangan
        boolean armOrientedCorrectly = true; // Default true jika siku tidak terdeteksi
        if (elbow != null) {
            PointF elbowPos = elbow.getPosition();
            if (elbowPos.y < wristPos.y) { // Jika siku di atas pergelangan tangan, mungkin bukan thumbs up yang jelas
                armOrientedCorrectly = false;
            }
        }

        if (thumbIsSignificantlyUp && armOrientedCorrectly) {
            Log.d(TAG, "Thumbs up (simplified) terdeteksi pada tangan kanan!");
            return wristPos; // Kembalikan posisi pergelangan tangan sebagai acuan
        }

        // Anda bisa menambahkan logika untuk tangan kiri di sini dengan cara yang sama
        // PoseLandmark leftThumbTip = pose.getPoseLandmark(PoseLandmark.LEFT_THUMB);
        // PoseLandmark leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
        // ... (logika serupa)

        return null;
    }
}

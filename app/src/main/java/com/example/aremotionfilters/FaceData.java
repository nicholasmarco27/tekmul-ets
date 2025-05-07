package com.example.aremotionfilters; // Ganti dengan nama paket Anda yang sebenarnya

import android.graphics.PointF; // Import PointF
import android.graphics.Rect;

public class FaceData {
    private final Rect boundingBox; // Mungkin null jika ini adalah data gestur murni
    private final String emotion;
    private final float headEulerAngleZ;

    // BARU: Titik acuan untuk gestur, misal posisi pergelangan tangan
    private final PointF gestureAnchorPoint;
    private final float gestureRotation; // Opsional: rotasi gestur jika diperlukan

    // Konstanta untuk String emosi/gestur agar konsisten
    public static final String EMOTION_SMILING = "SMILING";
    public static final String EMOTION_EYES_CLOSED = "EYES_CLOSED";
    public static final String EMOTION_NEUTRAL = "NEUTRAL";
    public static final String EMOTION_LEFT_WINK = "LEFT_WINK";
    public static final String EMOTION_RIGHT_WINK = "RIGHT_WINK";
    public static final String GESTURE_THUMBS_UP = "GESTURE_THUMBS_UP"; // BARU


    // Konstruktor untuk deteksi wajah
    public FaceData(Rect boundingBox, String emotion, float headEulerAngleZ) {
        this.boundingBox = boundingBox;
        this.emotion = emotion;
        this.headEulerAngleZ = headEulerAngleZ;
        this.gestureAnchorPoint = null; // Tidak ada gestur
        this.gestureRotation = 0f;
    }

    // BARU: Konstruktor untuk deteksi gestur
    public FaceData(String gesture, PointF gestureAnchorPoint, float gestureRotation) {
        this.boundingBox = null; // Tidak ada bounding box wajah untuk gestur murni
        this.emotion = gesture;
        this.headEulerAngleZ = 0f; // Tidak relevan untuk gestur tangan saat ini
        this.gestureAnchorPoint = gestureAnchorPoint;
        this.gestureRotation = gestureRotation;
    }


    public Rect getBoundingBox() {
        return boundingBox;
    }

    public String getEmotion() { // Nama metode tetap sama untuk kompatibilitas
        return emotion;
    }

    public float getHeadEulerAngleZ() {
        return headEulerAngleZ;
    }

    // BARU: Getter untuk titik acuan gestur
    public PointF getGestureAnchorPoint() {
        return gestureAnchorPoint;
    }

    public float getGestureRotation() { return gestureRotation; }
}

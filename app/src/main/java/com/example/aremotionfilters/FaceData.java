package com.example.aremotionfilters; // Replace with your actual package name

import android.graphics.Rect;

public class FaceData {
    private final Rect boundingBox;
    private final String emotion; // Misalnya "SMILING", "EYES_CLOSED", "NEUTRAL", "LEFT_WINK", "RIGHT_WINK"
    private final float headEulerAngleZ; // Untuk orientasi efek

    // Konstanta untuk String emosi agar konsisten
    public static final String EMOTION_SMILING = "SMILING";
    public static final String EMOTION_EYES_CLOSED = "EYES_CLOSED";
    public static final String EMOTION_NEUTRAL = "NEUTRAL";
    public static final String EMOTION_LEFT_WINK = "LEFT_WINK";
    public static final String EMOTION_RIGHT_WINK = "RIGHT_WINK";


    public FaceData(Rect boundingBox, String emotion, float headEulerAngleZ) {
        this.boundingBox = boundingBox;
        this.emotion = emotion;
        this.headEulerAngleZ = headEulerAngleZ;
    }

    public Rect getBoundingBox() {
        return boundingBox;
    }

    public String getEmotion() {
        return emotion;
    }

    public float getHeadEulerAngleZ() {
        return headEulerAngleZ;
    }
}


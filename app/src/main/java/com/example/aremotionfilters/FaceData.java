package com.example.aremotionfilters; // Replace with your actual package name

import android.graphics.Rect;

public class FaceData {
    private final Rect boundingBox;
    private final String emotion; // e.g., "SMILING", "EYES_CLOSED", "NEUTRAL"
    private final float headEulerAngleZ; // For orientation of the effect

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

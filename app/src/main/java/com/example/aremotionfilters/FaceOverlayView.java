package com.example.aremotionfilters; // Ganti dengan nama paket Anda yang sebenarnya

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

    private final Paint boxPaint; // Untuk debugging kotak pembatas
    // private final Paint textPaint; // Untuk debugging teks emosi (opsional)

    private List<FaceData> facesData = new ArrayList<>();
    private int imageWidth; // Lebar gambar dari ImageAnalysis
    private int imageHeight; // Tinggi gambar dari ImageAnalysis
    private boolean isFrontCamera = true; // Status kamera depan/belakang

    // Bitmap untuk berbagai efek
    private Bitmap sparklesBitmap;
    private Bitmap zzzBitmap;
    private Bitmap leftWinkBitmap;
    private Bitmap rightWinkBitmap;

    private final Matrix matrix = new Matrix(); // Digunakan untuk transformasi bitmap (rotasi, skala)

    // Konstanta untuk mengatur ukuran dan posisi efek relatif terhadap wajah
    private static final float EFFECT_WIDTH_PERCENTAGE_OF_FACE = 1f; // Lebar efek sebagai persentase dari lebar wajah
    private static final float EFFECT_HORIZONTAL_OFFSET_FACTOR = 0.1f; // Offset horizontal dari tepi kanan wajah
    private static final float EFFECT_VERTICAL_OFFSET_FACTOR = 0.6f;   // Offset vertikal dari tepi atas wajah

    private static final String TAG = "FaceOverlayView";

    public FaceOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        // Inisialisasi Paint untuk menggambar kotak pembatas (untuk debugging)
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(3.0f);

        // Inisialisasi Paint untuk teks debug (opsional)
        // textPaint = new Paint();
        // textPaint.setColor(Color.WHITE);
        // textPaint.setTextSize(30.0f);

        // Memuat bitmap untuk efek dari drawable resources
        try {
            sparklesBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sparkles);
            zzzBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.zzz);
            // Pastikan Anda memiliki gambar left_wink_effect.png dan right_wink_effect.png di res/drawable
            leftWinkBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.left_wink_effect);
            rightWinkBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.right_wink_effect);
        } catch (Exception e) {
            Log.e(TAG, "Kesalahan memuat bitmap: " + e.getMessage());
            // Pertimbangkan untuk menangani kasus di mana bitmap gagal dimuat
        }
    }

    /**
     * Memperbarui daftar data wajah yang akan digambar.
     * Dipanggil oleh FaceEmotionAnalyzer setelah setiap frame diproses.
     * @param faces Daftar objek FaceData yang berisi informasi wajah.
     * @param imageWidth Lebar gambar asli yang dianalisis.
     * @param imageHeight Tinggi gambar asli yang dianalisis.
     * @param isFrontCamera True jika kamera depan yang digunakan, false jika kamera belakang.
     */
    public void updateFaces(List<FaceData> faces, int imageWidth, int imageHeight, boolean isFrontCamera) {
        this.facesData = faces;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.isFrontCamera = isFrontCamera;
        postInvalidate(); // Meminta View untuk digambar ulang
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Jangan menggambar apa pun jika tidak ada data wajah, dimensi gambar tidak valid,
        // atau tidak ada bitmap efek yang tersedia.
        boolean noBitmapsAvailable = sparklesBitmap == null && zzzBitmap == null && leftWinkBitmap == null && rightWinkBitmap == null;
        if (facesData == null || facesData.isEmpty() || imageWidth == 0 || imageHeight == 0 || noBitmapsAvailable) {
            return;
        }

        // Dapatkan dimensi View ini (FaceOverlayView)
        float viewWidth = getWidth();
        float viewHeight = getHeight();

        // Hitung faktor skala untuk mengubah koordinat dari gambar analisis ke koordinat View
        float scaleX = viewWidth / imageWidth;
        float scaleY = viewHeight / imageHeight;

        for (FaceData faceData : facesData) {
            Rect originalBox = faceData.getBoundingBox(); // Kotak pembatas dari ML Kit (koordinat gambar)
            RectF scaledBox = new RectF(); // Kotak pembatas yang akan diskalakan ke koordinat View

            // Transformasi koordinat kotak pembatas
            // Jika menggunakan kamera depan, koordinat X perlu dicerminkan
            if (isFrontCamera) {
                scaledBox.left = viewWidth - (originalBox.right * scaleX);
                scaledBox.right = viewWidth - (originalBox.left * scaleX);
            } else {
                scaledBox.left = originalBox.left * scaleX;
                scaledBox.right = originalBox.right * scaleX;
            }
            scaledBox.top = originalBox.top * scaleY;
            scaledBox.bottom = originalBox.bottom * scaleY;

            // --- Opsional: Gambar kotak pembatas untuk debugging ---
            // canvas.drawRect(scaledBox, boxPaint);
            // String debugText = String.format(java.util.Locale.US, "%s (Z:%.1f)", faceData.getEmotion(), faceData.getHeadEulerAngleZ());
            // canvas.drawText(debugText, scaledBox.left, scaledBox.top - 10, textPaint);
            // --- Akhir Debug Opsional ---

            Bitmap effectBitmap = null; // Bitmap efek yang akan digambar
            String emotion = faceData.getEmotion(); // Dapatkan string emosi dari FaceData

            // Tentukan bitmap efek mana yang akan digunakan berdasarkan emosi
            if (FaceData.EMOTION_SMILING.equals(emotion) && sparklesBitmap != null) {
                effectBitmap = sparklesBitmap;
            } else if (FaceData.EMOTION_EYES_CLOSED.equals(emotion) && zzzBitmap != null) {
                effectBitmap = zzzBitmap;
            } else if (FaceData.EMOTION_LEFT_WINK.equals(emotion) && leftWinkBitmap != null) {
                effectBitmap = leftWinkBitmap;
            } else if (FaceData.EMOTION_RIGHT_WINK.equals(emotion) && rightWinkBitmap != null) {
                effectBitmap = rightWinkBitmap;
            }
            // Tambahkan kondisi lain jika ada lebih banyak emosi/efek

            if (effectBitmap != null) {
                // 1. Hitung Ukuran Efek yang Diinginkan
                float faceActualWidth = scaledBox.width(); // Lebar sebenarnya dari wajah di View
                if (faceActualWidth <= 0) continue; // Lewati jika lebar wajah tidak valid

                // Lebar efek dihitung sebagai persentase dari lebar wajah
                float desiredEffectWidth = faceActualWidth * EFFECT_WIDTH_PERCENTAGE_OF_FACE;
                // Hitung tinggi efek untuk menjaga rasio aspek asli bitmap
                float aspectRatio = (float) effectBitmap.getHeight() / (float) effectBitmap.getWidth();
                float desiredEffectHeight = desiredEffectWidth * aspectRatio;

                if (desiredEffectWidth <=0 || desiredEffectHeight <=0) continue; // Lewati jika ukuran efek tidak valid

                // 2. Hitung Posisi Target untuk efek (kiri-atas dari persegi panjang tujuan efek)
                // Kita ingin memposisikan pusat efek relatif terhadap kanan-atas wajah.
                float effectCenterX = scaledBox.right + (faceActualWidth * EFFECT_HORIZONTAL_OFFSET_FACTOR);
                float effectCenterY = scaledBox.top - (desiredEffectHeight * EFFECT_VERTICAL_OFFSET_FACTOR); // Kurangi untuk bergerak ke atas

                // Hitung koordinat kiri-atas untuk persegi panjang tujuan efek
                float targetEffectX = effectCenterX - (desiredEffectWidth / 2f);
                float targetEffectY = effectCenterY - (desiredEffectHeight / 2f);

                // 3. Tentukan persegi panjang sumber (seluruh bitmap asli) dan tujuan (di mana efek akan digambar di View)
                RectF srcRectF = new RectF(0, 0, effectBitmap.getWidth(), effectBitmap.getHeight());
                RectF dstRectF = new RectF(targetEffectX, targetEffectY, targetEffectX + desiredEffectWidth, targetEffectY + desiredEffectHeight);

                // 4. Atur Matriks untuk penskalaan, rotasi, dan translasi
                matrix.reset(); // Reset matriks untuk transformasi baru
                // Atur matriks untuk menskalakan bitmap sumber agar sesuai dengan persegi panjang tujuan
                matrix.setRectToRect(srcRectF, dstRectF, Matrix.ScaleToFit.CENTER); // Atau ScaleToFit.FILL
                // Sekarang, putar di sekitar pusat persegi panjang tujuan ini
                // Rotasi berdasarkan sudut Euler Z dari kepala
                matrix.postRotate(faceData.getHeadEulerAngleZ(), dstRectF.centerX(), dstRectF.centerY());

                // 5. Gambar bitmap efek menggunakan matriks yang telah dikonfigurasi
                canvas.drawBitmap(effectBitmap, matrix, null); // Paint null berarti menggunakan paint default bitmap
            }
        }
    }
}

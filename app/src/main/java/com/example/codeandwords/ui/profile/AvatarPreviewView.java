package com.example.codeandwords.ui.profile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class AvatarPreviewView extends View {

    private boolean showBackground = true;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private AvatarConfig config;

    public AvatarPreviewView(Context context) {
        super(context);
        init();
    }

    public AvatarPreviewView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AvatarPreviewView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        config = AvatarPrefs.load(getContext());
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setAvatarConfig(AvatarConfig config) {
        this.config = config;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (config == null) {
            config = AvatarPrefs.load(getContext());
        }

        if (config == null) {
            return;
        }

        float viewW = getWidth();
        float viewH = getHeight();

        if (viewW <= 0 || viewH <= 0) {
            return;
        }

        if (showBackground) {
            canvas.drawColor(config.backgroundColor);
        }

        float size = Math.min(viewW, viewH) * 0.88f;
        float left = (viewW - size) / 2f;
        float top = (viewH - size) / 2f - size * 0.02f;
        if (top < 0f) top = 0f;

        int saveCount = canvas.save();
        canvas.translate(left, top);
        drawAvatar(canvas, size, size);
        canvas.restoreToCount(saveCount);
    }

    private void drawAvatar(Canvas canvas, float w, float h) {
        float cx = w / 2f;

        drawHairBack(canvas, cx, h, w);

        drawNeck(canvas, cx, h, w);
        drawBody(canvas, cx, h, w);

        drawFace(canvas, cx, h, w);
        drawEars(canvas, cx, h, w);

        drawEyes(canvas, cx, h, w);
        drawBrows(canvas, cx, h, w);
        drawNose(canvas, cx, h, w);
        drawSmile(canvas, cx, h, w);

        drawHairForeheadBase(canvas, cx, h, w);
        drawHairFront(canvas, cx, h, w);

        drawGlasses(canvas, cx, h, w);
        drawHat(canvas, cx, h, w);
        drawEarrings(canvas, cx, h, w);
    }

    private void drawNeck(Canvas canvas, float cx, float h, float w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(config.skinColor);

        RectF neck = new RectF(
                cx - w * 0.070f,
                h * 0.500f,
                cx + w * 0.070f,
                h * 0.685f
        );

        canvas.drawRoundRect(neck, w * 0.025f, w * 0.025f, paint);
    }

    private void drawBody(Canvas canvas, float cx, float h, float w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(config.clothesColor);

        float top = h * 0.575f;
        float bottom = h * 1.03f;

        Path path = new Path();

        if (config.bodyStyle == 0) {
            path.moveTo(cx - w * 0.145f, top);
            path.lineTo(cx + w * 0.145f, top);
            path.quadTo(cx + w * 0.270f, h * 0.780f, cx + w * 0.240f, bottom);
            path.lineTo(cx - w * 0.240f, bottom);
            path.quadTo(cx - w * 0.270f, h * 0.780f, cx - w * 0.145f, top);
        } else if (config.bodyStyle == 1) {
            path.moveTo(cx - w * 0.145f, top);
            path.lineTo(cx + w * 0.145f, top);
            path.lineTo(cx + w * 0.220f, bottom);
            path.lineTo(cx - w * 0.220f, bottom);
        } else {
            path.moveTo(cx - w * 0.150f, top);
            path.lineTo(cx + w * 0.150f, top);
            path.quadTo(cx + w * 0.310f, h * 0.780f, cx + w * 0.290f, bottom);
            path.lineTo(cx - w * 0.290f, bottom);
            path.quadTo(cx - w * 0.310f, h * 0.780f, cx - w * 0.150f, top);
        }

        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawFace(Canvas canvas, float cx, float h, float w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(config.skinColor);

        RectF face = new RectF(cx - w * 0.14f, h * 0.20f, cx + w * 0.14f, h * 0.53f);
        canvas.drawRoundRect(face, w * 0.075f, w * 0.075f, paint);
    }

    private void drawEars(Canvas canvas, float cx, float h, float w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(config.skinColor);
        canvas.drawCircle(cx - w * 0.165f, h * 0.38f, w * 0.026f, paint);
        canvas.drawCircle(cx + w * 0.165f, h * 0.38f, w * 0.026f, paint);
    }

    private void drawEyes(Canvas canvas, float cx, float h, float w) {
        paint.setStyle(Paint.Style.FILL);

        if (config.faceShape == 9) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(w * 0.012f);
            paint.setColor(Color.WHITE);

            RectF leftClosed = new RectF(cx - w * 0.095f, h * 0.325f, cx - w * 0.020f, h * 0.395f);
            RectF rightClosed = new RectF(cx + w * 0.020f, h * 0.325f, cx + w * 0.095f, h * 0.395f);

            canvas.drawArc(leftClosed, 205f, 120f, false, paint);
            canvas.drawArc(rightClosed, 215f, 120f, false, paint);

            paint.setStyle(Paint.Style.FILL);
            return;
        }

        if (config.faceShape == 10) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);

            RectF leftEye = new RectF(cx - w * 0.105f, h * 0.300f, cx - w * 0.015f, h * 0.400f);
            RectF rightEye = new RectF(cx + w * 0.015f, h * 0.300f, cx + w * 0.105f, h * 0.400f);

            canvas.drawOval(leftEye, paint);
            canvas.drawOval(rightEye, paint);

            paint.setColor(config.eyeColor);
            canvas.drawCircle(cx - w * 0.050f, h * 0.350f, w * 0.020f, paint);
            canvas.drawCircle(cx + w * 0.050f, h * 0.350f, w * 0.020f, paint);

            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx - w * 0.058f, h * 0.340f, w * 0.007f, paint);
            canvas.drawCircle(cx - w * 0.043f, h * 0.360f, w * 0.005f, paint);
            canvas.drawCircle(cx + w * 0.042f, h * 0.340f, w * 0.007f, paint);
            canvas.drawCircle(cx + w * 0.057f, h * 0.360f, w * 0.005f, paint);
            return;
        }

        if (config.faceShape == 11) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(w * 0.011f);
            paint.setColor(Color.WHITE);

            RectF wink = new RectF(cx - w * 0.095f, h * 0.330f, cx - w * 0.020f, h * 0.385f);
            canvas.drawArc(wink, 200f, 120f, false, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);

            RectF rightEye = new RectF(cx + w * 0.020f, h * 0.310f, cx + w * 0.095f, h * 0.390f);
            canvas.drawOval(rightEye, paint);

            paint.setColor(config.eyeColor);
            drawPupil(canvas, cx + w * 0.050f, h * 0.350f, w * 0.018f);
            return;
        }

        float eyeTop = h * 0.31f;
        float eyeBottom = h * 0.39f;

        RectF leftEye = new RectF(cx - w * 0.095f, eyeTop, cx - w * 0.02f, eyeBottom);
        RectF rightEye = new RectF(cx + w * 0.02f, eyeTop, cx + w * 0.095f, eyeBottom);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);

        switch (config.faceShape) {
            case 5:
            case 7:
            case 8:
                leftEye = new RectF(cx - w * 0.095f, h * 0.325f, cx - w * 0.02f, h * 0.39f);
                rightEye = new RectF(cx + w * 0.02f, h * 0.325f, cx + w * 0.095f, h * 0.39f);
                break;

            case 4:
                leftEye = new RectF(cx - w * 0.10f, h * 0.305f, cx - w * 0.015f, h * 0.395f);
                rightEye = new RectF(cx + w * 0.015f, h * 0.305f, cx + w * 0.10f, h * 0.395f);
                break;
        }

        canvas.drawOval(leftEye, paint);
        canvas.drawOval(rightEye, paint);

        switch (config.faceShape) {
            case 0:
            default:
                drawPupil(canvas, cx - w * 0.046f, h * 0.350f, w * 0.018f);
                drawPupil(canvas, cx + w * 0.046f, h * 0.350f, w * 0.018f);
                break;
            case 1:
                drawPupil(canvas, cx - w * 0.040f, h * 0.348f, w * 0.018f);
                drawPupil(canvas, cx + w * 0.052f, h * 0.348f, w * 0.018f);
                break;
            case 2:
                drawPupil(canvas, cx - w * 0.035f, h * 0.347f, w * 0.018f);
                drawPupil(canvas, cx + w * 0.055f, h * 0.347f, w * 0.018f);
                break;
            case 3:
                drawPupil(canvas, cx - w * 0.030f, h * 0.352f, w * 0.018f);
                drawPupil(canvas, cx + w * 0.060f, h * 0.346f, w * 0.018f);
                break;
            case 4:
                drawPupil(canvas, cx - w * 0.046f, h * 0.350f, w * 0.020f);
                drawPupil(canvas, cx + w * 0.046f, h * 0.350f, w * 0.020f);
                break;
            case 5:
                drawPupil(canvas, cx - w * 0.050f, h * 0.356f, w * 0.017f);
                drawPupil(canvas, cx + w * 0.050f, h * 0.356f, w * 0.017f);
                break;
            case 6:
                drawPupil(canvas, cx - w * 0.038f, h * 0.346f, w * 0.018f);
                drawPupil(canvas, cx + w * 0.054f, h * 0.350f, w * 0.018f);
                break;
            case 7:
                drawPupil(canvas, cx - w * 0.053f, h * 0.356f, w * 0.017f);
                drawPupil(canvas, cx + w * 0.040f, h * 0.356f, w * 0.017f);
                break;
            case 8:
                drawPupil(canvas, cx - w * 0.046f, h * 0.360f, w * 0.015f);
                drawPupil(canvas, cx + w * 0.046f, h * 0.360f, w * 0.015f);
                break;
        }
    }

    private void drawPupil(Canvas canvas, float x, float y, float r) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(config.eyeColor);
        canvas.drawCircle(x, y, r, paint);

        paint.setColor(Color.WHITE);
        canvas.drawCircle(x + r * 0.35f, y - r * 0.35f, r * 0.28f, paint);
    }

    private void drawBrows(Canvas canvas, float cx, float h, float w) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(config.hairColor);
        paint.setStrokeWidth(w * 0.0105f);

        switch (config.faceShape) {
            case 0:
            default:
                canvas.drawLine(cx - w * 0.080f, h * 0.302f, cx - w * 0.032f, h * 0.294f, paint);
                canvas.drawLine(cx + w * 0.032f, h * 0.294f, cx + w * 0.080f, h * 0.302f, paint);
                break;
            case 1:
                canvas.drawLine(cx - w * 0.082f, h * 0.300f, cx - w * 0.035f, h * 0.292f, paint);
                canvas.drawLine(cx + w * 0.034f, h * 0.292f, cx + w * 0.078f, h * 0.300f, paint);
                break;
            case 2:
                canvas.drawLine(cx - w * 0.082f, h * 0.298f, cx - w * 0.037f, h * 0.289f, paint);
                canvas.drawLine(cx + w * 0.037f, h * 0.289f, cx + w * 0.082f, h * 0.298f, paint);
                break;
            case 3:
                canvas.drawLine(cx - w * 0.085f, h * 0.306f, cx - w * 0.034f, h * 0.292f, paint);
                canvas.drawLine(cx + w * 0.032f, h * 0.292f, cx + w * 0.072f, h * 0.305f, paint);
                break;
            case 4:
                canvas.drawLine(cx - w * 0.082f, h * 0.292f, cx - w * 0.032f, h * 0.284f, paint);
                canvas.drawLine(cx + w * 0.032f, h * 0.284f, cx + w * 0.082f, h * 0.292f, paint);
                break;
            case 5:
                canvas.drawLine(cx - w * 0.082f, h * 0.292f, cx - w * 0.032f, h * 0.304f, paint);
                canvas.drawLine(cx + w * 0.032f, h * 0.304f, cx + w * 0.082f, h * 0.292f, paint);
                break;
            case 6:
                canvas.drawLine(cx - w * 0.078f, h * 0.301f, cx - w * 0.034f, h * 0.292f, paint);
                canvas.drawLine(cx + w * 0.034f, h * 0.292f, cx + w * 0.078f, h * 0.301f, paint);
                break;
            case 7:
                canvas.drawLine(cx - w * 0.085f, h * 0.290f, cx - w * 0.032f, h * 0.304f, paint);
                canvas.drawLine(cx + w * 0.032f, h * 0.304f, cx + w * 0.085f, h * 0.290f, paint);
                break;
            case 8:
                canvas.drawLine(cx - w * 0.082f, h * 0.304f, cx - w * 0.032f, h * 0.304f, paint);
                canvas.drawLine(cx + w * 0.032f, h * 0.304f, cx + w * 0.082f, h * 0.304f, paint);
                break;
            case 9:
                canvas.drawArc(
                        new RectF(cx - w * 0.085f, h * 0.282f, cx - w * 0.030f, h * 0.310f),
                        200f, 110f, false, paint
                );
                canvas.drawArc(
                        new RectF(cx + w * 0.030f, h * 0.282f, cx + w * 0.085f, h * 0.310f),
                        230f, 110f, false, paint
                );
                break;
            case 10:
                canvas.drawLine(cx - w * 0.086f, h * 0.292f, cx - w * 0.030f, h * 0.282f, paint);
                canvas.drawLine(cx + w * 0.030f, h * 0.282f, cx + w * 0.086f, h * 0.292f, paint);
                break;
            case 11:
                canvas.drawLine(cx - w * 0.082f, h * 0.305f, cx - w * 0.032f, h * 0.292f, paint);
                canvas.drawLine(cx + w * 0.032f, h * 0.292f, cx + w * 0.082f, h * 0.300f, paint);
                break;
        }

        paint.setStyle(Paint.Style.FILL);
    }

    private void drawNose(Canvas canvas, float cx, float h, float w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(adjustBrightness(config.skinColor, 0.90f));

        Path nose = new Path();
        nose.moveTo(cx, h * 0.378f);
        nose.lineTo(cx + w * 0.020f, h * 0.448f);
        nose.lineTo(cx - w * 0.010f, h * 0.448f);
        nose.close();

        canvas.drawPath(nose, paint);
    }

    private void drawSmile(Canvas canvas, float cx, float h, float w) {
        int mouthColor = adjustBrightness(config.skinColor, 0.68f);
        int lipColor = Color.parseColor("#B94E5C");
        int tongueColor = Color.parseColor("#E57A88");
        int darkMouth = Color.parseColor("#8F2B38");

        paint.setStrokeWidth(w * 0.010f);

        switch (config.faceShape) {
            case 0:
            default: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(mouthColor);
                RectF mouth = new RectF(cx - w * 0.055f, h * 0.41f, cx + w * 0.005f, h * 0.485f);
                canvas.drawArc(mouth, 18f, 145f, false, paint);
                break;
            }
            case 1: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(mouthColor);
                RectF mouth = new RectF(cx - w * 0.050f, h * 0.420f, cx + w * 0.030f, h * 0.482f);
                canvas.drawArc(mouth, 12f, 150f, false, paint);
                break;
            }
            case 2: {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(lipColor);

                Path happy = new Path();
                happy.moveTo(cx - w * 0.018f, h * 0.446f);
                happy.quadTo(cx + w * 0.018f, h * 0.432f, cx + w * 0.050f, h * 0.456f);
                happy.quadTo(cx + w * 0.025f, h * 0.492f, cx - w * 0.010f, h * 0.486f);
                happy.close();
                canvas.drawPath(happy, paint);

                paint.setColor(tongueColor);
                canvas.drawOval(new RectF(cx + w * 0.002f, h * 0.458f, cx + w * 0.030f, h * 0.484f), paint);
                break;
            }
            case 3: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(mouthColor);
                RectF mouth = new RectF(cx - w * 0.050f, h * 0.426f, cx + w * 0.026f, h * 0.480f);
                canvas.drawArc(mouth, 18f, 120f, false, paint);
                break;
            }
            case 4: {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(mouthColor);
                canvas.drawCircle(cx + w * 0.010f, h * 0.458f, w * 0.018f, paint);
                break;
            }
            case 5: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(mouthColor);
                RectF mouth = new RectF(cx - w * 0.028f, h * 0.455f, cx + w * 0.048f, h * 0.500f);
                canvas.drawArc(mouth, 190f, 115f, false, paint);
                break;
            }
            case 6: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(mouthColor);

                Path pout = new Path();
                pout.moveTo(cx - w * 0.006f, h * 0.438f);
                pout.quadTo(cx + w * 0.022f, h * 0.455f, cx + w * 0.006f, h * 0.482f);
                pout.quadTo(cx - w * 0.020f, h * 0.464f, cx - w * 0.006f, h * 0.438f);
                canvas.drawPath(pout, paint);
                break;
            }
            case 7: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(mouthColor);
                RectF mouth = new RectF(cx - w * 0.030f, h * 0.458f, cx + w * 0.035f, h * 0.486f);
                canvas.drawArc(mouth, 195f, 95f, false, paint);
                break;
            }
            case 8: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(mouthColor);
                canvas.drawLine(cx - w * 0.020f, h * 0.462f, cx + w * 0.030f, h * 0.462f, paint);
                break;
            }
            case 9: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(w * 0.014f);
                paint.setColor(mouthColor);

                RectF bigSmile = new RectF(cx - w * 0.062f, h * 0.405f, cx + w * 0.062f, h * 0.505f);
                canvas.drawArc(bigSmile, 25f, 130f, false, paint);
                break;
            }
            case 10: {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(darkMouth);

                Path laugh = new Path();
                laugh.moveTo(cx - w * 0.045f, h * 0.438f);
                laugh.quadTo(cx + w * 0.005f, h * 0.418f, cx + w * 0.055f, h * 0.440f);
                laugh.quadTo(cx + w * 0.035f, h * 0.505f, cx - w * 0.030f, h * 0.495f);
                laugh.close();
                canvas.drawPath(laugh, paint);

                paint.setColor(Color.WHITE);
                canvas.drawOval(new RectF(cx - w * 0.030f, h * 0.435f, cx + w * 0.040f, h * 0.458f), paint);

                paint.setColor(tongueColor);
                canvas.drawOval(new RectF(cx - w * 0.010f, h * 0.472f, cx + w * 0.035f, h * 0.498f), paint);
                break;
            }
            case 11: {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(lipColor);

                Path sideSmile = new Path();
                sideSmile.moveTo(cx - w * 0.018f, h * 0.448f);
                sideSmile.quadTo(cx + w * 0.030f, h * 0.430f, cx + w * 0.058f, h * 0.458f);
                sideSmile.quadTo(cx + w * 0.030f, h * 0.488f, cx - w * 0.006f, h * 0.480f);
                sideSmile.close();
                canvas.drawPath(sideSmile, paint);

                paint.setColor(Color.WHITE);
                canvas.drawOval(new RectF(cx + w * 0.004f, h * 0.445f, cx + w * 0.040f, h * 0.460f), paint);
                break;
            }
        }

        paint.setStrokeWidth(w * 0.010f);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawGlasses(Canvas canvas, float cx, float h, float w) {
        if (config.glassesStyle == 0) return;

        int frameColor = config.glassesColor;

        if (config.glassesStyle == 1) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(w * 0.009f);
            paint.setColor(frameColor);
            canvas.drawCircle(cx - w * 0.046f, h * 0.35f, w * 0.034f, paint);
            canvas.drawCircle(cx + w * 0.046f, h * 0.35f, w * 0.034f, paint);
            canvas.drawLine(cx - w * 0.012f, h * 0.35f, cx + w * 0.012f, h * 0.35f, paint);
        } else {
            RectF left = new RectF(cx - w * 0.090f, h * 0.320f, cx - w * 0.014f, h * 0.385f);
            RectF right = new RectF(cx + w * 0.014f, h * 0.320f, cx + w * 0.090f, h * 0.385f);

            if (config.glassesStyle == 3) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#40505D"));
                canvas.drawRoundRect(left, w * 0.022f, w * 0.022f, paint);
                canvas.drawRoundRect(right, w * 0.022f, w * 0.022f, paint);
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(w * 0.009f);
            paint.setColor(frameColor);
            canvas.drawRoundRect(left, w * 0.022f, w * 0.022f, paint);
            canvas.drawRoundRect(right, w * 0.022f, w * 0.022f, paint);
            canvas.drawLine(cx - w * 0.014f, h * 0.352f, cx + w * 0.014f, h * 0.352f, paint);
        }

        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHat(Canvas canvas, float cx, float h, float w) {
        if (config.hatStyle == 0) return;

        int hatColor = config.hatColor;
        int darkHatColor = adjustBrightness(hatColor, 0.78f);
        int lightHatColor = adjustBrightness(hatColor, 1.12f);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(hatColor);

        switch (config.hatStyle) {
            case 1: {
                RectF crown = new RectF(cx - w * 0.145f, h * 0.125f, cx + w * 0.145f, h * 0.240f);
                canvas.drawRoundRect(crown, w * 0.070f, w * 0.070f, paint);

                paint.setColor(lightHatColor);
                RectF leftPanel = new RectF(cx - w * 0.120f, h * 0.128f, cx - w * 0.015f, h * 0.235f);
                canvas.drawRoundRect(leftPanel, w * 0.055f, w * 0.055f, paint);

                paint.setColor(darkHatColor);
                RectF band = new RectF(cx - w * 0.150f, h * 0.215f, cx + w * 0.150f, h * 0.252f);
                canvas.drawRoundRect(band, w * 0.018f, w * 0.018f, paint);

                Path visor = new Path();
                visor.moveTo(cx - w * 0.040f, h * 0.235f);
                visor.quadTo(cx + w * 0.105f, h * 0.220f, cx + w * 0.185f, h * 0.260f);
                visor.quadTo(cx + w * 0.115f, h * 0.290f, cx - w * 0.025f, h * 0.265f);
                visor.close();
                canvas.drawPath(visor, paint);
                break;
            }
            case 2: {
                RectF crown = new RectF(cx - w * 0.150f, h * 0.115f, cx + w * 0.150f, h * 0.245f);
                canvas.drawRoundRect(crown, w * 0.080f, w * 0.080f, paint);

                paint.setColor(lightHatColor);
                canvas.drawCircle(cx, h * 0.112f, w * 0.018f, paint);

                paint.setColor(adjustBrightness(hatColor, 0.92f));
                canvas.drawLine(cx - w * 0.070f, h * 0.125f, cx - w * 0.070f, h * 0.220f, paint);
                canvas.drawLine(cx, h * 0.122f, cx, h * 0.220f, paint);
                canvas.drawLine(cx + w * 0.070f, h * 0.125f, cx + w * 0.070f, h * 0.220f, paint);

                paint.setColor(darkHatColor);
                RectF visor = new RectF(cx - w * 0.165f, h * 0.220f, cx + w * 0.165f, h * 0.275f);
                canvas.drawRoundRect(visor, w * 0.030f, w * 0.030f, paint);
                break;
            }
            case 3: {
                RectF band = new RectF(cx - w * 0.155f, h * 0.175f, cx + w * 0.155f, h * 0.255f);
                canvas.drawRoundRect(band, w * 0.020f, w * 0.020f, paint);

                paint.setColor(lightHatColor);
                RectF top = new RectF(cx - w * 0.150f, h * 0.120f, cx + w * 0.150f, h * 0.205f);
                canvas.drawRoundRect(top, w * 0.060f, w * 0.060f, paint);

                paint.setColor(darkHatColor);
                canvas.drawLine(cx, h * 0.125f, cx, h * 0.255f, paint);

                Path tail = new Path();
                tail.moveTo(cx - w * 0.130f, h * 0.220f);
                tail.lineTo(cx - w * 0.220f, h * 0.705f);
                tail.quadTo(cx - w * 0.145f, h * 0.725f, cx - w * 0.125f, h * 0.250f);
                tail.close();
                canvas.drawPath(tail, paint);
                break;
            }
            case 4: {
                RectF beanie = new RectF(cx - w * 0.150f, h * 0.105f, cx + w * 0.150f, h * 0.245f);
                canvas.drawRoundRect(beanie, w * 0.070f, w * 0.070f, paint);

                paint.setColor(darkHatColor);
                RectF rim = new RectF(cx - w * 0.165f, h * 0.220f, cx + w * 0.165f, h * 0.270f);
                canvas.drawRoundRect(rim, w * 0.022f, w * 0.022f, paint);

                paint.setColor(adjustBrightness(hatColor, 0.90f));
                canvas.drawLine(cx + w * 0.070f, h * 0.165f, cx + w * 0.070f, h * 0.220f, paint);
                canvas.drawLine(cx + w * 0.115f, h * 0.170f, cx + w * 0.115f, h * 0.220f, paint);
                break;
            }
            case 5: {
                Path cap = new Path();
                cap.moveTo(cx - w * 0.145f, h * 0.235f);
                cap.quadTo(cx - w * 0.120f, h * 0.105f, cx + w * 0.005f, h * 0.100f);
                cap.quadTo(cx + w * 0.165f, h * 0.105f, cx + w * 0.160f, h * 0.250f);
                cap.lineTo(cx - w * 0.145f, h * 0.250f);
                cap.close();
                canvas.drawPath(cap, paint);

                paint.setColor(darkHatColor);
                RectF rim = new RectF(cx - w * 0.165f, h * 0.220f, cx + w * 0.165f, h * 0.275f);
                canvas.drawRoundRect(rim, w * 0.022f, w * 0.022f, paint);

                paint.setColor(lightHatColor);
                RectF fold = new RectF(cx - w * 0.130f, h * 0.095f, cx - w * 0.020f, h * 0.150f);
                canvas.drawRoundRect(fold, w * 0.030f, w * 0.030f, paint);
                break;
            }
        }

        paint.setStyle(Paint.Style.FILL);
    }

    private void drawEarrings(Canvas canvas, float cx, float h, float w) {
        if (config.earringsStyle == 0) return;

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#E3E8EC"));
        paint.setStrokeWidth(w * 0.010f);

        if (config.earringsStyle == 1) {
            canvas.drawCircle(cx - w * 0.165f, h * 0.40f, w * 0.030f, paint);
            canvas.drawCircle(cx + w * 0.165f, h * 0.40f, w * 0.030f, paint);
        } else if (config.earringsStyle == 2) {
            canvas.drawCircle(cx - w * 0.165f, h * 0.40f, w * 0.018f, paint);
            canvas.drawCircle(cx + w * 0.165f, h * 0.40f, w * 0.018f, paint);
        }

        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * База-"шапка" на лбу — только для длинных женских стилей (10-34).
     * Для мужских (1, 3, 4, 5) и каре (2) — собственный силуэт.
     */
    private void drawHairForeheadBase(Canvas canvas, float cx, float h, float w) {
        // Стили с собственным силуэтом — база НЕ нужна
        if (config.hairStyle >= 1 && config.hairStyle <= 5) {
            return;
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(config.hairColor);

        switch (config.hairStyle) {
            case 0:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
            case 31:
            case 32:
            case 33:
            case 34:
                RectF topBase = new RectF(
                        cx - w * 0.155f,
                        h * 0.165f,
                        cx + w * 0.155f,
                        h * 0.285f
                );
                canvas.drawRoundRect(topBase, w * 0.05f, w * 0.05f, paint);

                RectF leftTemple = new RectF(
                        cx - w * 0.155f,
                        h * 0.20f,
                        cx - w * 0.115f,
                        h * 0.29f
                );
                RectF rightTemple = new RectF(
                        cx + w * 0.115f,
                        h * 0.20f,
                        cx + w * 0.155f,
                        h * 0.29f
                );

                canvas.drawRoundRect(leftTemple, w * 0.03f, w * 0.03f, paint);
                canvas.drawRoundRect(rightTemple, w * 0.03f, w * 0.03f, paint);
                break;

            default:
                break;
        }
    }

    private void drawHairBack(Canvas canvas, float cx, float h, float w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(config.hairColor);

        switch (config.hairStyle) {
            case 0: drawLongStraightHair(canvas, cx, h, w); break;

            case 1: drawShortRoundedBack(canvas, cx, h, w); break;
            case 2: drawCareHairBack(canvas, cx, h, w); break;
            case 3: drawCrewCutBack(canvas, cx, h, w); break;
            case 4: drawSidePartManBack(canvas, cx, h, w); break;
            case 5: drawCurlyShortManBack(canvas, cx, h, w); break;

            case 10: drawBobHair(canvas, cx, h, w); break;
            case 11: drawMediumWavesHair(canvas, cx, h, w); break;
            case 12: drawRoundedWavesHair(canvas, cx, h, w); break;
            case 13: drawLongCurtainHair(canvas, cx, h, w); break;
            case 14: drawBigLongWavesHair(canvas, cx, h, w); break;
            case 15: drawAsymmetricLongHair(canvas, cx, h, w); break;

            case 16: drawMessyBunHair(canvas, cx, h, w); break;
            case 17: drawHighBunHair(canvas, cx, h, w); break;
            case 18: drawTwinPonyHair(canvas, cx, h, w); break;
            case 19: drawBraidsHairClassic(canvas, cx, h, w); break;
            case 20: drawBraidsHairThin(canvas, cx, h, w); break;
            case 21: drawBraidsHairPuffy(canvas, cx, h, w); break;
            case 22: drawBraidsHairDoubleBuns(canvas, cx, h, w); break;

            case 23: drawCurlyHairSoft(canvas, cx, h, w); break;
            case 24: drawCurlyHairBig(canvas, cx, h, w); break;
            case 25: drawCurlyHairLong(canvas, cx, h, w); break;

            case 26: drawPonyHairHigh(canvas, cx, h, w); break;
            case 27: drawPonyHairLow(canvas, cx, h, w); break;
            case 28: drawPonyHairSide(canvas, cx, h, w); break;

            case 29: drawBunHairClassic(canvas, cx, h, w); break;
            case 30: drawBunHairCurly(canvas, cx, h, w); break;
            case 31: drawBobHairShortRounded(canvas, cx, h, w); break;
            case 32: drawBobHairFrench(canvas, cx, h, w); break;
            case 33: drawWavyHairLongRibbon(canvas, cx, h, w); break;
            case 34: drawWavyHairLongSoft(canvas, cx, h, w); break;

            default: drawLongStraightHair(canvas, cx, h, w); break;
        }
    }

    private void drawHairFront(Canvas canvas, float cx, float h, float w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(config.hairColor);

        switch (config.hairStyle) {
            case 0: drawLongStraightFront(canvas, cx, h, w); break;

            case 1: drawShortRoundedFront(canvas, cx, h, w); break;
            case 2: drawCareHairFront(canvas, cx, h, w); break;
            case 3: drawCrewCutFront(canvas, cx, h, w); break;
            case 4: drawSidePartManFront(canvas, cx, h, w); break;
            case 5: drawCurlyShortManFront(canvas, cx, h, w); break;

            case 10: drawBobFront(canvas, cx, h, w); break;
            case 11: drawMediumWavesFront(canvas, cx, h, w); break;
            case 12: drawRoundedWavesFront(canvas, cx, h, w); break;
            case 13: drawLongCurtainFront(canvas, cx, h, w); break;
            case 14: drawBigLongWavesFront(canvas, cx, h, w); break;
            case 15: drawAsymmetricLongFront(canvas, cx, h, w); break;

            case 16: drawMessyBunFront(canvas, cx, h, w); break;
            case 17: drawHighBunFront(canvas, cx, h, w); break;
            case 18: drawTwinPonyFront(canvas, cx, h, w); break;
            case 19: drawBraidsFrontClassic(canvas, cx, h, w); break;
            case 20: drawBraidsFrontThin(canvas, cx, h, w); break;
            case 21: drawBraidsFrontPuffy(canvas, cx, h, w); break;
            case 22: drawBraidsFrontDoubleBuns(canvas, cx, h, w); break;

            case 23: drawCurlyFrontSoft(canvas, cx, h, w); break;
            case 24: drawCurlyFrontBig(canvas, cx, h, w); break;
            case 25: drawCurlyFrontLong(canvas, cx, h, w); break;

            case 26: drawPonyFrontHigh(canvas, cx, h, w); break;
            case 27: drawPonyFrontLow(canvas, cx, h, w); break;
            case 28: drawPonyFrontSide(canvas, cx, h, w); break;

            case 29: drawBunFrontClassic(canvas, cx, h, w); break;
            case 30: drawBunFrontCurly(canvas, cx, h, w); break;
            case 31: drawBobFrontShortRounded(canvas, cx, h, w); break;
            case 32: drawBobFrontFrench(canvas, cx, h, w); break;
            case 33: drawWavyFrontLongRibbon(canvas, cx, h, w); break;
            case 34: drawWavyFrontLongSoft(canvas, cx, h, w); break;

            default: drawLongStraightFront(canvas, cx, h, w); break;
        }
    }

    // === ДЛИННЫЕ ВОЛОСЫ (женские базовые) ===
    private void drawLongStraightHair(Canvas c, float cx, float h, float w) {
        RectF hair = new RectF(cx - w * 0.19f, h * 0.16f, cx + w * 0.19f, h * 0.88f);
        c.drawRoundRect(hair, w * 0.10f, w * 0.10f, paint);
    }

    private void drawLongStraightFront(Canvas c, float cx, float h, float w) {
        Path bangs = new Path();
        bangs.moveTo(cx - w * 0.16f, h * 0.23f);
        bangs.quadTo(cx - w * 0.10f, h * 0.14f, cx, h * 0.18f);
        bangs.quadTo(cx + w * 0.10f, h * 0.14f, cx + w * 0.16f, h * 0.23f);
        bangs.lineTo(cx + w * 0.16f, h * 0.275f);
        bangs.quadTo(cx, h * 0.21f, cx - w * 0.16f, h * 0.275f);
        bangs.close();
        c.drawPath(bangs, paint);
    }

    // ========================================
    // 1 — Короткая мужская стрижка (округлая)
    // ========================================
    private void drawShortRoundedBack(Canvas canvas, float cx, float h, float w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(config.hairColor);

        // Рисуем основной объем сзади, чуть шире лица
        RectF backHair = new RectF(
                cx - w * 0.155f,
                h * 0.150f,
                cx + w * 0.155f,
                h * 0.420f
        );
        canvas.drawRoundRect(backHair, w * 0.10f, w * 0.10f, paint);
    }

    private void drawShortRoundedFront(Canvas canvas, float cx, float h, float w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(config.hairColor);

        // Основная "шапка" волос
        RectF hairTop = new RectF(
                cx - w * 0.150f,
                h * 0.140f,
                cx + w * 0.150f,
                h * 0.320f
        );
        canvas.drawRoundRect(hairTop, w * 0.08f, w * 0.08f, paint);

        // Виски (заканчиваются ДО начала ушей)
        Path temples = new Path();
        // Левый висок
        temples.moveTo(cx - w * 0.150f, h * 0.280f);
        temples.lineTo(cx - w * 0.135f, h * 0.340f);
        temples.lineTo(cx - w * 0.100f, h * 0.280f);
        // Правый висок
        temples.moveTo(cx + w * 0.150f, h * 0.280f);
        temples.lineTo(cx + w * 0.135f, h * 0.340f);
        temples.lineTo(cx + w * 0.100f, h * 0.280f);

        canvas.drawPath(temples, paint);

        // Челка (небольшой изгиб для естественности)
        Path bangs = new Path();
        bangs.moveTo(cx - w * 0.150f, h * 0.250f);
        bangs.quadTo(cx, h * 0.220f, cx + w * 0.150f, h * 0.250f);
        bangs.lineTo(cx + w * 0.150f, h * 0.200f);
        bangs.lineTo(cx - w * 0.150f, h * 0.200f);
        bangs.close();

        canvas.drawPath(bangs, paint);
    }

    // ========================================
    // 2 — Каре (исправлено: чёлка короче)
    // ========================================

    /**
     * Каре — короткая женская стрижка длиной до подбородка.
     * Прямые волосы, ровный срез чуть ниже подбородка.
     */
    private void drawCareHairBack(Canvas c, float cx, float h, float w) {
        // Объёмная "шапка" вокруг головы с ровным срезом по бокам шеи
        Path p = new Path();

        // Верхняя округлая часть (макушка)
        p.moveTo(cx - w * 0.195f, h * 0.50f);
        p.quadTo(cx - w * 0.215f, h * 0.16f, cx, h * 0.150f);
        p.quadTo(cx + w * 0.215f, h * 0.16f, cx + w * 0.195f, h * 0.50f);

        // Ровный срез внизу (характерная черта каре)
        p.lineTo(cx + w * 0.195f, h * 0.555f);
        p.lineTo(cx - w * 0.195f, h * 0.555f);
        p.close();

        c.drawPath(p, paint);
    }

    private void drawCareHairFront(Canvas c, float cx, float h, float w) {

        // ЧЁЛКА — теперь выше и короче
        Path bangs = new Path();

        bangs.moveTo(cx - w * 0.155f, h * 0.165f);

        // верхняя линия
        bangs.quadTo(
                cx,
                h * 0.135f,
                cx + w * 0.155f,
                h * 0.165f
        );

        // правая сторона
        bangs.lineTo(cx + w * 0.155f, h * 0.238f);

        // нижний край — мягкий изгиб,
        // заканчивается ВЫШЕ бровей
        bangs.quadTo(
                cx,
                h * 0.250f,
                cx - w * 0.155f,
                h * 0.238f
        );

        bangs.close();

        c.drawPath(bangs, paint);

        // Боковые части каре
        Path leftSide = new Path();
        leftSide.moveTo(cx - w * 0.195f, h * 0.255f);
        leftSide.lineTo(cx - w * 0.150f, h * 0.255f);
        leftSide.lineTo(cx - w * 0.150f, h * 0.555f);
        leftSide.lineTo(cx - w * 0.195f, h * 0.555f);
        leftSide.close();
        c.drawPath(leftSide, paint);

        Path rightSide = new Path();
        rightSide.moveTo(cx + w * 0.195f, h * 0.255f);
        rightSide.lineTo(cx + w * 0.150f, h * 0.255f);
        rightSide.lineTo(cx + w * 0.150f, h * 0.555f);
        rightSide.lineTo(cx + w * 0.195f, h * 0.555f);
        rightSide.close();
        c.drawPath(rightSide, paint);
    }

    // ========================================
    // 3 — Короткий ёжик / Crew Cut (мужская)
    // ========================================
    private void drawCrewCutBack(Canvas canvas, float cx, float h, float w) { // Индекс 3
        // Очень короткая стрижка, сзади почти не видна масса, только легкий контур
        paint.setColor(config.hairColor);
        RectF back = new RectF(cx - w * 0.145f, h * 0.180f, cx + w * 0.145f, h * 0.350f);
        canvas.drawRoundRect(back, w * 0.05f, w * 0.05f, paint);
    }

    // Индекс 3 - Спортивная (Crew Cut)
    private void drawCrewCutFront(Canvas canvas, float cx, float h, float w) {
        paint.setColor(config.hairColor);
        // Основная площадка сверху
        RectF top = new RectF(cx - w * 0.140f, h * 0.160f, cx + w * 0.140f, h * 0.260f);
        canvas.drawRoundRect(top, w * 0.04f, w * 0.04f, paint);

        // Короткие виски
        canvas.drawRect(cx - w * 0.140f, h * 0.250f, cx - w * 0.110f, h * 0.320f, paint);
        canvas.drawRect(cx + w * 0.110f, h * 0.250f, cx + w * 0.140f, h * 0.320f, paint);
    }

    // ========================================
    // 4 — Гладкая укладка с боковым пробором (мужская)
    // ========================================
    private void drawSidePartManBack(Canvas canvas, float cx, float h, float w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(config.hairColor);
        // Объем за головой
        RectF back = new RectF(
                cx - w * 0.160f,
                h * 0.150f,
                cx + w * 0.160f,
                h * 0.400f
        );
        canvas.drawRoundRect(back, w * 0.09f, w * 0.09f, paint);
    }

    // Индекс 4 - Классика с пробором (Side Part)
    private void drawSidePartManFront(Canvas canvas, float cx, float h, float w) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(config.hairColor);

        // 1. Основная масса волос (закрывает лоб без пропусков)
        RectF hairBase = new RectF(
                cx - w * 0.155f,
                h * 0.145f,
                cx + w * 0.155f,
                h * 0.280f
        );
        canvas.drawRoundRect(hairBase, w * 0.06f, w * 0.06f, paint);

        // 2. Отрисовка висков (уши остаются открытыми)
        canvas.drawRect(cx - w * 0.155f, h * 0.250f, cx - w * 0.120f, h * 0.340f, paint);
        canvas.drawRect(cx + w * 0.120f, h * 0.250f, cx + w * 0.155f, h * 0.340f, paint);

        // 3. Стилизованная челка с пробором (рисуем поверх базы)
        Path sidePart = new Path();
        // Начинаем от левого края
        sidePart.moveTo(cx - w * 0.155f, h * 0.260f);
        // Поднимаемся к пробору (смещен влево)
        sidePart.lineTo(cx - w * 0.040f, h * 0.190f);
        // Идем к правому краю, создавая объемную волну
        sidePart.quadTo(cx + w * 0.050f, h * 0.140f, cx + w * 0.155f, h * 0.200f);
        sidePart.lineTo(cx + w * 0.155f, h * 0.280f);
        // Линия над глазами
        sidePart.quadTo(cx, h * 0.240f, cx - w * 0.155f, h * 0.280f);
        sidePart.close();

        canvas.drawPath(sidePart, paint);

        // 4. Акцентная линия пробора (чуть темнее для глубины)
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(adjustBrightness(config.hairColor, 0.85f));
        paint.setStrokeWidth(w * 0.008f);

        canvas.drawLine(cx - w * 0.040f, h * 0.160f, cx - w * 0.040f, h * 0.210f, paint);

        paint.setStyle(Paint.Style.FILL); // Сбрасываем стиль
    }

    // ========================================
    // 5 — Короткие кудряшки (мужская)
    // ========================================
    private void drawCurlyShortManBack(Canvas canvas, float cx, float h, float w) { // Индекс 5
        paint.setColor(config.hairColor);
        // Для кудрей делаем заднюю часть чуть шире и "пушистее"
        RectF back = new RectF(cx - w * 0.165f, h * 0.140f, cx + w * 0.165f, h * 0.380f);
        canvas.drawRoundRect(back, w * 0.12f, w * 0.12f, paint);
    }

    // Индекс 5 - Кудрявая (Curly Short)
    private void drawCurlyShortManFront(Canvas canvas, float cx, float h, float w) {
        paint.setColor(config.hairColor);
        // Рисуем несколько кругов разного размера для эффекта кудряшек
        float radius = w * 0.06f;
        canvas.drawCircle(cx - w * 0.080f, h * 0.180f, radius, paint);
        canvas.drawCircle(cx + w * 0.080f, h * 0.180f, radius, paint);
        canvas.drawCircle(cx, h * 0.160f, radius * 1.1f, paint);
        canvas.drawCircle(cx - w * 0.120f, h * 0.230f, radius * 0.9f, paint);
        canvas.drawCircle(cx + w * 0.120f, h * 0.230f, radius * 0.9f, paint);

        // Заполняем центр, чтобы не было дырок
        RectF fill = new RectF(cx - w * 0.120f, h * 0.180f, cx + w * 0.120f, h * 0.280f);
        canvas.drawRect(fill, paint);
    }

    // === ЖЕНСКИЕ ПРИЧЁСКИ (10-34) ===

    private void drawBobHair(Canvas c, float cx, float h, float w) {
        RectF hair = new RectF(cx - w * 0.18f, h * 0.16f, cx + w * 0.18f, h * 0.72f);
        c.drawRoundRect(hair, w * 0.07f, w * 0.07f, paint);
    }

    private void drawBobFront(Canvas c, float cx, float h, float w) {
        RectF fringe = new RectF(cx - w * 0.13f, h * 0.19f, cx + w * 0.13f, h * 0.265f);
        c.drawRoundRect(fringe, w * 0.02f, w * 0.02f, paint);
    }

    private void drawMediumWavesHair(Canvas c, float cx, float h, float w) {
        Path p = new Path();
        p.moveTo(cx - w * 0.19f, h * 0.18f);
        p.quadTo(cx - w * 0.27f, h * 0.44f, cx - w * 0.19f, h * 0.76f);
        p.lineTo(cx + w * 0.19f, h * 0.76f);
        p.quadTo(cx + w * 0.27f, h * 0.44f, cx + w * 0.19f, h * 0.18f);
        p.close();
        c.drawPath(p, paint);
    }

    private void drawMediumWavesFront(Canvas c, float cx, float h, float w) {
        c.drawCircle(cx - w * 0.08f, h * 0.22f, w * 0.040f, paint);
        c.drawCircle(cx, h * 0.21f, w * 0.045f, paint);
        c.drawCircle(cx + w * 0.08f, h * 0.22f, w * 0.040f, paint);
    }

    private void drawRoundedWavesHair(Canvas c, float cx, float h, float w) {
        RectF hair = new RectF(cx - w * 0.19f, h * 0.16f, cx + w * 0.19f, h * 0.76f);
        c.drawRoundRect(hair, w * 0.12f, w * 0.12f, paint);
    }

    private void drawRoundedWavesFront(Canvas c, float cx, float h, float w) {

        // Боковые мягкие волны
        c.drawCircle(cx - w * 0.085f, h * 0.225f, w * 0.043f, paint);
        c.drawCircle(cx + w * 0.085f, h * 0.225f, w * 0.043f, paint);

        // СГЛАЖЕННАЯ центральная чёлка
        Path bangs = new Path();

        bangs.moveTo(cx - w * 0.120f, h * 0.205f);

        // верх
        bangs.quadTo(
                cx,
                h * 0.160f,
                cx + w * 0.120f,
                h * 0.205f
        );

        // правая сторона
        bangs.quadTo(
                cx + w * 0.115f,
                h * 0.255f,
                cx + w * 0.090f,
                h * 0.270f
        );

        // нижняя мягкая линия
        bangs.quadTo(
                cx,
                h * 0.290f,
                cx - w * 0.090f,
                h * 0.270f
        );

        // левая сторона
        bangs.quadTo(
                cx - w * 0.115f,
                h * 0.255f,
                cx - w * 0.120f,
                h * 0.205f
        );

        bangs.close();

        c.drawPath(bangs, paint);
    }

    private void drawLongCurtainHair(Canvas c, float cx, float h, float w) {
        Path p = new Path();
        p.moveTo(cx - w * 0.19f, h * 0.16f);
        p.lineTo(cx - w * 0.24f, h * 0.82f);
        p.lineTo(cx + w * 0.24f, h * 0.82f);
        p.lineTo(cx + w * 0.19f, h * 0.16f);
        p.close();
        c.drawPath(p, paint);
    }

    private void drawLongCurtainFront(Canvas c, float cx, float h, float w) {
        Path left = new Path();
        left.moveTo(cx - w * 0.16f, h * 0.21f);
        left.quadTo(cx - w * 0.09f, h * 0.14f, cx - w * 0.01f, h * 0.21f);
        left.lineTo(cx - w * 0.01f, h * 0.29f);
        left.lineTo(cx - w * 0.16f, h * 0.29f);
        left.close();
        c.drawPath(left, paint);

        Path right = new Path();
        right.moveTo(cx + w * 0.16f, h * 0.21f);
        right.quadTo(cx + w * 0.09f, h * 0.14f, cx + w * 0.01f, h * 0.21f);
        right.lineTo(cx + w * 0.01f, h * 0.29f);
        right.lineTo(cx + w * 0.16f, h * 0.29f);
        right.close();
        c.drawPath(right, paint);
    }

    private void drawBigLongWavesHair(Canvas c, float cx, float h, float w) {
        Path p = new Path();
        p.moveTo(cx - w * 0.20f, h * 0.16f);
        p.quadTo(cx - w * 0.30f, h * 0.45f, cx - w * 0.20f, h * 0.86f);
        p.lineTo(cx + w * 0.20f, h * 0.86f);
        p.quadTo(cx + w * 0.30f, h * 0.45f, cx + w * 0.20f, h * 0.16f);
        p.close();
        c.drawPath(p, paint);
    }

    private void drawBigLongWavesFront(Canvas c, float cx, float h, float w) {
        c.drawCircle(cx - w * 0.09f, h * 0.22f, w * 0.045f, paint);
        c.drawCircle(cx + w * 0.09f, h * 0.22f, w * 0.045f, paint);
        RectF top = new RectF(cx - w * 0.10f, h * 0.18f, cx + w * 0.10f, h * 0.265f);
        c.drawRoundRect(top, w * 0.04f, w * 0.04f, paint);
    }

    private void drawAsymmetricLongHair(Canvas c, float cx, float h, float w) {
        Path p = new Path();
        p.moveTo(cx - w * 0.16f, h * 0.16f);
        p.quadTo(cx - w * 0.30f, h * 0.52f, cx - w * 0.22f, h * 0.86f);
        p.lineTo(cx + w * 0.17f, h * 0.86f);
        p.quadTo(cx + w * 0.24f, h * 0.52f, cx + w * 0.17f, h * 0.16f);
        p.close();
        c.drawPath(p, paint);
    }

    private void drawAsymmetricLongFront(Canvas c, float cx, float h, float w) {
        Path p = new Path();
        p.moveTo(cx - w * 0.02f, h * 0.17f);
        p.lineTo(cx + w * 0.13f, h * 0.19f);
        p.lineTo(cx + w * 0.05f, h * 0.31f);
        p.lineTo(cx - w * 0.09f, h * 0.28f);
        p.close();
        c.drawPath(p, paint);
    }

    private void drawMessyBunHair(Canvas c, float cx, float h, float w) {
        RectF back = new RectF(cx - w * 0.17f, h * 0.16f, cx + w * 0.17f, h * 0.70f);
        c.drawRoundRect(back, w * 0.09f, w * 0.09f, paint);
        c.drawCircle(cx + w * 0.02f, h * 0.11f, w * 0.050f, paint);
    }

    private void drawMessyBunFront(Canvas c, float cx, float h, float w) {
        Path p = new Path();
        p.moveTo(cx - w * 0.14f, h * 0.22f);
        p.quadTo(cx - w * 0.08f, h * 0.14f, cx - w * 0.01f, h * 0.19f);
        p.quadTo(cx + w * 0.07f, h * 0.14f, cx + w * 0.14f, h * 0.22f);
        p.lineTo(cx + w * 0.14f, h * 0.275f);
        p.lineTo(cx - w * 0.14f, h * 0.275f);
        p.close();
        c.drawPath(p, paint);
    }

    private void drawHighBunHair(Canvas c, float cx, float h, float w) {
        RectF back = new RectF(cx - w * 0.17f, h * 0.16f, cx + w * 0.17f, h * 0.70f);
        c.drawRoundRect(back, w * 0.09f, w * 0.09f, paint);
        c.drawCircle(cx, h * 0.10f, w * 0.050f, paint);
    }

    private void drawHighBunFront(Canvas c, float cx, float h, float w) {
        RectF top = new RectF(cx - w * 0.13f, h * 0.18f, cx + w * 0.13f, h * 0.255f);
        c.drawRoundRect(top, w * 0.04f, w * 0.04f, paint);
    }

    private void drawTwinPonyHair(Canvas c, float cx, float h, float w) {
        RectF center = new RectF(cx - w * 0.13f, h * 0.16f, cx + w * 0.13f, h * 0.38f);
        c.drawRoundRect(center, w * 0.06f, w * 0.06f, paint);

        RectF leftTail = new RectF(cx - w * 0.24f, h * 0.28f, cx - w * 0.10f, h * 0.84f);
        RectF rightTail = new RectF(cx + w * 0.10f, h * 0.28f, cx + w * 0.24f, h * 0.84f);

        c.drawRoundRect(leftTail, w * 0.07f, w * 0.07f, paint);
        c.drawRoundRect(rightTail, w * 0.07f, w * 0.07f, paint);
    }

    private void drawTwinPonyFront(Canvas c, float cx, float h, float w) {
        c.drawCircle(cx - w * 0.10f, h * 0.19f, w * 0.040f, paint);
        c.drawCircle(cx + w * 0.10f, h * 0.19f, w * 0.040f, paint);
        RectF top = new RectF(cx - w * 0.12f, h * 0.18f, cx + w * 0.12f, h * 0.25f);
        c.drawRoundRect(top, w * 0.04f, w * 0.04f, paint);
    }

    private void drawBraidsHairClassic(Canvas c, float cx, float h, float w) {
        RectF top = new RectF(cx - w * 0.17f, h * 0.16f, cx + w * 0.17f, h * 0.37f);
        c.drawRoundRect(top, w * 0.07f, w * 0.07f, paint);

        drawBraid(c, cx - w * 0.145f, h * 0.34f, h * 0.85f, w * 0.034f);
        drawBraid(c, cx + w * 0.145f, h * 0.34f, h * 0.85f, w * 0.034f);
    }

    private void drawBraidsFrontClassic(Canvas c, float cx, float h, float w) {
        RectF fringe = new RectF(cx - w * 0.14f, h * 0.19f, cx + w * 0.14f, h * 0.265f);
        c.drawRoundRect(fringe, w * 0.04f, w * 0.04f, paint);
    }

    private void drawBraidsHairThin(Canvas c, float cx, float h, float w) {
        RectF top = new RectF(cx - w * 0.16f, h * 0.16f, cx + w * 0.16f, h * 0.36f);
        c.drawRoundRect(top, w * 0.06f, w * 0.06f, paint);

        drawThinBraid(c, cx - w * 0.135f, h * 0.33f, h * 0.83f, w * 0.022f);
        drawThinBraid(c, cx + w * 0.135f, h * 0.33f, h * 0.83f, w * 0.022f);
    }

    private void drawBraidsFrontThin(Canvas c, float cx, float h, float w) {
        for (int i = -3; i <= 3; i++) {
            c.drawCircle(cx + i * w * 0.03f, h * 0.21f, w * 0.018f, paint);
        }
    }

    private void drawBraidsHairPuffy(Canvas c, float cx, float h, float w) {
        RectF top = new RectF(cx - w * 0.18f, h * 0.16f, cx + w * 0.18f, h * 0.40f);
        c.drawRoundRect(top, w * 0.10f, w * 0.10f, paint);

        drawPuffyBraid(c, cx - w * 0.15f, h * 0.34f, h * 0.82f, w * 0.05f);
        drawPuffyBraid(c, cx + w * 0.15f, h * 0.34f, h * 0.82f, w * 0.05f);
    }

    private void drawBraidsFrontPuffy(Canvas c, float cx, float h, float w) {
        c.drawCircle(cx - w * 0.09f, h * 0.22f, w * 0.045f, paint);
        c.drawCircle(cx + w * 0.09f, h * 0.22f, w * 0.045f, paint);
        RectF top = new RectF(cx - w * 0.11f, h * 0.18f, cx + w * 0.11f, h * 0.255f);
        c.drawRoundRect(top, w * 0.04f, w * 0.04f, paint);
    }

    private void drawBraidsHairDoubleBuns(Canvas c, float cx, float h, float w) {
        RectF top = new RectF(cx - w * 0.14f, h * 0.17f, cx + w * 0.14f, h * 0.39f);
        c.drawRoundRect(top, w * 0.07f, w * 0.07f, paint);

        c.drawCircle(cx - w * 0.11f, h * 0.11f, w * 0.045f, paint);
        c.drawCircle(cx + w * 0.11f, h * 0.11f, w * 0.045f, paint);

        drawThinBraid(c, cx - w * 0.12f, h * 0.34f, h * 0.77f, w * 0.022f);
        drawThinBraid(c, cx + w * 0.12f, h * 0.34f, h * 0.77f, w * 0.022f);

        int old = paint.getColor();
        paint.setColor(Color.parseColor("#D5A628"));
        c.drawCircle(cx - w * 0.11f, h * 0.16f, w * 0.010f, paint);
        c.drawCircle(cx + w * 0.11f, h * 0.16f, w * 0.010f, paint);
        paint.setColor(old);
    }

    private void drawBraidsFrontDoubleBuns(Canvas c, float cx, float h, float w) {
        for (int i = -2; i <= 2; i++) {
            c.drawCircle(cx + i * w * 0.03f, h * 0.215f, w * 0.018f, paint);
        }
    }

    private void drawCurlyHairSoft(Canvas c, float cx, float h, float w) {
        RectF center = new RectF(cx - w * 0.19f, h * 0.17f, cx + w * 0.19f, h * 0.76f);
        c.drawRoundRect(center, w * 0.11f, w * 0.11f, paint);
        addCurlCloud(c, cx - w * 0.18f, h * 0.42f, w * 0.055f);
        addCurlCloud(c, cx + w * 0.18f, h * 0.42f, w * 0.055f);
    }

    private void drawCurlyFrontSoft(Canvas c, float cx, float h, float w) {
        c.drawCircle(cx - w * 0.08f, h * 0.22f, w * 0.040f, paint);
        c.drawCircle(cx, h * 0.21f, w * 0.042f, paint);
        c.drawCircle(cx + w * 0.08f, h * 0.22f, w * 0.040f, paint);
    }

    private void drawCurlyHairBig(Canvas c, float cx, float h, float w) {
        RectF center = new RectF(cx - w * 0.21f, h * 0.16f, cx + w * 0.21f, h * 0.82f);
        c.drawRoundRect(center, w * 0.13f, w * 0.13f, paint);

        for (int i = -2; i <= 2; i++) {
            c.drawCircle(cx + i * w * 0.08f, h * 0.19f, w * 0.05f, paint);
        }
    }

    private void drawCurlyFrontBig(Canvas c, float cx, float h, float w) {
        c.drawCircle(cx - w * 0.11f, h * 0.22f, w * 0.045f, paint);
        c.drawCircle(cx, h * 0.20f, w * 0.05f, paint);
        c.drawCircle(cx + w * 0.11f, h * 0.22f, w * 0.045f, paint);
    }

    private void drawCurlyHairLong(Canvas c, float cx, float h, float w) {
        RectF center = new RectF(cx - w * 0.19f, h * 0.17f, cx + w * 0.19f, h * 0.88f);
        c.drawRoundRect(center, w * 0.12f, w * 0.12f, paint);
        drawVerticalCurls(c, cx - w * 0.17f, h * 0.34f, h * 0.86f, w * 0.038f);
        drawVerticalCurls(c, cx + w * 0.17f, h * 0.34f, h * 0.86f, w * 0.038f);
    }

    private void drawCurlyFrontLong(Canvas c, float cx, float h, float w) {
        for (int i = -3; i <= 3; i++) {
            c.drawCircle(cx + i * w * 0.035f, h * 0.21f, w * 0.022f, paint);
        }
    }

    private void drawPonyHairHigh(Canvas c, float cx, float h, float w) {
        RectF main = new RectF(cx - w * 0.15f, h * 0.16f, cx + w * 0.15f, h * 0.40f);
        c.drawRoundRect(main, w * 0.08f, w * 0.08f, paint);

        Path tail = new Path();
        tail.moveTo(cx + w * 0.07f, h * 0.16f);
        tail.quadTo(cx + w * 0.22f, h * 0.25f, cx + w * 0.18f, h * 0.65f);
        tail.lineTo(cx + w * 0.10f, h * 0.64f);
        tail.quadTo(cx + w * 0.13f, h * 0.36f, cx + w * 0.01f, h * 0.16f);
        tail.close();
        c.drawPath(tail, paint);
    }

    private void drawPonyFrontHigh(Canvas c, float cx, float h, float w) {
        Path p = new Path();
        p.moveTo(cx - w * 0.12f, h * 0.21f);
        p.quadTo(cx - w * 0.03f, h * 0.15f, cx + w * 0.10f, h * 0.21f);
        p.lineTo(cx + w * 0.12f, h * 0.275f);
        p.lineTo(cx - w * 0.12f, h * 0.275f);
        p.close();
        c.drawPath(p, paint);
    }

    private void drawPonyHairLow(Canvas c, float cx, float h, float w) {
        RectF main = new RectF(cx - w * 0.17f, h * 0.16f, cx + w * 0.17f, h * 0.74f);
        c.drawRoundRect(main, w * 0.10f, w * 0.10f, paint);

        RectF tail = new RectF(cx - w * 0.05f, h * 0.54f, cx + w * 0.05f, h * 0.90f);
        c.drawRoundRect(tail, w * 0.04f, w * 0.04f, paint);
    }

    private void drawPonyFrontLow(Canvas c, float cx, float h, float w) {
        RectF fringe = new RectF(cx - w * 0.12f, h * 0.19f, cx + w * 0.12f, h * 0.275f);
        c.drawRoundRect(fringe, w * 0.04f, w * 0.04f, paint);
    }

    private void drawPonyHairSide(Canvas c, float cx, float h, float w) {
        RectF main = new RectF(cx - w * 0.17f, h * 0.16f, cx + w * 0.17f, h * 0.70f);
        c.drawRoundRect(main, w * 0.10f, w * 0.10f, paint);

        Path tail = new Path();
        tail.moveTo(cx + w * 0.10f, h * 0.40f);
        tail.quadTo(cx + w * 0.27f, h * 0.46f, cx + w * 0.20f, h * 0.83f);
        tail.lineTo(cx + w * 0.12f, h * 0.82f);
        tail.quadTo(cx + w * 0.16f, h * 0.58f, cx + w * 0.04f, h * 0.42f);
        tail.close();
        c.drawPath(tail, paint);
    }

    private void drawPonyFrontSide(Canvas c, float cx, float h, float w) {
        Path p = new Path();
        p.moveTo(cx - w * 0.12f, h * 0.21f);
        p.quadTo(cx, h * 0.15f, cx + w * 0.11f, h * 0.21f);
        p.lineTo(cx + w * 0.12f, h * 0.275f);
        p.lineTo(cx - w * 0.12f, h * 0.275f);
        p.close();
        c.drawPath(p, paint);
    }

    private void drawBunHairClassic(Canvas c, float cx, float h, float w) {
        RectF main = new RectF(cx - w * 0.17f, h * 0.16f, cx + w * 0.17f, h * 0.68f);
        c.drawRoundRect(main, w * 0.09f, w * 0.09f, paint);
        c.drawCircle(cx, h * 0.10f, w * 0.055f, paint);
    }

    private void drawBunFrontClassic(Canvas c, float cx, float h, float w) {
        RectF top = new RectF(cx - w * 0.12f, h * 0.19f, cx + w * 0.12f, h * 0.265f);
        c.drawRoundRect(top, w * 0.04f, w * 0.04f, paint);
    }

    private void drawBunHairCurly(Canvas c, float cx, float h, float w) {
        RectF main = new RectF(cx - w * 0.17f, h * 0.16f, cx + w * 0.17f, h * 0.68f);
        c.drawRoundRect(main, w * 0.09f, w * 0.09f, paint);
        c.drawCircle(cx, h * 0.10f, w * 0.05f, paint);
        c.drawCircle(cx - w * 0.04f, h * 0.12f, w * 0.03f, paint);
        c.drawCircle(cx + w * 0.04f, h * 0.12f, w * 0.03f, paint);
    }

    private void drawBunFrontCurly(Canvas c, float cx, float h, float w) {
        c.drawCircle(cx - w * 0.07f, h * 0.215f, w * 0.032f, paint);
        c.drawCircle(cx, h * 0.20f, w * 0.036f, paint);
        c.drawCircle(cx + w * 0.07f, h * 0.215f, w * 0.032f, paint);
    }

    private void drawBobHairShortRounded(Canvas c, float cx, float h, float w) {
        RectF main = new RectF(cx - w * 0.18f, h * 0.16f, cx + w * 0.18f, h * 0.62f);
        c.drawRoundRect(main, w * 0.08f, w * 0.08f, paint);
    }

    private void drawBobFrontShortRounded(Canvas c, float cx, float h, float w) {
        RectF fringe = new RectF(cx - w * 0.11f, h * 0.19f, cx + w * 0.11f, h * 0.27f);
        c.drawRoundRect(fringe, w * 0.03f, w * 0.03f, paint);
    }

    private void drawBobHairFrench(Canvas c, float cx, float h, float w) {
        RectF main = new RectF(cx - w * 0.18f, h * 0.16f, cx + w * 0.18f, h * 0.66f);
        c.drawRoundRect(main, w * 0.09f, w * 0.09f, paint);
    }

    private void drawBobFrontFrench(Canvas c, float cx, float h, float w) {
        RectF fringe = new RectF(cx - w * 0.13f, h * 0.19f, cx + w * 0.13f, h * 0.285f);
        c.drawRoundRect(fringe, w * 0.015f, w * 0.015f, paint);
    }

    private void drawWavyHairLongRibbon(Canvas c, float cx, float h, float w) {
        RectF main = new RectF(cx - w * 0.19f, h * 0.16f, cx + w * 0.19f, h * 0.86f);
        c.drawRoundRect(main, w * 0.11f, w * 0.11f, paint);
        addRibbon(c, cx - w * 0.12f, h * 0.22f, w * 0.018f);
        addRibbon(c, cx + w * 0.12f, h * 0.22f, w * 0.018f);
    }

    private void drawWavyFrontLongRibbon(Canvas c, float cx, float h, float w) {
        c.drawCircle(cx - w * 0.08f, h * 0.22f, w * 0.04f, paint);
        c.drawCircle(cx + w * 0.08f, h * 0.22f, w * 0.04f, paint);
        RectF top = new RectF(cx - w * 0.10f, h * 0.19f, cx + w * 0.10f, h * 0.265f);
        c.drawRoundRect(top, w * 0.04f, w * 0.04f, paint);
    }

    private void drawWavyHairLongSoft(Canvas c, float cx, float h, float w) {
        Path p = new Path();
        p.moveTo(cx - w * 0.20f, h * 0.16f);
        p.quadTo(cx - w * 0.28f, h * 0.42f, cx - w * 0.20f, h * 0.88f);
        p.lineTo(cx + w * 0.20f, h * 0.88f);
        p.quadTo(cx + w * 0.28f, h * 0.42f, cx + w * 0.20f, h * 0.16f);
        p.close();
        c.drawPath(p, paint);
    }

    private void drawWavyFrontLongSoft(Canvas c, float cx, float h, float w) {
        c.drawCircle(cx - w * 0.09f, h * 0.22f, w * 0.042f, paint);
        c.drawCircle(cx, h * 0.21f, w * 0.04f, paint);
        c.drawCircle(cx + w * 0.09f, h * 0.22f, w * 0.042f, paint);
    }

    // === Утилиты ===
    private void drawBraid(Canvas c, float x, float startY, float endY, float r) {
        float y = startY;
        boolean shift = false;
        while (y < endY) {
            c.drawCircle(x + (shift ? r * 0.45f : -r * 0.45f), y, r, paint);
            y += r * 1.45f;
            shift = !shift;
        }
    }

    private void drawThinBraid(Canvas c, float x, float startY, float endY, float r) {
        float y = startY;
        boolean shift = false;
        while (y < endY) {
            c.drawCircle(x + (shift ? r * 0.5f : -r * 0.5f), y, r, paint);
            y += r * 1.5f;
            shift = !shift;
        }
    }

    private void drawPuffyBraid(Canvas c, float x, float startY, float endY, float r) {
        float y = startY;
        while (y < endY) {
            c.drawCircle(x, y, r, paint);
            y += r * 1.2f;
        }
    }

    private void addCurlCloud(Canvas c, float x, float y, float r) {
        c.drawCircle(x, y, r, paint);
        c.drawCircle(x - r * 0.7f, y + r * 0.5f, r * 0.8f, paint);
        c.drawCircle(x + r * 0.7f, y + r * 0.5f, r * 0.8f, paint);
    }

    private void drawVerticalCurls(Canvas c, float x, float startY, float endY, float r) {
        float y = startY;
        while (y < endY) {
            c.drawCircle(x, y, r, paint);
            y += r * 1.45f;
        }
    }

    private void addRibbon(Canvas c, float x, float y, float r) {
        int old = paint.getColor();
        paint.setColor(Color.parseColor("#D5A628"));
        c.drawCircle(x, y, r, paint);
        paint.setColor(old);
    }

    private int adjustBrightness(int color, float factor) {
        int r = Math.min(255, Math.max(0, (int) (Color.red(color) * factor)));
        int g = Math.min(255, Math.max(0, (int) (Color.green(color) * factor)));
        int b = Math.min(255, Math.max(0, (int) (Color.blue(color) * factor)));
        return Color.rgb(r, g, b);
    }

    public void setShowBackground(boolean showBackground) {
        this.showBackground = showBackground;
        invalidate();
    }
}
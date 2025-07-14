package com.example.a22f3272smd_project;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

/**
 * Standalone FaceGraphic class in case you prefer to use it separately from the GraphicOverlay
 * This can be used as an alternative to the inner FaceGraphic class in GraphicOverlay
 */
public class FaceGraphic extends GraphicOverlay.Graphic {
    private static final float FACE_POSITION_RADIUS = 4.0f;
    private static final float ID_TEXT_SIZE = 30.0f;
    private static final float BOX_STROKE_WIDTH = 5.0f;

    private final Paint facePositionPaint;
    private final Paint landmarkPaint;
    private final Paint boxPaint;
    private final Paint idPaint;
    private final Paint smilePaint;
    private final Paint eyePaint;
    private final Face face;

    public FaceGraphic(GraphicOverlay overlay, Face face) {
        super(overlay);
        this.face = face;

        // Face outline
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(BOX_STROKE_WIDTH);

        // Face position dot
        facePositionPaint = new Paint();
        facePositionPaint.setColor(Color.RED);
        facePositionPaint.setStyle(Paint.Style.FILL);

        // Facial landmarks
        landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.BLUE);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setStrokeWidth(5f);

        // Text for smile probability
        idPaint = new Paint();
        idPaint.setColor(Color.WHITE);
        idPaint.setTextSize(ID_TEXT_SIZE);

        // Smile indicator
        smilePaint = new Paint();
        smilePaint.setColor(Color.YELLOW);
        smilePaint.setStyle(Paint.Style.STROKE);
        smilePaint.setStrokeWidth(10f);

        // Eye open/closed indicator
        eyePaint = new Paint();
        eyePaint.setColor(Color.CYAN);
        eyePaint.setStyle(Paint.Style.STROKE);
        eyePaint.setStrokeWidth(5f);
    }

    @Override
    public void draw(Canvas canvas) {
        if (face == null) return;

        // Draw face bounding box
        RectF bounds = new RectF(face.getBoundingBox());
        float left = translateX(bounds.left);
        float top = translateY(bounds.top);
        float right = translateX(bounds.right);
        float bottom = translateY(bounds.bottom);

        canvas.drawRect(left, top, right, bottom, boxPaint);

        // Get face position
        float centerX = (left + right) / 2;
        float centerY = (top + bottom) / 2;

        // Draw central point of face
        canvas.drawCircle(centerX, centerY, FACE_POSITION_RADIUS, facePositionPaint);

        // Draw facial landmarks
        drawLandmark(canvas, FaceLandmark.LEFT_EYE);
        drawLandmark(canvas, FaceLandmark.RIGHT_EYE);
        drawLandmark(canvas, FaceLandmark.LEFT_EAR);
        drawLandmark(canvas, FaceLandmark.RIGHT_EAR);
        drawLandmark(canvas, FaceLandmark.LEFT_CHEEK);
        drawLandmark(canvas, FaceLandmark.RIGHT_CHEEK);
        drawLandmark(canvas, FaceLandmark.NOSE_BASE);
        drawLandmark(canvas, FaceLandmark.MOUTH_LEFT);
        drawLandmark(canvas, FaceLandmark.MOUTH_RIGHT);
        drawLandmark(canvas, FaceLandmark.MOUTH_BOTTOM);

        // Get eyes positions for drawing eye openness
        FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);

        // Draw smile probability
        if (face.getSmilingProbability() != null) {
            float smileProb = face.getSmilingProbability();
            String smileText = String.format("Smile: %.2f", smileProb);
            canvas.drawText(smileText, left, bottom + 40, idPaint);

            // Draw mouth based on smile probability
            float mouthWidth = (right - left) * 0.7f;
            float mouthHeight = mouthWidth * 0.4f * smileProb;

            // Find mouth center based on landmarks if available
            float mouthCenterX = centerX;
            float mouthCenterY = bottom - (bottom - top) * 0.3f; // Default position

            FaceLandmark mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT);
            FaceLandmark mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT);
            FaceLandmark mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM);

            if (mouthLeft != null && mouthRight != null) {
                mouthCenterX = (translateX(mouthLeft.getPosition().x) + translateX(mouthRight.getPosition().x)) / 2;

                if (mouthBottom != null) {
                    mouthCenterY = (translateY(mouthLeft.getPosition().y) + translateY(mouthRight.getPosition().y) +
                            translateY(mouthBottom.getPosition().y)) / 3;
                } else {
                    mouthCenterY = (translateY(mouthLeft.getPosition().y) + translateY(mouthRight.getPosition().y)) / 2;
                }
            }

            // Draw a curved line representing the smile - curvature based on smile probability
            canvas.drawArc(
                    mouthCenterX - mouthWidth/2,
                    mouthCenterY - mouthHeight/2,
                    mouthCenterX + mouthWidth/2,
                    mouthCenterY + mouthHeight/2,
                    0, 180, false, smilePaint);
        }

        // Draw eye open probability
        if (face.getLeftEyeOpenProbability() != null && leftEye != null) {
            float leftEyeOpenProb = face.getLeftEyeOpenProbability();
            String leftEyeText = String.format("Left Eye: %.2f", leftEyeOpenProb);
            canvas.drawText(leftEyeText, left, bottom + 80, idPaint);

            // Draw left eye openness
            PointF leftEyePosition = leftEye.getPosition();
            float eyeX = translateX(leftEyePosition.x);
            float eyeY = translateY(leftEyePosition.y);
            float eyeSize = scaleX(15); // Base eye size
            float openness = Math.max(0.1f, leftEyeOpenProb); // Minimum to ensure visibility

            // Draw eye as ellipse with height based on openness
            RectF leftEyeRect = new RectF(
                    eyeX - eyeSize,
                    eyeY - eyeSize * openness,
                    eyeX + eyeSize,
                    eyeY + eyeSize * openness);
            canvas.drawOval(leftEyeRect, eyePaint);
        }

        if (face.getRightEyeOpenProbability() != null && rightEye != null) {
            float rightEyeOpenProb = face.getRightEyeOpenProbability();
            String rightEyeText = String.format("Right Eye: %.2f", rightEyeOpenProb);
            canvas.drawText(rightEyeText, left, bottom + 120, idPaint);

            // Draw right eye openness
            PointF rightEyePosition = rightEye.getPosition();
            float eyeX = translateX(rightEyePosition.x);
            float eyeY = translateY(rightEyePosition.y);
            float eyeSize = scaleX(15); // Base eye size
            float openness = Math.max(0.1f, rightEyeOpenProb); // Minimum to ensure visibility

            // Draw eye as ellipse with height based on openness
            RectF rightEyeRect = new RectF(
                    eyeX - eyeSize,
                    eyeY - eyeSize * openness,
                    eyeX + eyeSize,
                    eyeY + eyeSize * openness);
            canvas.drawOval(rightEyeRect, eyePaint);
        }
    }

    private void drawLandmark(Canvas canvas, int landmarkType) {
        FaceLandmark landmark = face.getLandmark(landmarkType);
        if (landmark != null) {
            PointF point = landmark.getPosition();
            canvas.drawCircle(
                    translateX(point.x),
                    translateY(point.y),
                    FACE_POSITION_RADIUS,
                    landmarkPaint);
        }
    }
}
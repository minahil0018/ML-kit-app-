package com.example.a22f3272smd_project;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class GraphicOverlay extends View {
    private final List<Graphic> graphics = new ArrayList<>();
    private List<Face> faces;
    private int imageWidth;
    private int imageHeight;
    private float scaleX;
    private float scaleY;
    private int cameraFacing = 1; // Default to front camera (LENS_FACING_FRONT)

    public void setCameraFacing(int facing) {
        this.cameraFacing = facing;
    }

    public abstract static class Graphic {
        private final GraphicOverlay overlay;

        public Graphic(GraphicOverlay overlay) {
            this.overlay = overlay;
        }

        public abstract void draw(Canvas canvas);

        protected float translateX(float x) {
            if (overlay.cameraFacing == 1) {
                // For front camera, flip the x-coordinate
                return overlay.getWidth() - (x * overlay.scaleX);
            } else {
                return x * overlay.scaleX;
            }
        }

        protected float translateY(float y) {
            return y * overlay.scaleY;
        }

        protected float scaleX(float horizontal) {
            return horizontal * overlay.scaleX;
        }

        protected float scaleY(float vertical) {
            return vertical * overlay.scaleY;
        }
    }

    public GraphicOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void clear() {
        graphics.clear();
        postInvalidate();
    }

    public void add(Graphic graphic) {
        graphics.add(graphic);
    }

    public void setFaces(List<Face> faces, int width, int height) {
        clear(); // Clear previous graphics
        this.faces = faces;
        this.imageWidth = width;
        this.imageHeight = height;

        // Calculate scale factors for converting image coordinates to view coordinates
        scaleX = (float) getWidth() / width;
        scaleY = (float) getHeight() / height;

        // Add a new graphic for each face
        for (Face face : faces) {
            add(new FaceGraphic(this, face));
        }

        invalidate(); // Trigger redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Graphic graphic : graphics) {
            graphic.draw(canvas);
        }
    }

    // Inner class to handle face graphic rendering
    private static class FaceGraphic extends Graphic {
        private static final float FACE_POSITION_RADIUS = 4.0f;
        private static final float ID_TEXT_SIZE = 30.0f;
        private static final float BOX_STROKE_WIDTH = 5.0f;

        private final Paint facePositionPaint;
        private final Paint landmarkPaint;
        private final Paint boxPaint;
        private final Paint idPaint;
        private final Paint smilePaint;
        private final Face face;

        FaceGraphic(GraphicOverlay overlay, Face face) {
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
        }

        @Override
        public void draw(Canvas canvas) {
            if (face == null) return;

            // Draw face bounding box
            Rect bounds = face.getBoundingBox();
            float left = translateX(bounds.left);
            float top = translateY(bounds.top);
            float right = translateX(bounds.right);
            float bottom = translateY(bounds.bottom);

            // For front camera, we've already flipped in translateX
            canvas.drawRect(left, top, right, bottom, boxPaint);

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

            // Draw smile probability
            if (face.getSmilingProbability() != null) {
                float smileProb = face.getSmilingProbability();
                String smileText = String.format("Smile: %.2f", smileProb);
                canvas.drawText(smileText, left, bottom + 40, idPaint);

                // Draw smile indicator - a curved line that gets wider based on smile probability
                float centerX = (left + right) / 2;
                float centerY = bottom - 30;
                float mouthWidth = (right - left) * 0.8f;
                float smileHeight = mouthWidth * 0.2f * smileProb;

                // Draw a curved line to represent the smile
                canvas.drawArc(
                        centerX - mouthWidth/2,
                        centerY - smileHeight/2,
                        centerX + mouthWidth/2,
                        centerY + smileHeight/2,
                        0, 180, false, smilePaint);
            }

            // Draw eye open probability
            if (face.getLeftEyeOpenProbability() != null) {
                float leftEyeOpenProb = face.getLeftEyeOpenProbability();
                String leftEyeText = String.format("Left Eye: %.2f", leftEyeOpenProb);
                canvas.drawText(leftEyeText, left, bottom + 80, idPaint);
            }

            if (face.getRightEyeOpenProbability() != null) {
                float rightEyeOpenProb = face.getRightEyeOpenProbability();
                String rightEyeText = String.format("Right Eye: %.2f", rightEyeOpenProb);
                canvas.drawText(rightEyeText, left, bottom + 120, idPaint);
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
}
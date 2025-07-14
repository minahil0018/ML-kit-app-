package com.example.a22f3272smd_project;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.google.mlkit.vision.text.Text;

import java.util.List;

public class TextOverlay extends View {

    private static final String TAG = "TextOverlay";

    private List<Text.Element> elements;
    private final Paint boxPaint;
    private final Paint textPaint;

    // Image dimensions and rotation
    private int imageWidth;
    private int imageHeight;
    private int rotation;

    // Scale factors for mapping coordinates
    private float scaleX;
    private float scaleY;
    private float translationX;
    private float translationY;

    public TextOverlay(Context context) {
        super(context);
        boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4.0f);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32f);
        textPaint.setStyle(Paint.Style.FILL);
        // Add shadow to make text more visible against various backgrounds
        textPaint.setShadowLayer(3.0f, 0f, 0f, Color.BLACK);
    }

    public TextOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4.0f);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setShadowLayer(3.0f, 0f, 0f, Color.BLACK);
    }

    /**
     * Sets the detected text elements and their properties for drawing
     *
     * @param elements List of text elements detected
     * @param imageWidth Width of the input image
     * @param imageHeight Height of the input image
     * @param rotation Rotation of the image (in degrees)
     */
    public void setElements(List<Text.Element> elements, int imageWidth, int imageHeight, int rotation) {
        this.elements = elements;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.rotation = rotation;

        // Calculate scaling factors when image dimensions change
        calculateTransformationMatrix();

        // Request redraw
        invalidate();
    }

    /**
     * Sets just the text elements (uses previous image dimensions if available)
     */
    public void setElements(List<Text.Element> elements) {
        this.elements = elements;

        // Only calculate scaling if we have valid image dimensions
        if (imageWidth > 0 && imageHeight > 0) {
            calculateTransformationMatrix();
        }

        invalidate(); // Request redraw
    }

    /**
     * Calculate the transformation matrix to map image coordinates to view coordinates
     */
    private void calculateTransformationMatrix() {
        int viewWidth = getWidth();
        int viewHeight = getHeight();

        if (viewWidth == 0 || viewHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            Log.w(TAG, "Cannot calculate scaling factors with zero dimensions");
            return;
        }

        // Handle different orientations
        boolean swapDimensions = (rotation == 90 || rotation == 270);
        int rotatedWidth = swapDimensions ? imageHeight : imageWidth;
        int rotatedHeight = swapDimensions ? imageWidth : imageHeight;

        // Calculate scale factors to fit the image in the view while maintaining aspect ratio
        float imageAspect = (float) rotatedWidth / rotatedHeight;
        float viewAspect = (float) viewWidth / viewHeight;

        if (imageAspect > viewAspect) {
            // Image is wider than view, scale to match width
            scaleX = (float) viewWidth / rotatedWidth;
            scaleY = scaleX; // Keep aspect ratio
            translationX = 0;
            translationY = (viewHeight - rotatedHeight * scaleY) / 2;
        } else {
            // Image is taller than view, scale to match height
            scaleY = (float) viewHeight / rotatedHeight;
            scaleX = scaleY; // Keep aspect ratio
            translationY = 0;
            translationX = (viewWidth - rotatedWidth * scaleX) / 2;
        }

        Log.d(TAG, String.format("Image: %dx%d, View: %dx%d, Scale: %.2f,%.2f, Translation: %.2f,%.2f",
                imageWidth, imageHeight, viewWidth, viewHeight, scaleX, scaleY, translationX, translationY));
    }

    /**
     * Transform an x-coordinate from image space to view space
     */
    private float mapX(float x) {
        // Handle rotation
        float rotatedX;
        switch (rotation) {
            case 90:
                rotatedX = imageHeight - x;  // Flip for 90 degrees
                break;
            case 180:
                rotatedX = imageWidth - x;  // Flip for 180 degrees
                break;
            case 270:
                rotatedX = x;  // Already correct for 270 degrees
                break;
            default:  // 0 degrees
                rotatedX = x;
                break;
        }

        // Apply scaling and translation
        return rotatedX * scaleX + translationX;
    }

    /**
     * Transform a y-coordinate from image space to view space
     */
    private float mapY(float y) {
        // Handle rotation
        float rotatedY;
        switch (rotation) {
            case 90:
                rotatedY = y;  // Already correct for 90 degrees
                break;
            case 180:
                rotatedY = imageHeight - y;  // Flip for 180 degrees
                break;
            case 270:
                rotatedY = imageWidth - y;  // Flip for 270 degrees
                break;
            default:  // 0 degrees
                rotatedY = y;
                break;
        }

        // Apply scaling and translation
        return rotatedY * scaleY + translationY;
    }

    /**
     * Maps a rectangle from image coordinates to view coordinates
     */
    private Rect mapRect(Rect imageRect) {
        if (imageRect == null) return null;

        return new Rect(
                (int) mapX(imageRect.left),
                (int) mapY(imageRect.top),
                (int) mapX(imageRect.right),
                (int) mapY(imageRect.bottom)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (elements == null || elements.isEmpty() || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        // If scale factors weren't calculated yet (like on first layout)
        if (scaleX == 0 || scaleY == 0) {
            calculateTransformationMatrix();
        }

        for (Text.Element element : elements) {
            Rect originalRect = element.getBoundingBox();
            if (originalRect != null) {
                // Map coordinates from image space to view space
                Rect viewRect = mapRect(originalRect);

                // Draw the bounding box
                canvas.drawRect(viewRect, boxPaint);

                // Draw the text inside/above the box
                String text = element.getText();
                canvas.drawText(text, viewRect.left, viewRect.top - 5, textPaint);
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Recalculate transformation when view size changes
        calculateTransformationMatrix();
    }
}
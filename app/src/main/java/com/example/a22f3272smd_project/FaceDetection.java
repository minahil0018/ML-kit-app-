package com.example.a22f3272smd_project;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceDetection extends AppCompatActivity {

    private static final String TAG = "FaceDetection";
    private PreviewView previewView;
    private GraphicOverlay overlay;
    private Button captureFrameButton;
    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;
    private boolean freeze = false;
    private int cameraFacing = CameraSelector.LENS_FACING_FRONT; // Default to front camera

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_detection);

        previewView = findViewById(R.id.preview_view);
        overlay = findViewById(R.id.graphic_overlay);
        captureFrameButton = findViewById(R.id.capture_frame);

        // Set camera facing in overlay - add this method to GraphicOverlay class
        if (overlay instanceof GraphicOverlay) {
            ((GraphicOverlay) overlay).setCameraFacing(cameraFacing);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Configure the face detector with all features enabled
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f) // Adjust if needed for better detection
                .enableTracking() // Enable face tracking for smoother updates
                .build();

        faceDetector = com.google.mlkit.vision.face.FaceDetection.getClient(options);

        // Setup freeze frame button
        captureFrameButton.setOnClickListener(v -> {
            freeze = !freeze;
            captureFrameButton.setText(freeze ? "Resume" : "Freeze Frame");

            if (freeze) {
                Toast.makeText(this, "Frame frozen", Toast.LENGTH_SHORT).show();
            }
        });

        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Set up the preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Camera selector based on current facing direction
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(cameraFacing)
                        .build();

                // Configure image analysis with latest-only strategy
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    if (!freeze) {
                        try {
                            @SuppressWarnings("UnsafeOptInUsageError")
                            InputImage inputImage = InputImage.fromMediaImage(
                                    image.getImage(), image.getImageInfo().getRotationDegrees());

                            faceDetector.process(inputImage)
                                    .addOnSuccessListener(faces -> {
                                        processFaceDetectionResults(faces, inputImage.getWidth(), inputImage.getHeight());
                                        image.close();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Face detection failed", e);
                                        image.close();
                                    });
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing image: " + e.getMessage());
                            image.close();
                        }
                    } else {
                        image.close();
                    }
                });

                // Unbind previous use cases before rebinding
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Process the detected faces and update the overlay
     */
    private void processFaceDetectionResults(List<Face> faces, int width, int height) {
        // Update the graphic overlay with detected faces
        if (overlay != null) {
            overlay.setFaces(faces, width, height);

            // Optional: Log information about detected faces
            if (faces.isEmpty()) {
                Log.d(TAG, "No faces detected");
            } else {
                Log.d(TAG, "Detected " + faces.size() + " faces");

                // Optional code to log details about the first face
                if (faces.size() > 0) {
                    Face face = faces.get(0);
                    if (face.getSmilingProbability() != null) {
                        Log.d(TAG, "Smiling probability: " + face.getSmilingProbability());
                    }
                }
            }
        }
    }

    /**
     * Toggle between front and back camera (if you want to add this feature)
     */
    private void toggleCamera() {
        cameraFacing = (cameraFacing == CameraSelector.LENS_FACING_FRONT) ?
                CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;

        // Update camera facing in overlay
        if (overlay instanceof GraphicOverlay) {
            ((GraphicOverlay) overlay).setCameraFacing(cameraFacing);
        }

        // Restart camera with new facing direction
        startCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (faceDetector != null) {
            faceDetector.close();
        }
    }
}
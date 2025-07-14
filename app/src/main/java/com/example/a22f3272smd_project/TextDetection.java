package com.example.a22f3272smd_project;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TextDetection extends AppCompatActivity {

    private static final String TAG = "TextDetection";
    private PreviewView previewView;
    private TextView resultText;
    private Button captureButton;
    private TextOverlay textOverlay;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_detection);

        previewView = findViewById(R.id.camera_preview);
        resultText = findViewById(R.id.text_output);
        captureButton = findViewById(R.id.capture_button);
        textOverlay = findViewById(R.id.overlay);

        cameraExecutor = Executors.newSingleThreadExecutor();
        checkCameraPermission();

        captureButton.setOnClickListener(v -> takePhoto());
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Log.e(TAG, "Camera permission denied");
            resultText.setText("Camera permission is required to use this feature.");
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is null");
            return;
        }

        // Show feedback that capture is in progress
        captureButton.setEnabled(false);
        resultText.setText("Processing image...");
        textOverlay.setElements(new ArrayList<>()); // Clear previous boxes

        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        processImage(imageProxy);
                        captureButton.setEnabled(true);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                        resultText.setText("Failed to capture image.");
                        captureButton.setEnabled(true);
                    }
                });
    }

    private void processImage(ImageProxy imageProxy) {
        try {
            @SuppressWarnings("UnsafeOptInUsageError")
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees());

            // Log image details for debugging
            Log.d(TAG, "Processing image: " + image.getWidth() + "x" + image.getHeight() +
                    ", rotation: " + imageProxy.getImageInfo().getRotationDegrees());

            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        if (visionText.getText().isEmpty()) {
                            resultText.setText("No text detected in image.");
                        } else {
                            resultText.setText(visionText.getText());
                            Log.d(TAG, "Detected text: " + visionText.getText());
                        }

                        // Collect all elements for drawing bounding boxes
                        List<Text.Element> allElements = new ArrayList<>();
                        for (Text.TextBlock block : visionText.getTextBlocks()) {
                            for (Text.Line line : block.getLines()) {
                                allElements.addAll(line.getElements());
                            }
                        }

                        Log.d(TAG, "Found " + allElements.size() + " text elements");

                        // Pass image dimensions and rotation for proper coordinate mapping
                        textOverlay.setElements(
                                allElements,
                                image.getWidth(),
                                image.getHeight(),
                                image.getRotationDegrees()
                        );

                        imageProxy.close();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Text recognition failed", e);
                        resultText.setText("Failed to recognize text.");
                        textOverlay.setElements(new ArrayList<>()); // Clear overlay
                        imageProxy.close();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            resultText.setText("Error processing image.");
            imageProxy.close();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
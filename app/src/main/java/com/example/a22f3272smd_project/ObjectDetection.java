package com.example.a22f3272smd_project;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ObjectDetection extends AppCompatActivity {

    private PreviewView previewView;
    private TextView resultText;
    private Button captureButton;
    private Button switchCameraButton;
    private Button exportButton;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ObjectDetector objectDetector;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_object_detection);

        previewView = findViewById(R.id.preview_view);
        resultText = findViewById(R.id.text_output);
        captureButton = findViewById(R.id.capture_button);
        switchCameraButton = findViewById(R.id.switch_camera_button);
        exportButton = findViewById(R.id.export_button);

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Configure the object detector
        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableClassification()
                .enableMultipleObjects()
                .build();
        objectDetector = com.google.mlkit.vision.objects.ObjectDetection.getClient(options);

        startCamera();

        switchCameraButton.setOnClickListener(v -> switchCamera());
        exportButton.setOnClickListener(v -> exportLabels());
        captureButton.setOnClickListener(v -> takePhoto());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                imageCapture = new ImageCapture.Builder().build();

                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (Exception e) {
                Log.e("ObjectDetection", "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void switchCamera() {
        cameraSelector = cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA
                ? CameraSelector.DEFAULT_FRONT_CAMERA
                : CameraSelector.DEFAULT_BACK_CAMERA;
        startCamera();
    }

    private void exportLabels() {
        String labels = resultText.getText().toString();
        if (labels.equals("Detected objects will appear here") || labels.isEmpty()) {
            Log.d("ObjectDetection", "No labels to export");
            return;
        }
        Log.d("ObjectDetection", "Exporting labels: " + labels);
        // Implement actual export logic here (e.g., save to file or share)
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        processImage(imageProxy);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("ObjectDetection", "Capture failed", exception);
                    }
                });
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImage(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            resultText.setText("Failed to capture image.");
            imageProxy.close();
            return;
        }

        @SuppressWarnings("UnsafeOptInUsageError")
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        objectDetector.process(image)
                .addOnSuccessListener(detectedObjects -> {
                    StringBuilder result = new StringBuilder();
                    if (detectedObjects.isEmpty()) {
                        result.append("No objects detected.");
                    } else {
                        for (DetectedObject detectedObject : detectedObjects) {
                            for (DetectedObject.Label label : detectedObject.getLabels()) {
                                result.append("Label: ")
                                        .append(label.getText())
                                        .append(", Confidence: ")
                                        .append(String.format("%.2f", label.getConfidence()))
                                        .append("\n");
                            }
                        }
                    }
                    resultText.setText(result.toString());
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    resultText.setText("Object detection failed.");
                    imageProxy.close();
                    Log.e("ObjectDetection", "Detection failed", e);
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (objectDetector != null) {
            objectDetector.close();
        }
    }
}


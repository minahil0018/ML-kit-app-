package com.example.a22f3272smd_project;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BarcodeDetection extends AppCompatActivity {

    private PreviewView previewView;
    private TextView resultText;
    private ExecutorService cameraExecutor;
    private boolean scanned = false;
    private final Handler handler = new Handler();
    private String lastScannedData = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_detection);

        previewView = findViewById(R.id.preview_view);
        resultText = findViewById(R.id.result_text);
        Button copyButton = findViewById(R.id.copy_button);
        Button openLinkButton = findViewById(R.id.open_link_button);

        copyButton.setOnClickListener(v -> {
            String textToCopy = resultText.getText().toString().trim();
            if (!textToCopy.isEmpty() && !textToCopy.equals("Scan a barcode...")) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("barcode", textToCopy);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            }
        });

        openLinkButton.setOnClickListener(v -> {
            String data = resultText.getText().toString().trim();

            if (data.isEmpty() || data.equals("Scan a barcode...")) {
                Toast.makeText(this, "No barcode data found", Toast.LENGTH_SHORT).show();
                return;
            }

            openDetectedContent(data);
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().build();

                BarcodeScanner scanner = BarcodeScanning.getClient();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    if (!scanned) {
                        @SuppressWarnings("UnsafeOptInUsageError")
                        InputImage inputImage = InputImage.fromMediaImage(
                                image.getImage(), image.getImageInfo().getRotationDegrees());

                        scanner.process(inputImage)
                                .addOnSuccessListener(barcodes -> {
                                    if (!barcodes.isEmpty()) {
                                        Barcode barcode = barcodes.get(0);
                                        String value = barcode.getRawValue();
                                        if (value != null && !value.equals(lastScannedData)) {
                                            scanned = true;
                                            lastScannedData = value;
                                            handleResult(value);

                                            handler.postDelayed(() -> {
                                                scanned = false;
                                            }, 2000); // Allow scanning again after 2 seconds
                                        }
                                    }
                                    image.close();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("BarcodeScan", "Detection failed", e);
                                    image.close();
                                });
                    } else {
                        image.close();
                    }
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e("BarcodeScan", "Camera init failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void handleResult(String data) {
        runOnUiThread(() -> {
            resultText.setText(data);
            MediaPlayer.create(this, R.raw.notification).start();
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(300);
            }
        });
    }

    private void openDetectedContent(String data) {
        // First check if it's a product barcode (numeric only typically)
        if (isProductBarcode(data)) {
            searchProductOnline(data);
            return;
        }

        // Check if it's a URL or needs conversion to URL
        String url = parseUrl(data);

        if (url != null) {
            // It's a URL, open directly
            openUrl(url);
        } else {
            // Not recognized as a URL, search for it
            searchGeneric(data);
        }
    }

    private boolean isProductBarcode(String data) {
        // Most product barcodes (UPC, EAN) are purely numeric and 8-14 digits
        return data.matches("\\d{8,14}");
    }

    private void searchProductOnline(String barcode) {
        // Choose a product search engine (Amazon, Google Shopping, etc.)
        String searchUrl = "https://www.google.com/search?q=" + barcode;
        openUrl(searchUrl);
    }

    private String parseUrl(String data) {
        // If already a properly formatted URL, return it
        if (data.toLowerCase().startsWith("http://") || data.toLowerCase().startsWith("https://")) {
            return data;
        }

        // Check if it's a URL without protocol
        if (data.toLowerCase().startsWith("www.") ||
                data.toLowerCase().endsWith(".com") ||
                data.toLowerCase().endsWith(".org") ||
                data.toLowerCase().endsWith(".net") ||
                data.matches(".*\\.[a-z]{2,}(/.*)?")) {
            return "https://" + data;
        }

        return null;
    }

    private void searchGeneric(String query) {
        // Fall back to a generic search if not recognized as a specific format
        String searchUrl = "https://www.google.com/search?q=" + Uri.encode(query);
        openUrl(searchUrl);
    }

    private void openUrl(String url) {
        try {
            Log.d("BarcodeDetection", "Opening URL: " + url);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

            // Set flags to properly handle the opening
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setPackage("com.android.chrome"); // Try Chrome first

            try {
                startActivity(intent);
            } catch (Exception e) {
                // If Chrome isn't available, use any browser
                intent.setPackage(null);
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e("BarcodeDetection", "Failed to open URL: " + url, e);
            Toast.makeText(this, "Failed to open the link: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        handler.removeCallbacksAndMessages(null);
    }
}
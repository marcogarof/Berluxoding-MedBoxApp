package it.uniba.berluxoding.medboxapp.controller;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import android.util.Log;
import android.widget.Toast;

import it.uniba.berluxoding.medboxapp.R;

public class HeartRateMonitorActivity extends AppCompatActivity {

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private SurfaceView cameraPreview;
    private SurfaceHolder previewHolder;
    private TextView instructionsText;
    private TextView heartRateText;
    private Button startMeasurementButton;
    private boolean measuring = false;
    private List<Long> timeStamps = new ArrayList<>();
    private List<Integer> redIntensities = new ArrayList<>();
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private CaptureRequest.Builder captureRequestBuilder;
    private CaptureRequest captureRequest;
    private CameraCaptureSession.CaptureCallback captureCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate_monitor);

        cameraPreview = findViewById(R.id.camera_preview);
        previewHolder = cameraPreview.getHolder();
        instructionsText = findViewById(R.id.instructions_text);
        heartRateText = findViewById(R.id.heart_rate_text);
        startMeasurementButton = findViewById(R.id.start_measurement_button);

        startMeasurementButton.setOnClickListener(v -> {
            if (!measuring) {
                startMeasurement();
            } else {
                stopMeasurement();
            }
        });
    }

    private void startMeasurement() {
        startMeasurementButton.setText("Ferma Misurazione");
        measuring = true;

        startBackgroundThread();
        setupImageReader();
        openCamera();
    }

    private void stopMeasurement() {
        startMeasurementButton.setText("Inizia Misurazione");
        measuring = false;

        closeCamera();
        stopBackgroundThread();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundHandler = null;
            backgroundThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void setupImageReader() {
        imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(imageReaderListener, backgroundHandler);
    }

    /**
     * private ImageReader.OnImageAvailableListener imageReaderListener = reader -> {
     *         if (!measuring) return;
     *
     *         try (Image image = reader.acquireLatestImage()) {
     *             if (image != null) {
     *                 ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
     *                 ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
     *                 ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
     *                 processFrameData(yBuffer, uBuffer, vBuffer);
     *             }
     *         }
     *     };
     */

    private ImageReader.OnImageAvailableListener imageReaderListener = reader -> {
        if (!measuring) return;

        try (Image image = reader.acquireLatestImage()) {
            if (image != null) {
                Log.d("HeartRateMonitor", "Image acquired");
                ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
                ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
                ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
                processFrameData(yBuffer, uBuffer, vBuffer);
            } else {
                Log.d("HeartRateMonitor", "Image is null");
            }
        } catch (Exception e) {
            Log.e("HeartRateMonitor", "Error processing image", e);
        }
    };


    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
                return;
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            closeCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            closeCamera();
        }
    };

    private void startCaptureSession() {
        try {
            // Prepare the Surface from the SurfaceView
            SurfaceHolder holder = cameraPreview.getHolder();
            Surface previewSurface = holder.getSurface();

            // Configure the CaptureRequest.Builder
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);

            // Create a CameraCaptureSession for camera preview
            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    // When the session is configured, start displaying the preview
                    captureSession = session;
                    try {
                        // Auto focus should be continuous for camera preview
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                        // Flash is automatically enabled when necessary
                        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);

                        // Build the CaptureRequest
                        captureRequest = captureRequestBuilder.build();

                        // Define the CaptureCallback
                        captureCallback = new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                                super.onCaptureProgressed(session, request, partialResult);
                            }

                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                            }
                        };

                        // Start displaying the camera preview
                        captureSession.setRepeatingRequest(captureRequest, captureCallback, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    // Handle configuration failure

                    // Log the failure or handle it appropriately
                    Log.e("HeartRateMonitor", "Capture session configuration failed.");

                    // Optionally, you can inform the user or attempt to recover
                    runOnUiThread(() -> {
                        Toast.makeText(HeartRateMonitorActivity.this, "Camera configuration failed. Please try again.", Toast.LENGTH_LONG).show();
                    });

                    // You might want to close the camera or retry configuration
                    closeCamera();

                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private void processFrameData(ByteBuffer yBuffer, ByteBuffer uBuffer, ByteBuffer vBuffer) {
        // Conversione YUV a RGB
        int width = imageReader.getWidth();
        int height = imageReader.getHeight();
        int[] rgb = decodeYUV420SP(yBuffer, uBuffer, vBuffer, width, height);

        Log.d("HeartRateMonitor", "RGB data: " + Arrays.toString(rgb));

        // Calcola l'intensità rossa
        int redIntensity = calculateRedIntensity(rgb);
        Log.d("HeartRateMonitor", "Red intensity: " + redIntensity);

        long currentTime = System.currentTimeMillis();
        timeStamps.add(currentTime);
        redIntensities.add(redIntensity);

        if (timeStamps.size() > 300) {
            timeStamps.remove(0);
            redIntensities.remove(0);
        }

        processFrame();
    }


    private int[] decodeYUV420SP(ByteBuffer yBuffer, ByteBuffer uBuffer, ByteBuffer vBuffer, int width, int height) {
        final int frameSize = width * height;
        int[] rgb = new int[frameSize];
        byte[] y = new byte[frameSize];
        byte[] u = new byte[frameSize / 4];
        byte[] v = new byte[frameSize / 4];

        yBuffer.get(y);
        uBuffer.get(u);
        vBuffer.get(v);

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, uOffset = 0, vOffset = 0;
            for (int i = 0; i < width; i++, yp++) {
                int yVal = (0xff & y[yp]) - 16;
                if (yVal < 0) yVal = 0;
                if ((i & 1) == 0) {
                    vOffset = (0xff & v[uvp++]) - 128;
                    uOffset = (0xff & u[uvp++]) - 128;
                }

                int y1192 = 1192 * yVal;
                int r = (y1192 + 1634 * vOffset);
                int g = (y1192 - 833 * vOffset - 400 * uOffset);
                int b = (y1192 + 2066 * uOffset);

                r = Math.max(0, Math.min(262143, r));
                g = Math.max(0, Math.min(262143, g));
                b = Math.max(0, Math.min(262143, b));

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
        return rgb;
    }

    private int calculateRedIntensity(int[] rgb) {
        long redSum = 0;
        for (int color : rgb) {
            int red = (color >> 16) & 0xff;
            redSum += red;
        }
        return (int) (redSum / rgb.length);
    }

    private void processFrame() {
        if (redIntensities.size() >= 30) {
            List<Long> filteredTimeStamps = new ArrayList<>(timeStamps);
            List<Integer> filteredRedIntensities = new ArrayList<>(redIntensities);

            // Applicazione della media mobile
            for (int i = 1; i < redIntensities.size() - 1; i++) {
                filteredRedIntensities.set(i, (redIntensities.get(i - 1) + redIntensities.get(i) + redIntensities.get(i + 1)) / 3);
            }

            // Rilevamento dei picchi
            List<Integer> peakIndices = new ArrayList<>();
            for (int i = 1; i < filteredRedIntensities.size() - 1; i++) {
                if (filteredRedIntensities.get(i) > filteredRedIntensities.get(i - 1) && filteredRedIntensities.get(i) > filteredRedIntensities.get(i + 1)) {
                    peakIndices.add(i);
                }
            }

            Log.d("HeartRateMonitor", "Peak indices: " + peakIndices);

            if (peakIndices.size() >= 2) {
                long totalInterval = 0;
                for (int i = 1; i < peakIndices.size(); i++) {
                    totalInterval += filteredTimeStamps.get(peakIndices.get(i)) - filteredTimeStamps.get(peakIndices.get(i - 1));
                }
                long averageInterval = totalInterval / (peakIndices.size() - 1);
                int bpm = (int) (60000 / averageInterval);
                runOnUiThread(() -> heartRateText.setText("Frequenza Cardiaca: " + bpm));
            }
        }
    }

}

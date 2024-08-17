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

import android.os.Looper;
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
import java.util.LinkedHashSet;
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

    private LinkedHashSet<BrightnessPlusTimeStamp> brightnessesPlusTimeStamps = new LinkedHashSet<>();

    private Handler handler = new Handler(Looper.getMainLooper());

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
        instructionsText.setText("Attendi 15 sec ...");
        measuring = true;

        if(!brightnessesPlusTimeStamps.isEmpty()) brightnessesPlusTimeStamps.clear();

        //l'acquisizione di immagini deve operare per un tempo di 10 sec
        startBackgroundThread();
        setupImageReader();
        openCamera();

        // Posticipo l'invocazione di stopMeasurement() di 10 secondi
        handler.postDelayed(() -> stopMeasurement(), 15000); // 10000 millisecondi = 10 secondi

    }

    private void stopMeasurement() {
        measuring = false;
        closeCameraExceptImageReader();
        instructionsText.setText("Calcolando la frequenza cardiaca... Attendi");

        //calcolo dell'heart rate
        processFrames();

        closeImageReader();
        stopBackgroundThread();
        startMeasurementButton.setText("Inizia Misurazione");
        instructionsText.setText("Posiziona il dito indice sulla fotocamera e sul flash");
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

        Log.d("HeartRateMonitor", "ImageReader setup complete");
    }

    private ImageReader.OnImageAvailableListener imageReaderListener = reader -> {
        if (!measuring) return;

        try (Image image = reader.acquireLatestImage()) {
            if (image != null) {
                processFrameData(image, System.currentTimeMillis());
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
            // Prepara il Surface per la visualizzazione della fotocamera
            SurfaceHolder holder = cameraPreview.getHolder();
            Surface previewSurface = holder.getSurface();

            // Configura l'ImageReader come target della sessione di acquisizione
            List<Surface> outputSurfaces = new ArrayList<>();
            outputSurfaces.add(previewSurface);
            outputSurfaces.add(imageReader.getSurface());

            // Configura la richiesta di acquisizione
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.addTarget(imageReader.getSurface());

            // Crea e avvia la sessione di acquisizione
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        // Configura la richiesta di acquisizione
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);

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

                        // Avvia la visualizzazione della fotocamera
                        captureSession.setRepeatingRequest(captureRequest, captureCallback, backgroundHandler);

                    } catch (CameraAccessException e) {
                        Log.e("HeartRateMonitor", "Error configuring capture session", e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e("HeartRateMonitor", "Capture session configuration failed.");
                    runOnUiThread(() -> {
                        Toast.makeText(HeartRateMonitorActivity.this, "Camera configuration failed. Please try again.", Toast.LENGTH_LONG).show();
                    });
                    closeCamera();
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e("HeartRateMonitor", "Error accessing camera", e);
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

    private void closeCameraExceptImageReader() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void closeImageReader() {
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private void processFrameData(@NonNull Image image, long currentTime) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        // Conversione YUV a RGB
        int[] rgb = decodeYUV420SP(yBuffer, uBuffer, vBuffer, imageReader.getWidth(), imageReader.getHeight());

        // Calcola l'intensità rossa
        double brightness = calculateLuminance(normalizeImage(rgb));

        brightnessesPlusTimeStamps.add( new BrightnessPlusTimeStamp(brightness, currentTime) );
    }

    private int[] normalizeImage(@NonNull int[] rgb) {
        int max = Arrays.stream(rgb).max().orElse(255);
        int min = Arrays.stream(rgb).min().orElse(0);
        return Arrays.stream(rgb)
                .map(color -> (color - min) * 255 / (max - min))
                .toArray();
    }

    private double calculateLuminance(@NonNull int[] rgb) {
        double luminanceSum = 0.0;
        for (int color : rgb) {
            int r = (color >> 16) & 0xff;
            int g = (color >> 8) & 0xff;
            int b = color & 0xff;
            double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
            luminanceSum += luminance;
        }
        return luminanceSum / rgb.length;
    }

    @NonNull
    private int[] decodeYUV420SP(@NonNull ByteBuffer yBuffer, @NonNull ByteBuffer uBuffer, @NonNull ByteBuffer vBuffer, int width, int height) {
        final int ySize = width * height;
        final int uvSize = ySize / 4;
        int[] rgb = new int[ySize]; // RGB array size should match the Y size
        byte[] y = new byte[ySize];
        byte[] u = new byte[uvSize];
        byte[] v = new byte[uvSize];

        // Read data from buffers into arrays
        yBuffer.get(y);
        uBuffer.get(u);
        vBuffer.get(v);

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = (j >> 1) * width / 2;
            for (int i = 0; i < width; i++, yp++) {
                int yVal = (0xff & y[yp]) - 16;
                if (yVal < 0) yVal = 0;

                int uOffset = (0xff & u[uvp]) - 128;
                int vOffset = (0xff & v[uvp]) - 128;

                if ((i & 1) == 1) uvp++; // Increment UV position only on even indices

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

    private void processFrames() {
        //Verifica che la lista con timeStamps e redIntensities contengano dati sufficienti per calcolare il BPM.
        Log.d("HeartRateMonitor", "Brightnesses plus times stamps list size: " + brightnessesPlusTimeStamps.size());

        if (brightnessesPlusTimeStamps.size() >= 30) {
            //media mobile 9 volte
            final int numeroApplicazioniDellaMediaMobile = 9;
            final int windowSize = 3;

            List<BrightnessPlusTimeStamp> smoothBrightnessesPlusTimeStamps = new ArrayList<>(brightnessesPlusTimeStamps);

            for (int i = numeroApplicazioniDellaMediaMobile; i > 0; i--) {
                smoothBrightnessesPlusTimeStamps = smoothData(smoothBrightnessesPlusTimeStamps, windowSize);
            }

            // Rilevamento dei picchi
            List<BrightnessPlusTimeStamp> peakIndices = new ArrayList<>();
            for (int i = 1; i < smoothBrightnessesPlusTimeStamps.size() - 1; i++) {
                if (smoothBrightnessesPlusTimeStamps.get(i).getBrightness() > smoothBrightnessesPlusTimeStamps.get(i - 1).getBrightness() &&
                        smoothBrightnessesPlusTimeStamps.get(i).getBrightness() > smoothBrightnessesPlusTimeStamps.get(i + 1).getBrightness()) {
                    peakIndices.add(smoothBrightnessesPlusTimeStamps.get(i));
                }
            }

            Log.d("HeartRateMonitor", "Peak indices size: " + peakIndices.size());

            //Calcolo della Frequenza Cardiaca
            if (peakIndices.size() >= 2) {
                long totalInterval = peakIndices.get(peakIndices.size()-1).getCurrentTime() - peakIndices.get(0).getCurrentTime();
                Log.d("HeartRateMonitor", "Total interval: " + totalInterval);
                long averageInterval = totalInterval / (peakIndices.size() - 1);
                int bpm = (int) (60000 / averageInterval);

                Log.d("HeartRateMonitor", "Heart rate calculated: " + bpm);

                runOnUiThread(() -> heartRateText.setText("Heart Rate: " + bpm));
            }
            else runOnUiThread(() -> instructionsText.setText("Calcolo frequenza cardiaca fallito! Riprova"));

        }
        else runOnUiThread(() -> instructionsText.setText("Acquisizione immagini fallita! Riprova"));

    }

    @NonNull
    private List<BrightnessPlusTimeStamp> smoothData(@NonNull List<BrightnessPlusTimeStamp> data, int windowSize) {
        List<BrightnessPlusTimeStamp> smoothedData = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            int windowStart = Math.max(0, i - windowSize / 2);
            int windowEnd = Math.min(data.size(), i + windowSize / 2 + 1);
            double sum = 0;
            for (int j = windowStart; j < windowEnd; j++) {
                sum += data.get(j).getBrightness();
            }
            smoothedData.add(new BrightnessPlusTimeStamp(sum / (windowEnd - windowStart), data.get(i).getCurrentTime()));
        }
        data.clear();
        return smoothedData;
    }


}


class BrightnessPlusTimeStamp {

    private double brightness;
    private long currentTime;

    BrightnessPlusTimeStamp(double brightness, long currentTime){
        super();
        this.brightness = brightness;
        this.currentTime = currentTime;
    }

    public double getBrightness() {
        return brightness;
    }

    public void setBrightness(double brightness) {
        this.brightness = brightness;
    }

    public long getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(long currentTime) {
        this.currentTime = currentTime;
    }
}

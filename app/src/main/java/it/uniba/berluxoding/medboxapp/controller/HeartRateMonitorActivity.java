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

    //private LinkedHashSet<ImageDataPlusTimeStamp> capturedImagesPlusTimeStamps = new LinkedHashSet<>();
    private LinkedHashSet<RedIntensityPlusTimeStamp> redIntensitiesPlusTimeStamps = new LinkedHashSet<>();

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
        instructionsText.setText("Aspetta 10 sec ...");
        measuring = true;

        if(!redIntensitiesPlusTimeStamps.isEmpty()) redIntensitiesPlusTimeStamps.clear();

        //l'acquisizione di immagini deve operare per un tempo di 10 sec
        startBackgroundThread();
        setupImageReader();
        openCamera();

        // Posticipo l'invocazione di stopMeasurement() di 10 secondi
        handler.postDelayed(() -> stopMeasurement(), 10000); // 10000 millisecondi = 10 secondi

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
        instructionsText.setText("Posiziona la punta del dito indice sulla fotocamera, la parte posteriore sul flash");
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
        //Log.d("HeartRateMonitor", "ImageReader callback triggered"); //Verifica se il metodo imageReaderListener viene chiamato
        if (!measuring) return;

        try (Image image = reader.acquireLatestImage()) {
            if (image != null) {
                //Log.d("HeartRateMonitor", "Image acquired");
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

    /*
    //DA ELIMINARE

    // Tempo di esposizione suggerito: 30 millisecondi, non aumentare per un cell che a va 30 fps
    private long desiredExposureTime = 30_000_000L; // 30 millisecondi in nanosecondi (1 ms = 1,000,000 ns)

    // ISO suggerito: 200 (puoi regolarlo tra 100-400 a seconda delle condizioni di luce)
    private int desiredISO = 250;
    */

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

                        /*
                        //DA ELIMINARE

                        // Disattiva l'Auto Exposure (AE), che è il sistema di esposizione automatica della fotocamera.
                        // Questo permette di fissare manualmente l'esposizione (tempo di apertura dell'otturatore).
                        //captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);

                        // Imposta manualmente il tempo di esposizione (in nanosecondi).
                        // 'desiredExposureTime' è una variabile che contiene il valore del tempo di esposizione scelto.
                        //captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, desiredExposureTime);

                        // Imposta manualmente la sensibilità ISO della fotocamera, che determina quanto la fotocamera è sensibile alla luce.
                        // 'desiredISO' è una variabile che contiene il valore ISO scelto.
                        //captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, desiredISO);

                        // Disattiva l'Auto White Balance (AWB), ovvero il bilanciamento automatico del bianco,
                        // per evitare che la fotocamera modifichi i colori in base alle condizioni di luce.
                        //captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);// oppure DAYLIGHT invece di OFF

                        // Imposta la modalità di correzione del colore della fotocamera per utilizzare una matrice di trasformazione personalizzata.
                        // Questa modalità consente di avere un controllo più preciso su come i colori vengono interpretati dalla fotocamera.
                        //captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);

                        //if(isHDRSupported()) captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR);
                        */

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

    /*
        // DA ELIMINARE
    private boolean isHDRSupported(){
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        String cameraId = "";
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            // Controlla il supporto per HDR
            int[] availableSceneModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
            boolean hdrSupported = false;

            if (availableSceneModes != null) {
                for (int mode : availableSceneModes) {
                    if (mode == CameraMetadata.CONTROL_SCENE_MODE_HDR) {
                        hdrSupported = true;
                        break;
                    }
                }
            }

            if (hdrSupported) {
                Log.d("HDR Support", "Camera ID " + cameraId + " supports HDR.");
                return true;
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        Log.d("HDR Support", "Camera ID " + cameraId + " does not support HDR.");
        return false;
    }
    */

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

    private void processFrameData(Image image, long currentTime) {
        //Log.d("HeartRateMonitor", "Processing frame data");

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        // Conversione YUV a RGB
        int[] rgb = decodeYUV420SP(yBuffer, uBuffer, vBuffer, imageReader.getWidth(), imageReader.getHeight());

        //Log.d("HeartRateMonitor", "RGB data: " + Arrays.toString(rgb));

        // Calcola l'intensità rossa
        double redIntensity = calculateLuminance(normalizeImage(rgb));
        //Log.d("HeartRateMonitor", "Red intensity: " + redIntensity);

        redIntensitiesPlusTimeStamps.add( new RedIntensityPlusTimeStamp(redIntensity, currentTime) );

        /*
            //LIMITARE I FRAME ACQUISITI PER NON ACCUMULARE TROPPI DATI
            if (timeStamps.size() > 300) {
                timeStamps.remove(0);
                redIntensities.remove(0);
            }
             */
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

    private int calculateRedIntensity(@NonNull int[] rgb) {
        long redSum = 0;
        for (int color : rgb) {
            int red = (color >> 16) & 0xff;
            redSum += red;
        }
        return (int) (redSum / rgb.length);
    }

    private void processFrames() {
        //Verifica che la lista con timeStamps e redIntensities contengano dati sufficienti per calcolare il BPM.
        Log.d("HeartRateMonitor", "Red intensities plus times stamps list size: " + redIntensitiesPlusTimeStamps.size());

        if (redIntensitiesPlusTimeStamps.size() >= 30) {

            List<RedIntensityPlusTimeStamp> redIntensitiesPlusTimeStampList = new ArrayList<>(redIntensitiesPlusTimeStamps); //smoothData(smoothData(smoothData(smoothData(smoothData(new ArrayList<>(redIntensitiesPlusTimeStamps),3),3),3),3),3);


            // Applicazione della media mobile
            for (int i = 1; i < redIntensitiesPlusTimeStampList.size() - 1; i++) {
                redIntensitiesPlusTimeStampList.set(i, redIntensitiesPlusTimeStampList.get(i).setRedIntensity(
                        (redIntensitiesPlusTimeStampList.get(i - 1).getRedIntensity() + redIntensitiesPlusTimeStampList.get(i).getRedIntensity() + redIntensitiesPlusTimeStampList.get(i + 1).getRedIntensity()) / 3)
                );
            }

            for (int i = 1; i < redIntensitiesPlusTimeStampList.size() - 1; i++) {
                redIntensitiesPlusTimeStampList.set(i, redIntensitiesPlusTimeStampList.get(i).setRedIntensity(
                        (redIntensitiesPlusTimeStampList.get(i - 1).getRedIntensity() + redIntensitiesPlusTimeStampList.get(i).getRedIntensity() + redIntensitiesPlusTimeStampList.get(i + 1).getRedIntensity()) / 3)
                );
            }

            for (int i = 1; i < redIntensitiesPlusTimeStampList.size() - 1; i++) {
                redIntensitiesPlusTimeStampList.set(i, redIntensitiesPlusTimeStampList.get(i).setRedIntensity(
                        (redIntensitiesPlusTimeStampList.get(i - 1).getRedIntensity() + redIntensitiesPlusTimeStampList.get(i).getRedIntensity() + redIntensitiesPlusTimeStampList.get(i + 1).getRedIntensity()) / 3)
                );
            }

            for (int i = 1; i < redIntensitiesPlusTimeStampList.size() - 1; i++) {
                redIntensitiesPlusTimeStampList.set(i, redIntensitiesPlusTimeStampList.get(i).setRedIntensity(
                        (redIntensitiesPlusTimeStampList.get(i - 1).getRedIntensity() + redIntensitiesPlusTimeStampList.get(i).getRedIntensity() + redIntensitiesPlusTimeStampList.get(i + 1).getRedIntensity()) / 3)
                );
            }


            // Rilevamento dei picchi
            List<RedIntensityPlusTimeStamp> peakIndices = new ArrayList<>();
            for (int i = 1; i < redIntensitiesPlusTimeStampList.size() - 1; i++) {
                if (redIntensitiesPlusTimeStampList.get(i).getRedIntensity() > redIntensitiesPlusTimeStampList.get(i - 1).getRedIntensity() &&
                        redIntensitiesPlusTimeStampList.get(i).getRedIntensity() > redIntensitiesPlusTimeStampList.get(i + 1).getRedIntensity()) {
                    peakIndices.add(redIntensitiesPlusTimeStampList.get(i));
                }
            }

            Log.d("HeartRateMonitor", "Peak indices size: " + peakIndices.size());

            //Calcolo della Frequenza Cardiaca
            if (peakIndices.size() >= 2) {
                long totalInterval = 0;
                for (int i = 1; i < peakIndices.size(); i++) {
                    totalInterval += peakIndices.get(i).getCurrentTime() - peakIndices.get(i - 1).getCurrentTime();
                }
                long averageInterval = totalInterval / (peakIndices.size() - 1);
                int bpm = (int) (60000 / averageInterval);

                Log.d("HeartRateMonitor", "Heart rate calculated: " + bpm); //Verifica che la UI venga aggiornata correttamente:

                runOnUiThread(() -> heartRateText.setText("Heart Rate: " + bpm)); // NON VA BENE, SOVRACCARICA IL MAIN THREAD, QUEST'AGGIORNAMENTO NON PUÒ ESSER FATTO AD OGNI FRAME !!!
            }
            else runOnUiThread(() -> instructionsText.setText("Calcolo frequenza cardiaca fallito! Riprova"));

        }
        else runOnUiThread(() -> instructionsText.setText("Acquisizione immagini fallita! Riprova"));

    }

    @NonNull
    private List<RedIntensityPlusTimeStamp> smoothData(@NonNull List<RedIntensityPlusTimeStamp> data, int windowSize) {
        List<RedIntensityPlusTimeStamp> smoothedData = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            int windowStart = Math.max(0, i - windowSize / 2);
            int windowEnd = Math.min(data.size(), i + windowSize / 2 + 1);
            double sum = 0;
            for (int j = windowStart; j < windowEnd; j++) {
                sum += data.get(j).getRedIntensity();
            }
            smoothedData.add(new RedIntensityPlusTimeStamp(sum / (windowEnd - windowStart), data.get(i).getCurrentTime()));
        }
        data.clear();
        return smoothedData;
    }


}


class ImageDataPlusTimeStamp {

    private byte[] y;
    private byte[] u;
    private byte[] v;
    private long currentTime;

    public ImageDataPlusTimeStamp(byte[] y, byte[] u, byte[] v, long currentTime) {
        this.y = y;
        this.u = u;
        this.v = v;
        this.currentTime = currentTime;
    }

    public byte[] getY() {
        return y;
    }

    public void setY(byte[] y) {
        this.y = y;
    }

    public byte[] getU() {
        return u;
    }

    public void setU(byte[] u) {
        this.u = u;
    }

    public byte[] getV() {
        return v;
    }

    public void setV(byte[] v) {
        this.v = v;
    }

    public long getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(long currentTime) {
        this.currentTime = currentTime;
    }
}

class RedIntensityPlusTimeStamp {

    private double redIntensity;
    private long currentTime;

    RedIntensityPlusTimeStamp(double redIntensity, long currentTime){
        super();
        this.redIntensity = redIntensity;
        this.currentTime = currentTime;
    }

    public double getRedIntensity() {
        return redIntensity;
    }

    public RedIntensityPlusTimeStamp setRedIntensity(double redIntensity) {
        this.redIntensity = redIntensity;
        return this;
    }

    public long getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(long currentTime) {
        this.currentTime = currentTime;
    }
}

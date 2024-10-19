package it.uniba.berluxoding.medboxapp.controller.devices;

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
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

import android.util.Log;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import it.uniba.berluxoding.medboxapp.R;

/**
 * Attività principale per la misurazione della frequenza cardiaca utilizzando la fotocamera del dispositivo.
 * Questa classe gestisce l'acquisizione di immagini dalla fotocamera, l'elaborazione dei dati per calcolare
 * la frequenza cardiaca basata sulle variazioni di luminosità rilevate.
 */
public class HeartRateMonitorActivity extends AppCompatActivity {

    //Dispositivo della fotocamera utilizzato per acquisire le immagini.
    private CameraDevice cameraDevice;

    //Sessione di acquisizione della fotocamera, responsabile della cattura delle immagini.
    private CameraCaptureSession captureSession;

    //Lettore di immagini per ottenere le immagini catturate dalla fotocamera.
    private ImageReader imageReader;

    //Vista per visualizzare l'anteprima della fotocamera.
    private SurfaceView cameraPreview;

    //Detentore della superficie della vista di anteprima della fotocamera.
    private SurfaceHolder previewHolder;

    //Testo per le istruzioni visualizzato all'utente.
    private TextView instructionsText;

    //Testo per visualizzare la frequenza cardiaca calcolata.
    private TextView heartRateText;

    //Pulsante per avviare e fermare la misurazione della frequenza cardiaca.
    private Button startMeasurementButton, closeButton;

    //Flag per indicare se la misurazione della frequenza cardiaca è in corso.
    private boolean measuring = false;

    //Collezione di coppie di luminosità e timestamp di tutte le immagini acquisite per l'elaborazione.
    private LinkedHashSet<BrightnessPlusTimeStamp> brightnessesPlusTimeStamps = new LinkedHashSet<>();

    //Handler per eseguire operazioni nel thread principale.
    private Handler handler = new Handler(Looper.getMainLooper());

    //Handler per eseguire operazioni nel thread di background.
    private Handler backgroundHandler;

    //Thread di background per le operazioni della fotocamera.
    private HandlerThread backgroundThread;

    //Costruttore della richiesta di acquisizione della fotocamera.
    private CaptureRequest.Builder captureRequestBuilder;

    //Richiesta di acquisizione della fotocamera.
    private CaptureRequest captureRequest;

    //Callback per la cattura delle immagini dalla fotocamera.
    private CameraCaptureSession.CaptureCallback captureCallback;

    //Riferimento al database
    private DatabaseReference mDatabase;

    //Identificativo della misurazione, e percorsi in cui salvare la misurazione
    private String key, savePath, savePath2, savePath3;

    private final String TAG = "HEART_RATE_MONITOR_ACTIVITY";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate_monitor);

        String userId = getIntent().getStringExtra("userId");
        setReferences(userId);

        cameraPreview = findViewById(R.id.camera_preview);
        previewHolder = cameraPreview.getHolder(); // Ottiene il SurfaceHolder della preview della fotocamera
        instructionsText = findViewById(R.id.instructions_text);
        heartRateText = findViewById(R.id.heart_rate_text);
        startMeasurementButton = findViewById(R.id.start_measurement_button);
        closeButton = findViewById(R.id.close_button);

        // Imposta un listener per il click sul bottone di avvio della misurazione
        startMeasurementButton.setOnClickListener(v -> {
            if (!measuring) {
                startMeasurement(); // Avvia la misurazione se non è già in corso
            } else {
                stopMeasurement(); // Ferma la misurazione se è già in corso
            }
        });
        closeButton.setOnClickListener(v -> {
            finish();
        });
    }

    /**
     * Avvia la misurazione della frequenza cardiaca.
     * Modifica il testo del pulsante per indicare che la misurazione può essere fermata e aggiorna le istruzioni per l'utente.
     * Inizia un thread di background per gestire l'acquisizione delle immagini dalla fotocamera,
     * configura l'ImageReader e apre la fotocamera per iniziare a acquisire le immagini.
     * Pianifica la chiamata a {@link #stopMeasurement()} dopo 10 secondi per terminare la misurazione.
     */
    private void startMeasurement() {
        // Cambia il testo del pulsante per indicare che la misurazione può essere fermata
        startMeasurementButton.setText("Ferma Misurazione");

        // Aggiorna il testo delle istruzioni per l'utente, informandolo che deve attendere 15 secondi
        instructionsText.setText("Attendi 10 sec ...");

        // Imposta il flag di misurazione a true per indicare che la misurazione è in corso
        measuring = true;

        // Libera la lista dei dati di luminosità e timestamp se non è vuota
        // Questo è necessario per evitare la raccolta di dati obsoleti
        if (!brightnessesPlusTimeStamps.isEmpty()) brightnessesPlusTimeStamps.clear();

        // Avvia un thread di background per eseguire operazioni della fotocamera senza bloccare il thread principale
        startBackgroundThread();

        // Configura l'ImageReader per l'acquisizione delle immagini dalla fotocamera
        setupImageReader();

        // Apre la fotocamera e avvia la sessione di acquisizione
        openCamera();

        // Pianifica la chiamata a stopMeasurement() dopo 10 secondi
        // Questo assicura che la misurazione duri 10 secondi
        handler.postDelayed(() -> stopMeasurement(), 10000); // 10000 millisecondi = 10 secondi
    }

    /**
     * Termina la misurazione della frequenza cardiaca.
     * Imposta il flag di misurazione a false, chiude la fotocamera e la sessione di acquisizione (mantenendo l'ImageReader aperto),
     * calcola la frequenza cardiaca utilizzando i dati acquisiti, e aggiorna l'interfaccia utente con i risultati.
     * Ferma il thread di background e ripristina il testo del pulsante e delle istruzioni per l'utente.
     */
    private void stopMeasurement() {
        // Imposta il flag di misurazione a false per indicare che la misurazione è terminata
        measuring = false;

        // Chiude la fotocamera e la sessione di acquisizione, mantenendo l'ImageReader aperto per l'elaborazione delle immagini
        closeCameraExceptImageReader();

        // Aggiorna il testo delle istruzioni per informare l'utente che il calcolo della frequenza cardiaca è in corso
        instructionsText.setText("Calcolando la frequenza cardiaca... Attendi");

        // Elabora i frame acquisiti per calcolare la frequenza cardiaca
        processFrames();

        // Chiude l'ImageReader ora che l'acquisizione delle immagini è completa
        closeImageReader();

        // Ferma il thread di background che eseguiva operazioni della fotocamera
        stopBackgroundThread();

        // Ripristina il testo del pulsante e le istruzioni dell'interfaccia utente
        startMeasurementButton.setText("Inizia Misurazione");
        instructionsText.setText("Posiziona il dito indice sulla fotocamera, con la parte posteriore sul flas");
    }

    /**
     * Avvia un thread di background per eseguire operazioni della fotocamera.
     * Questo thread viene utilizzato per gestire la fotocamera e l'acquisizione delle immagini senza bloccare il thread principale.
     * Se il thread di background è già in esecuzione, non viene avviato nuovamente.
     */
    private void startBackgroundThread() {
        // Verifica se il thread di background è già in esecuzione
        if (backgroundThread == null) {
            // Crea e avvia un nuovo thread di background con il nome "CameraBackground"
            backgroundThread = new HandlerThread("CameraBackground");
            backgroundThread.start();

            // Crea un Handler associato al thread di background per gestire i task in background
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }
    }

    /**
     * Ferma e libera le risorse associate al thread di background.
     * Questo metodo chiude il thread di background in modo sicuro e attende la sua terminazione completa.
     * Se il thread di background non esiste, non viene eseguita alcuna operazione.
     */
    private void stopBackgroundThread() {
        // Verifica se il thread di background esiste e deve essere fermato
        if (backgroundThread != null) {
            // Ferma il thread di background in modo sicuro
            backgroundThread.quitSafely();

            try {
                // Attende la terminazione del thread di background
                backgroundThread.join();

                // Libera le risorse associate al thread e al suo Handler
                backgroundHandler = null;
                backgroundThread = null;
            } catch (InterruptedException e) {
                // Registra un errore se il thread viene interrotto durante la terminazione
                Log.e("HeartRateMonitorActivity", "Errore durante l'arresto del thread di background", e);
                e.printStackTrace();
            }
        }
    }

    /**
     * Configura un'istanza di {@link ImageReader} per acquisire immagini dalla fotocamera.
     * Imposta la risoluzione dell'immagine a 640x480 pixel e utilizza il formato di immagine YUV_420_888.
     * Registra un listener per gestire le immagini disponibili tramite {@link ImageReader.OnImageAvailableListener}.
     * I dati acquisiti saranno gestiti dal thread di background.
     */
    private void setupImageReader() {
        // Crea un'istanza di ImageReader con risoluzione 640x480 e formato YUV_420_888
        imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);

        // Imposta un listener per gestire le immagini disponibili tramite il thread di background
        imageReader.setOnImageAvailableListener(imageReaderListener, backgroundHandler);

        // Registra un messaggio di debug per confermare che l'ImageReader è stato configurato
        Log.d("HeartRateMonitor", "ImageReader setup complete");
    }

    /**
     * Listener per gestire le immagini disponibili da {@link ImageReader}.
     * Acquisisce l'ultima immagine disponibile e la elabora se la misurazione è in corso.
     * In caso di errore durante l'acquisizione o l'elaborazione dell'immagine, viene registrato un errore.
     */
    private ImageReader.OnImageAvailableListener imageReaderListener = reader -> {
        // Controlla se la misurazione è in corso. Se non lo è, esce dal metodo
        if (!measuring) return;

        try (Image image = reader.acquireLatestImage()) {
            // Acquisisce l'ultima immagine disponibile e la elabora
            if (image != null) {
                // Passa l'immagine e il timestamp corrente per l'elaborazione dei dati
                processFrameData(image, System.currentTimeMillis());
            } else {
                // Registra un messaggio di debug se l'immagine è null
                Log.d("HeartRateMonitor", "Image is null");
            }
        } catch (Exception e) {
            // Registra un errore in caso di eccezione durante l'elaborazione dell'immagine
            Log.e("HeartRateMonitor", "Error processing image", e);
        }
    };

    /**
     * Apre la fotocamera e avvia una sessione di acquisizione delle immagini.
     * Ottiene l'istanza di {@link CameraManager} e verifica i permessi per l'uso della fotocamera.
     * Se i permessi sono concessi, apre la fotocamera utilizzando il primo ID della fotocamera disponibile.
     * Se i permessi non sono stati concessi, richiede all'utente di concedere i permessi.
     */
    private void openCamera() {
        // Ottiene l'istanza di CameraManager per accedere alla fotocamera
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            // Ottiene l'ID della prima fotocamera disponibile
            String cameraId = manager.getCameraIdList()[0];

            // Controlla i permessi per l'uso della fotocamera
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // Richiede i permessi per l'uso della fotocamera se non sono stati concessi
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
                return;
            }

            // Apre la fotocamera utilizzando il primo ID della fotocamera disponibile e il callback di stato
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            // Registra un errore in caso di eccezione durante l'accesso alla fotocamera
            e.printStackTrace();
        }
    }

    /**
     * Callback per gestire lo stato della fotocamera.
     * Gestisce l'apertura della fotocamera, la disconnessione e gli errori.
     */
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            // Imposta il dispositivo della fotocamera e avvia la sessione di acquisizione
            cameraDevice = camera;
            startCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            // Chiude la fotocamera in caso di disconnessione
            closeCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            // Chiude la fotocamera in caso di errore
            closeCamera();
        }
    };

    /**
     * Avvia una sessione di acquisizione dell'immagine della fotocamera.
     * Prepara le superfici di destinazione per il flusso video della fotocamera e per l'ImageReader.
     * Configura la richiesta di acquisizione e avvia la sessione di acquisizione dell'immagine.
     * Utilizza metodi differenti a seconda della versione API di Android:
     * - API level 28 e successivi: Usa {@link SessionConfiguration} per configurare la sessione.
     * - API level precedenti: Usa il metodo deprecato {@link CameraDevice#createCaptureSession(List, CameraCaptureSession.StateCallback, Handler)}.
     */
    private void startCaptureSession() {
        try {
            // Prepara la superficie per l'anteprima della fotocamera
            SurfaceHolder holder = cameraPreview.getHolder();
            Surface previewSurface = holder.getSurface();

            // Configura l'ImageReader come target per la sessione di acquisizione
            List<Surface> outputSurfaces = new ArrayList<>();
            outputSurfaces.add(previewSurface);
            outputSurfaces.add(imageReader.getSurface());

            // Configura la richiesta di acquisizione
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.addTarget(imageReader.getSurface());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {  // API level 28 e successivi
                // Per API level 28 e successivi, usa il nuovo metodo createCaptureSession
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(new OutputConfiguration(previewSurface));
                outputConfigs.add(new OutputConfiguration(imageReader.getSurface()));

                // Configura la sessione di acquisizione
                SessionConfiguration sessionConfig = new SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        outputConfigs,
                        getMainExecutor(),  // Executor per gestire i callback
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                configureCaptureSession(session);
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                handleConfigurationFailed();
                            }
                        }
                );

                cameraDevice.createCaptureSession(sessionConfig);

            } else {  // Per API level precedenti
                // Per dispositivi più vecchi, usa il metodo deprecato createCaptureSession
                cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        configureCaptureSession(session);
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        handleConfigurationFailed();
                    }
                }, backgroundHandler);
            }

        } catch (CameraAccessException e) {
            // Registra un errore in caso di eccezione durante l'accesso alla fotocamera
            Log.e("HeartRateMonitor", "Error accessing camera", e);
        }
    }

    /**
     * Configura la sessione di acquisizione dell'immagine della fotocamera.
     * Imposta le modalità di autofocus e flash nella richiesta di acquisizione e avvia la cattura ripetitiva.
     * Registra un callback per monitorare i progressi e il completamento delle acquisizioni.
     *
     * @param session La sessione di acquisizione da configurare.
     */
    private void configureCaptureSession(CameraCaptureSession session) {
        captureSession = session;
        try {
            // Imposta la modalità di autofocus continuo per le immagini
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // Imposta la modalità flash su torch (illuminazione continua)
            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);

            // Costruisce la richiesta di acquisizione
            captureRequest = captureRequestBuilder.build();

            // Configura un callback per monitorare i progressi e il completamento delle acquisizioni
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

            // Avvia la cattura ripetitiva delle immagini con la richiesta di acquisizione e il callback
            captureSession.setRepeatingRequest(captureRequest, captureCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            // Registra un errore in caso di eccezione durante la configurazione della sessione di acquisizione
            Log.e("HeartRateMonitor", "Error configuring capture session", e);
        }
    }

    /**
     * Gestisce il fallimento della configurazione della sessione di acquisizione della fotocamera.
     * Registra un messaggio di errore e mostra un toast all'utente per informarlo del problema.
     * Chiude la fotocamera in caso di errore di configurazione.
     */
    private void handleConfigurationFailed() {
        // Registra un errore in caso di fallimento della configurazione della sessione
        Log.e("HeartRateMonitor", "Capture session configuration failed.");
        // Mostra un messaggio di errore all'utente
        runOnUiThread(() -> {
            Toast.makeText(HeartRateMonitorActivity.this, "Camera configuration failed. Please try again.", Toast.LENGTH_LONG).show();
        });
        // Chiude la fotocamera
        closeCamera();
    }

    /**
     * Chiude tutti i componenti della fotocamera, inclusi la sessione di acquisizione, il dispositivo della fotocamera e l'ImageReader.
     * Questo metodo deve essere chiamato quando si desidera liberare tutte le risorse della fotocamera e interrompere la sua operazione.
     */
    private void closeCamera() {
        // Chiude la sessione di acquisizione se non è null e la imposta a null
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        // Chiude il dispositivo della fotocamera se non è null e lo imposta a null
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        // Chiude l'ImageReader se non è null e lo imposta a null
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    /**
     * Chiude tutti i componenti della fotocamera tranne l'ImageReader.
     * Utilizzato quando è necessario chiudere la sessione di acquisizione e il dispositivo della fotocamera senza influenzare l'ImageReader.
     */
    private void closeCameraExceptImageReader() {
        // Chiude la sessione di acquisizione se non è null e la imposta a null
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        // Chiude il dispositivo della fotocamera se non è null e lo imposta a null
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        // Non chiude l'ImageReader
    }

    /**
     * Chiude solo l'ImageReader e libera le risorse associate.
     * Questo metodo deve essere utilizzato quando è necessario chiudere solo l'ImageReader senza influenzare gli altri componenti della fotocamera.
     */
    private void closeImageReader() {
        // Chiude l'ImageReader se non è null e lo imposta a null
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    /**
     * Elabora i dati del frame catturato dalla fotocamera, convertendoli dal formato YUV a RGB,
     * calcola la luminanza del frame e la registra insieme al timestamp corrente.
     * Questo metodo è essenziale per estrarre l'informazione necessaria per il monitoraggio della frequenza cardiaca.
     *
     * @param image L'immagine catturata dalla fotocamera, in formato YUV_420_888.
     * @param currentTime Il timestamp corrente in millisecondi che indica quando l'immagine è stata catturata.
     */
    private void processFrameData(@NonNull Image image, long currentTime) {
        // Ottiene i buffer YUV dai piani dell'immagine
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        // Conversione da YUV a RGB
        int[] rgb = decodeYUV420SP(yBuffer, uBuffer, vBuffer, imageReader.getWidth(), imageReader.getHeight());

        // Calcola l'intensità luminosa basata sul canale rosso
        double brightness = calculateLuminance(normalizeImage(rgb));

        // Aggiunge la luminanza e il timestamp alla lista di campioni
        brightnessesPlusTimeStamps.add(new BrightnessPlusTimeStamp(brightness, currentTime));
    }

    /**
     * Normalizza i valori RGB mappando l'intervallo di colori originale
     * in un intervallo standard 0-255. Questo aiuta a standardizzare l'intensità
     * luminosa indipendentemente dalle variazioni di luminosità originali nel frame.
     *
     * @param rgb Array di valori RGB rappresentanti l'immagine.
     * @return Un array normalizzato di valori RGB.
     */
    private int[] normalizeImage(@NonNull int[] rgb) {
        // Trova il massimo e il minimo valore nell'array RGB
        int max = Arrays.stream(rgb).max().orElse(255);
        int min = Arrays.stream(rgb).min().orElse(0);

        // Normalizza ogni valore RGB
        return Arrays.stream(rgb)
                .map(color -> (color - min) * 255 / (max - min))
                .toArray();
    }

    /**
     * Calcola la luminanza media di un'immagine in formato RGB. La luminanza rappresenta la percezione della luminosità
     * e viene calcolata usando i coefficienti standard per i canali rosso, verde e blu, secondo la formula standard per la luminanza percettiva.
     *
     * @param rgb Array di valori RGB rappresentanti l'immagine.
     * @return La luminanza media dell'immagine.
     */
    private double calculateLuminance(@NonNull int[] rgb) {
        double luminanceSum = 0.0;

        // Calcola la luminanza per ciascun pixel
        for (int color : rgb) {
            int r = (color >> 16) & 0xff;// Estrae il valore del canale rosso
            int g = (color >> 8) & 0xff;// Estrae il valore del canale verde
            int b = color & 0xff;// Estrae il valore del canale blu
            // Calcola la luminanza utilizzando i coefficienti di ponderazione standard
            double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
            luminanceSum += luminance;
        }

        // Ritorna la luminanza media
        return luminanceSum / rgb.length;
    }

    /**
     * Decodifica un'immagine in formato YUV420SP in un array RGB. Questo è un passaggio critico nella conversione
     * dei dati della fotocamera in un formato che può essere utilizzato per ulteriori elaborazioni, come il calcolo
     * della luminanza o altre analisi basate sul colore.
     *
     * @param yBuffer Il buffer contenente i dati Y (luminanza).
     * @param uBuffer Il buffer contenente i dati U (crominanza blu).
     * @param vBuffer Il buffer contenente i dati V (crominanza rossa).
     * @param width La larghezza dell'immagine.
     * @param height L'altezza dell'immagine.
     * @return Un array di valori RGB rappresentanti l'immagine decodificata.
     */
    @NonNull
    private int[] decodeYUV420SP(@NonNull ByteBuffer yBuffer, @NonNull ByteBuffer uBuffer, @NonNull ByteBuffer vBuffer, int width, int height) {
        final int ySize = width * height;  // Dimensione del canale Y
        final int uvSize = ySize / 4;      // Dimensione dei canali U e V
        int[] rgb = new int[ySize];        // Array RGB da riempire, la dimensione dell'array RGB deve corrispondere a quella di Y
        byte[] y = new byte[ySize];        // Array per i dati Y
        byte[] u = new byte[uvSize];       // Array per i dati U
        byte[] v = new byte[uvSize];       // Array per i dati V

        // Legge i dati dai buffer nelle rispettive array
        yBuffer.get(y);
        uBuffer.get(u);
        vBuffer.get(v);

        // Conversione YUV a RGB
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = (j >> 1) * width / 2;
            for (int i = 0; i < width; i++, yp++) {
                int yVal = (0xff & y[yp]) - 16;
                if (yVal < 0) yVal = 0;

                int uOffset = (0xff & u[uvp]) - 128;
                int vOffset = (0xff & v[uvp]) - 128;

                if ((i & 1) == 1) uvp++; // Incrementa la posizione UV solo sugli indici pari

                int y1192 = 1192 * yVal;
                int r = (y1192 + 1634 * vOffset);
                int g = (y1192 - 833 * vOffset - 400 * uOffset);
                int b = (y1192 + 2066 * uOffset);

                // Limita i valori RGB nel range accettabile
                r = Math.max(0, Math.min(262143, r));
                g = Math.max(0, Math.min(262143, g));
                b = Math.max(0, Math.min(262143, b));

                // Combina i canali RGB in un singolo valore e lo memorizza nell'array
                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
        return rgb;
    }

    /**
     * Elabora i frame acquisiti per calcolare la frequenza cardiaca (BPM).
     * Verifica che ci siano dati sufficienti, applica una media mobile per lisciare i dati,
     * rileva i picchi di luminosità, e calcola il BPM sulla base dell'intervallo medio tra i picchi.
     * Visualizza il BPM calcolato o un messaggio di errore se il calcolo fallisce.
     */
    private void processFrames() {
        // Verifica che la lista con timeStamps e redIntensities contenga dati sufficienti per calcolare il BPM
        Log.d("HeartRateMonitor", "Brightnesses plus times stamps list size: " + brightnessesPlusTimeStamps.size());

        if (brightnessesPlusTimeStamps.size() >= 30) {
            // Parametri per la media mobile
            final int numeroApplicazioniDellaMediaMobile = 9;
            final int windowSize = 3;

            List<BrightnessPlusTimeStamp> smoothBrightnessesPlusTimeStamps = new ArrayList<>(brightnessesPlusTimeStamps);

            // Applica la media mobile 9 volte per lisciare i dati
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

            // Calcolo della Frequenza Cardiaca
            if (peakIndices.size() >= 2) {
                long totalInterval = peakIndices.get(peakIndices.size()-1).getCurrentTime() - peakIndices.get(0).getCurrentTime();
                Log.d("HeartRateMonitor", "Total interval: " + totalInterval);
                long averageInterval = totalInterval / (peakIndices.size() - 1);
                int bpm = (int) (60000 / averageInterval);

                Log.d("HeartRateMonitor", "Heart rate calculated: " + bpm);

                runOnUiThread(() -> heartRateText.setText("Heart Rate: " + bpm));
                dataStructure(String.valueOf(bpm));

            } else {
                runOnUiThread(() -> instructionsText.setText("Calcolo frequenza cardiaca fallito! Riprova"));
            }
        } else {
            runOnUiThread(() -> instructionsText.setText("Acquisizione immagini fallita! Riprova"));
        }
    }

    /**
     * Metodo che imposta i percorsi in cui salvare i dati nel database nello spazio di memoria disponibile
     * per il singolo utente.
     * @param userId Identificativo dell'utente che sta utilizzando il dispostivo nel momento attuale.
     */
    private void setReferences (String userId) {
        mDatabase = FirebaseDatabase.getInstance().getReference();
        DatabaseReference userRef = mDatabase.child("AsilApp").child(userId);
        key = userRef.child("misurazioni").push().getKey();
        savePath = "AsilApp/" + userId + "/misurazioni/" + key;
        savePath2 = "AsilApp/" + userId + "/misurazioni-strumento/cardifrequenzimetro/" + key;
        savePath3 = "medbox/risposta/misurazioneId";
    }

    /**
     * Metodo che crea una mappa dei dati da salvare, necessita del valore della misurazione da salvare.
     * @param valore Risultato della misurazione.
     */
    private void dataStructure(String valore) {
        HashMap<String, String> map = new HashMap<>();
        map.put("id", key);
        map.put("strumento", "cardifrequenzimetro");
        map.put("valore", valore);
        //ToDo gestione della data e dell'ora
        map.put("data", "02/12/1994");
        map.put("orario", "12:00");

        save(map);
    }

    /**
     * Metodo che salva i dati nei percorsi indicati.
     * @param map Struttura dati da salvare.
     */
    private void save (HashMap<String, String> map) {
        Log.d(TAG, "savingData!");
        HashMap<String, Object> saveMap = new HashMap<>();
        saveMap.put(savePath, map);
        saveMap.put(savePath2, map);
        saveMap.put(savePath3, key);
        mDatabase.updateChildren(saveMap)
                .addOnSuccessListener(aVoid -> {
                    // Successo
                    Log.d("Firebase", "Dati salvati correttamente in più percorsi.");
                    finish();
                })
                .addOnFailureListener(e -> {
                    // Fallimento
                    Log.e("Firebase", "Errore durante il salvataggio dei dati: " + e.getMessage());
                    finish();
                });

    }

    /**
     * Applica una media mobile ai dati di luminosità per lisciarli, riducendo le fluttuazioni
     * e facilitando il rilevamento dei picchi.
     *
     * @param data La lista di oggetti BrightnessPlusTimeStamp contenente i dati di luminosità e i relativi timestamp.
     * @param windowSize La dimensione della finestra per la media mobile.
     * @return Una lista di BrightnessPlusTimeStamp lisciata con i valori di luminosità mediati.
     */
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


/**
 * La classe `BrightnessPlusTimeStamp` rappresenta una coppia di valori composta dalla luminosità di un'immagine
 * e dal timestamp associato a quando l'immagine è stata acquisita.
 *
 * Questa classe viene utilizzata per memorizzare e gestire i dati necessari per calcolare la frequenza cardiaca (BPM)
 * basata sulle variazioni di luminosità nel tempo.
 */
class BrightnessPlusTimeStamp {

    private double brightness;
    private long currentTime;

    BrightnessPlusTimeStamp(double brightness, long currentTime) {
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

package it.uniba.berluxoding.medboxapp.controller.devices;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

import it.uniba.berluxoding.medboxapp.R;

/**
 * Activity per la registrazione e il salvataggio delle misurazioni della pressione sanguigna di un utente.
 * L'utente può selezionare tra tre valori di pressione: ottimale, alta o bassa.
 * Ogni scelta viene salvata nel database Firebase in percorsi multipli.
 */
public class BloodPressureActivity extends AppCompatActivity {

    // Costanti per i valori di pressione sanguigna
    private Button ottimale, bassa, alta;
    private DatabaseReference mDatabase, userRef; // Riferimenti al database Firebase
    private String key, savePath, savePath2, savePath3; // Percorsi di salvataggio nel database
    private final String TAG = "BLOOD_PRESSURE_ACTIVITY"; // Tag per i log

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Abilita EdgeToEdge per un layout a schermo intero
        EdgeToEdge.enable(this);
        // Imposta il layout per questa activity
        setContentView(R.layout.activity_blood_pressure);

        // Gestione insets per dispositivi con schermi a tutto schermo
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Ottieni l'ID utente dalla Intent
        String userId = getIntent().getStringExtra("userId");
        setReferences(userId);

        // Configura i listener dei pulsanti
        buttonBinding();
    }

    /**
     * Inizializza i riferimenti al database e definisce i percorsi di salvataggio per l'utente.
     *
     * @param userId L'ID dell'utente corrente
     */
    private void setReferences(String userId) {
        mDatabase = FirebaseDatabase.getInstance().getReference();
        userRef = mDatabase.child("AsilApp").child(userId);
        key = userRef.child("misurazioni").push().getKey();
        savePath = "AsilApp/" + userId + "/misurazioni/" + key;
        savePath2 = "AsilApp/" + userId + "/misurazioni-strumento/sfigmomanometro/" + key;
        savePath3 = "medbox/risposta/misurazioneId";
    }

    /**
     * Configura i pulsanti per registrare i diversi valori di pressione sanguigna.
     */
    private void buttonBinding() {
        Log.d(TAG, "button binding!");
        bassa = findViewById(R.id.pressione_bassa);
        ottimale = findViewById(R.id.pressione_ottimale);
        alta = findViewById(R.id.pressione_alta);

        // Imposta il valore della pressione in base al pulsante selezionato
        bassa.setOnClickListener(v -> salvaPressione("90/60mmHg"));
        ottimale.setOnClickListener(v -> salvaPressione("120/80mmHg"));
        alta.setOnClickListener(v -> salvaPressione("140/90mmHg"));
    }

    /**
     * Prepara la struttura dati con il valore della pressione selezionato e richiama il salvataggio.
     *
     * @param pressione Il valore della pressione sanguigna selezionato
     */
    private void salvaPressione(String pressione) {
        dataStructure(pressione);
    }

    /**
     * Crea una struttura dati con i dettagli della misurazione e richiama la funzione di salvataggio.
     *
     * @param valore Il valore della pressione sanguigna
     */
    private void dataStructure(String valore) {
        HashMap<String, String> map = new HashMap<>();
        map.put("id", key);
        map.put("strumento", "sfigmomanometro");
        map.put("valore", valore);
        // ToDo: Gestione della data e dell'ora
        map.put("data", "1994/12/02");
        map.put("orario", "12:00");

        save(map);
    }

    /**
     * Salva i dati della misurazione nei percorsi multipli definiti nel database Firebase.
     *
     * @param map La mappa contenente i dati della misurazione
     */
    private void save(HashMap<String, String> map) {
        Log.d(TAG, "savingData!");
        HashMap<String, Object> saveMap = new HashMap<>();
        saveMap.put(savePath, map);
        saveMap.put(savePath2, map);
        saveMap.put(savePath3, key);
        mDatabase.updateChildren(saveMap)
                .addOnSuccessListener(aVoid -> {
                    // Salvataggio riuscito
                    Log.d("Firebase", "Dati salvati correttamente in più percorsi.");
                    finish();
                })
                .addOnFailureListener(e -> {
                    // Fallimento nel salvataggio
                    Log.e("Firebase", "Errore durante il salvataggio dei dati: " + e.getMessage());
                    finish();
                });
    }
}

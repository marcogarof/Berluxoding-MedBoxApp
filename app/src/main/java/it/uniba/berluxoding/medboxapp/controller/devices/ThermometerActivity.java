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
 * Activity per la registrazione e il salvataggio delle misurazioni della temperatura corporea di un utente.
 * L'utente può selezionare tra tre valori di temperatura: limite inferiore, limite superiore e limite febbrile.
 * Ogni scelta viene salvata nel database Firebase in percorsi multipli.
 */
public class ThermometerActivity extends AppCompatActivity {

    private DatabaseReference mDatabase; // Riferimento al database Firebase
    private String key, savePath, savePath2, savePath3; // Percorsi di salvataggio nel database
    private final String TAG = "THERMOMETER_ACTIVITY"; // Tag per i log

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Abilita EdgeToEdge per un layout a schermo intero
        EdgeToEdge.enable(this);
        // Imposta il layout per questa activity
        setContentView(R.layout.activity_thermometer);

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
        DatabaseReference userRef = mDatabase.child("AsilApp").child(userId);
        key = userRef.child("misurazioni").push().getKey();
        savePath = "AsilApp/" + userId + "/misurazioni/" + key;
        savePath2 = "AsilApp/" + userId + "/misurazioni-strumento/termometro/" + key;
        savePath3 = "medbox/risposta/misurazioneId";
    }

    /**
     * Configura i pulsanti per registrare i diversi valori di temperatura.
     */
    private void buttonBinding() {
        Log.d(TAG, "button binding!");
        Button lowerLimit = findViewById(R.id.lower_limit);
        Button burningLimit = findViewById(R.id.burning_limit);
        Button superiorLimit = findViewById(R.id.superior_limit);

        // Imposta il valore della temperatura in base al pulsante selezionato
        lowerLimit.setOnClickListener(v -> salvaTemperature("35,5°C"));
        superiorLimit.setOnClickListener(v -> salvaTemperature("37,0°C"));
        burningLimit.setOnClickListener(v -> salvaTemperature("38.8°C"));
    }

    /**
     * Prepara la struttura dati con il valore della temperatura selezionato e richiama il salvataggio.
     *
     * @param temperature Il valore della temperatura selezionato
     */
    private void salvaTemperature(String temperature) {
        dataStructure(temperature);
    }

    /**
     * Crea una struttura dati con i dettagli della misurazione e richiama la funzione di salvataggio.
     *
     * @param temperature Il valore della temperatura corporea
     */
    private void dataStructure(String temperature) {
        HashMap<String, String> map = new HashMap<>();
        map.put("id", key);
        map.put("strumento", "termometro");
        map.put("valore", temperature);
        // ToDo: Gestione della data e dell'ora
        map.put("data", "2000/05/25");
        map.put("orario", "13:00");

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

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

public class ThermometerActivity extends AppCompatActivity {

    private Button lowerLimit, superiorLimit, burningLimit;
    private DatabaseReference mDatabase, userRef;
    private String temperature, key, savePath, savePath2, savePath3;
    private final String TAG = "THERMOMETER_ACTIVITY";

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_thermometer);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        String userId = getIntent().getStringExtra("userId");
        setReferences(userId);

        buttonBinding();
    }


    private void setReferences (String userId) {
        mDatabase = FirebaseDatabase.getInstance().getReference();
        userRef = mDatabase.child("AsilApp").child(userId);
        key = userRef.child("misurazioni").push().getKey();
        savePath = "AsilApp/" + userId + "/misurazioni/" + key;
        savePath2 = "AsilApp/" + userId + "/misurazioni-strumento/termometro/" + key;
        savePath3 = "medbox/risposta/misurazioneId";
    }

    private void buttonBinding() {
        Log.d(TAG, "button binding!");
        lowerLimit = findViewById(R.id.lower_limit);
        burningLimit = findViewById(R.id.burning_limit);
        superiorLimit = findViewById(R.id.superior_limit);

        /**
         * Valore limite inferiore delle temperature normali
         */
        lowerLimit.setOnClickListener(v -> {
            temperature = "35,5°C";
            dataStructure();
        });
        /**
         * Valore limite superiore delle temperature normali
         */
        superiorLimit.setOnClickListener(v -> {
            temperature = "37,0°C";
            dataStructure();
        });
        /**
         * Valore limite superiore delle temperature febbrili,
         * oltre tale valore si rischiano danni alle cellule cerebrali che possono causare convulsioni
         */
        burningLimit.setOnClickListener(v -> {
            temperature = "38.8°C";
            dataStructure();
        });
    }
    private void dataStructure() {
        HashMap<String, String> map = new HashMap<>();
        map.put("id", key);
        map.put("strumento", "sfigmomanometro");
        map.put("valore", temperature);
        //ToDo gestione della data e dell'ora
        map.put("data", "25/05/2000");
        map.put("orario", "13:00");

        save(map);
    }

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
}
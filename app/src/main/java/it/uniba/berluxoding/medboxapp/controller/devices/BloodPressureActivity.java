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

public class BloodPressureActivity extends AppCompatActivity {

    //valore = 120/80 mmHg pressione ottimale
    //valore > 140/90 mmHg pressione alta
    //valore <  90/60 mmHg pressione bassa

    private Button ottimale, bassa, alta;
    private DatabaseReference mDatabase, userRef;
    private String pressione, key, savePath, savePath2, savePath3;
    private final String TAG = "BLOOD_PRESSURE_ACTIVITY";

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_blood_pressure);
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
        savePath2 = "AsilApp/" + userId + "/misurazioni-strumento/sfigmomanometro/" + key;
        savePath3 = "medbox/risposta/misurazioneId";
    }

    private void buttonBinding() {
        Log.d(TAG, "button binding!");
        ottimale = findViewById(R.id.pressione_ottimale);
        bassa = findViewById(R.id.pressione_bassa);
        alta = findViewById(R.id.pressione_alta);

        ottimale.setOnClickListener(v -> {
            pressione = "120/80mmHg";
            dataStructure(pressione);
        });
        bassa.setOnClickListener(v -> {
            pressione = "90/60mmHg";
            dataStructure(pressione);
        });
        alta.setOnClickListener(v -> {
            pressione = "140/90mmHg";
            dataStructure(pressione);
        });
    }
    private void dataStructure(String valore) {
        HashMap<String, String> map = new HashMap<>();
        map.put("id", key);
        map.put("strumento", "sfigmomanometro");
        map.put("valore", valore);
        //ToDo gestione della data e dell'ora
        map.put("data", "1994/12/02");
        map.put("orario", "12:00");

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
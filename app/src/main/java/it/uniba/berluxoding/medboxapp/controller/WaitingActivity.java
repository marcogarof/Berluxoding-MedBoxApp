package it.uniba.berluxoding.medboxapp.controller;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import it.uniba.berluxoding.medboxapp.R;
import it.uniba.berluxoding.medboxapp.controller.devices.BloodPressureActivity;
import it.uniba.berluxoding.medboxapp.controller.devices.HeartRateMonitorActivity;
import it.uniba.berluxoding.medboxapp.controller.devices.ThermometerActivity;

public class WaitingActivity extends AppCompatActivity {

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_waiting);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Riferimento al nodo 'medbox/richiesta'
        DatabaseReference richiestaRef = FirebaseDatabase.getInstance().getReference("medbox/richiesta");

// Listener per ricevere nuove richieste
        richiestaRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Leggi la richiesta
                    String userId = dataSnapshot.child("userId").getValue(String.class);
                    String strumento = dataSnapshot.child("strumento").getValue(String.class);

                    Log.d("Firebase", "Richiesta ricevuta: userId=" + userId + ", strumento=" + strumento);


                    richiestaRef.removeEventListener(this);// Rimuovi il listener
                    //rimuovi la richiesta
                    richiestaRef.removeValue()
                            .addOnSuccessListener(aVoid -> Log.d("Firebase", "Rischiesta eliminata con successo."))
                            .addOnFailureListener(e -> Log.e("Firebase", "Errore nell'eliminazione della richiesta: " + e.getMessage()));
                    //apri nuova activity
                    openInstrument(strumento, userId);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("Firebase", "Errore nel recupero della richiesta: " + databaseError.getMessage());
            }
        });
    }

    private void openInstrument(String strumento, String uId) {
        // In base al servizio ricevuto, naviga alla giusta Activity
        if ("cardifrequenzimetro".equals(strumento)) {
            // Naviga alla Activity per il servizio Chitarra
            Intent intent = new Intent(this, HeartRateMonitorActivity.class);
            intent.putExtra("userId", uId);
            startActivity(intent);
        } else if ("sfigmomanometro".equals(strumento)) {
            // Naviga alla Activity per il servizio Batteria
            Intent intent = new Intent(this, BloodPressureActivity.class);
            intent.putExtra("userId", uId);
            startActivity(intent);
        } else if ("termometro".equals(strumento)) {
            // Naviga alla Activity per il servizio Batteria
            Intent intent = new Intent(this, ThermometerActivity.class);
            intent.putExtra("userId", uId);
            startActivity(intent);
        } /*else {
            // Naviga a una schermata di errore o gestione non riconosciuta
            Intent intent = new Intent(this, ErrorActivity.class);
            startActivity(intent);
        }*/
    }
}
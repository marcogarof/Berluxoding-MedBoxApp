package it.uniba.berluxoding.medboxapp.controller;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
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

/**
 * Activity che si occupa di monitorare le richieste di accesso agli strumenti medici.
 * Una volta ricevuta una richiesta, naviga alla schermata appropriata in base allo strumento richiesto.
 */
public class WaitingActivity extends AppCompatActivity {
    DatabaseReference richiestaRef;
    ValueEventListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Abilita EdgeToEdge per un layout a schermo intero
        EdgeToEdge.enable(this);
        // Imposta il layout per questa activity
        setContentView(R.layout.activity_waiting);

        // Gestisce i margini per schermi a tutto schermo
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Riferimento al nodo 'medbox/richiesta'
        richiestaRef = FirebaseDatabase.getInstance().getReference("medbox/richiesta");

        // Riferimento al nodo 'medbox/richiesta' nel database Firebase
        DatabaseReference richiestaRef = FirebaseDatabase.getInstance().getReference("medbox/richiesta");
        setListener(richiestaRef);
    }

    @Override
    protected void onPause () {
        super.onPause();
        richiestaRef.removeEventListener(listener);
    }


    /**
     * Imposta un listener per ascoltare le nuove richieste nel database Firebase.
     *
     * @param ref Riferimento al nodo nel database da monitorare.
     */
    private void setListener(DatabaseReference ref) {
        // Listener per ricevere nuove richieste

        listener = ref.addValueEventListener(new ValueEventListener() {
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Leggi i dati della richiesta
                    String userId = dataSnapshot.child("userId").getValue(String.class);
                    String strumento = dataSnapshot.child("strumento").getValue(String.class);

                    Log.d("Firebase", "Richiesta ricevuta: userId=" + userId + ", strumento=" + strumento);

                    // Rimuove il listener e cancella la richiesta dal database
                    ref.removeEventListener(this);
                    ref.removeValue()
                            .addOnSuccessListener(aVoid -> Log.d("Firebase", "Richiesta eliminata con successo."))
                            .addOnFailureListener(e -> Log.e("Firebase", "Errore nell'eliminazione della richiesta: " + e.getMessage()));

                    // Apre la nuova activity in base allo strumento richiesto
                    openInstrument(strumento, userId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("Firebase", "Errore nel recupero della richiesta: " + databaseError.getMessage());
            }
        });
    }

    /**
     * Avvia la schermata corrispondente allo strumento richiesto dall'utente.
     *
     * @param strumento Il nome dello strumento richiesto.
     * @param uId       L'ID dell'utente che ha effettuato la richiesta.
     */
    private void openInstrument(String strumento, String uId) {
        if ("cardifrequenzimetro".equals(strumento))
            startActivityWithIntent(HeartRateMonitorActivity.class, uId);
        else if ("sfigmomanometro".equals(strumento))
            startActivityWithIntent(BloodPressureActivity.class, uId);
        else if ("termometro".equals(strumento))
            startActivityWithIntent(ThermometerActivity.class, uId);
    }

    /**
     * Avvia una nuova attività, passando l'ID dell'utente come extra.
     *
     * @param targetActivity La classe dell'attività da avviare.
     * @param uId            L'ID dell'utente che ha richiesto lo strumento.
     */
    private void startActivityWithIntent(Class<?> targetActivity, String uId) {
        Intent intent = new Intent(WaitingActivity.this, targetActivity);
        intent.putExtra("userId", uId);
        startActivity(intent);
    }
}

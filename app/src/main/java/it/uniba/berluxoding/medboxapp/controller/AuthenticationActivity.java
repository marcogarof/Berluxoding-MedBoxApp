package it.uniba.berluxoding.medboxapp.controller;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

import it.uniba.berluxoding.medboxapp.R;

/**
 * Activity di autenticazione che gestisce il login automatico di un utente su Firebase Authentication.
 * Se il login ha successo, naviga verso la schermata `WaitingActivity`.
 */
public class AuthenticationActivity extends AppCompatActivity {

    private FirebaseAuth mAuth; // Istanza di Firebase Authentication
    private final String TAG = "AUTHENTICATION_ACTIVITY"; // Tag per i log

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Abilita EdgeToEdge per un layout a schermo intero
        EdgeToEdge.enable(this);
        // Imposta il layout per questa activity
        setContentView(R.layout.activity_authentication);

        // Gestione insets per dispositivi con schermi a tutto schermo
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Inizializza FirebaseAuth
        mAuth = FirebaseAuth.getInstance();
        // Esegue il login dell'utente
        signIn();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    /**
     * Esegue l'accesso su Firebase utilizzando le credenziali fornite.
     * Se il login ha successo, procede alla schermata successiva.
     */
    private void signIn() {
        Log.d(TAG, "Authentication");

        String user = "medbox@app.it"; // Email dell'utente per l'autenticazione
        String pass = "password123";    // Password dell'utente

        mAuth.signInWithEmailAndPassword(user, pass)
                .addOnCompleteListener(this, task -> {
                    Log.d(TAG, "signIn:onComplete:" + task.isSuccessful());

                    if (task.isSuccessful()) {
                        onAuthSuccess();
                    } else {
                        Toast.makeText(getApplicationContext(), "Sign In Failed",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Callback chiamato quando l'autenticazione ha successo.
     * Avvia l'activity `WaitingActivity` e termina l'activity corrente.
     */
    private void onAuthSuccess() {
        Log.d(TAG, "Authenticated!");
        Intent intent = new Intent(AuthenticationActivity.this, WaitingActivity.class);
        startActivity(intent);
        finish();
    }
}

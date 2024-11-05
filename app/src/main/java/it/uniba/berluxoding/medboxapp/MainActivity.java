package it.uniba.berluxoding.medboxapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import it.uniba.berluxoding.medboxapp.controller.AuthenticationActivity;

/**
 * Activity principale che funge da schermata di avvio dell'applicazione.
 * Dopo un breve ritardo, reindirizza l'utente alla schermata di autenticazione.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Abilita EdgeToEdge per un layout a schermo intero
        EdgeToEdge.enable(this);
        // Imposta il layout per questa activity
        setContentView(R.layout.activity_main);

        // Utilizza un Handler per avviare l'activity di autenticazione dopo un ritardo di 100 ms
        new Handler().postDelayed(() -> {
            // Crea un'intent per aprire l'activity "AuthenticationActivity"
            Intent openPage = new Intent(MainActivity.this, AuthenticationActivity.class);
            // Avvia l'activity di autenticazione
            startActivity(openPage);
            // Chiude l'activity corrente
            finish();
        }, 100); // Ritardo di 100 ms
    }
}

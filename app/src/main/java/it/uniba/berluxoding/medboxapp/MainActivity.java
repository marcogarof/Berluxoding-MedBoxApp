package it.uniba.berluxoding.medboxapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import it.uniba.berluxoding.medboxapp.controller.AuthenticationActivity;

public class  MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);


        new Handler().postDelayed(() -> {

            // definisco l'intenzione di aprire l'Activity "Page1.java"
            Intent openPage = new Intent(MainActivity.this, AuthenticationActivity.class);
            // passo all'attivazione dell'activity page1.java
            startActivity(openPage);
            finish();
        }, 100);
    }
}
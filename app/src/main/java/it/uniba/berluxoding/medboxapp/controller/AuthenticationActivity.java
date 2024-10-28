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

public class AuthenticationActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    private final String TAG = "AUTHENTICATION_ACTIVITY";

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_authentication);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        signIn();

    }


    @Override
    public void onStart() {
        super.onStart();
    }

    private void signIn() {
        Log.d(TAG, "Authentication");

        String user = "medbox@app.it";
        String pass = "password123";
        mAuth.signInWithEmailAndPassword(user, pass)
                .addOnCompleteListener(this, task -> {
                    Log.d(TAG, "signIn:onComplete:" + task.isSuccessful());
                    // hideProgressBar();

                    if (task.isSuccessful()) {
                        onAuthSuccess();
                    } else {
                        Toast.makeText(getApplicationContext(), "Sign In Failed",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void onAuthSuccess() {
        Log.d(TAG, "Authenticated!");
        Intent intent = new Intent(AuthenticationActivity.this, WaitingActivity.class);
        startActivity(intent);
        finish();
    }
}
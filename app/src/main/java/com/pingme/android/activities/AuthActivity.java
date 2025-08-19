package com.pingme.android.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.pingme.android.R;
import com.pingme.android.databinding.ActivityAuthBinding;
import com.pingme.android.utils.FirebaseUtil;

import android.widget.ProgressBar;

public class AuthActivity extends AppCompatActivity {

    private ActivityAuthBinding binding;
    private FirebaseAuth mAuth;
    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;
    private boolean isLoginMode = true;

    private final ActivityResultLauncher<IntentSenderRequest> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    try {
                        SignInCredential credential = oneTapClient.getSignInCredentialFromIntent(result.getData());
                        String idToken = credential.getGoogleIdToken();
                        if (idToken != null) {
                            firebaseAuthWithGoogle(idToken);
                        } else {
                            Toast.makeText(this, "No ID token found", Toast.LENGTH_SHORT).show();
                        }
                    } catch (ApiException e) {
                        Toast.makeText(this, "Google Sign-In failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        oneTapClient = Identity.getSignInClient(this);

        signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                                .setSupported(true)
                                .setServerClientId(getString(R.string.default_web_client_id)) // Replace in strings.xml
                                .setFilterByAuthorizedAccounts(false)
                                .build()
                )
                .setAutoSelectEnabled(true)
                .build();

        setupClickListeners();
        updateUI();
    }

    private void setupClickListeners() {
        binding.btnGoogle.setOnClickListener(v -> signInWithGoogle());
        binding.btnEmailAction.setOnClickListener(v -> {
            if (isLoginMode) {
                loginWithEmail();
            } else {
                registerWithEmail();
            }
        });
        binding.tvSwitchMode.setOnClickListener(v -> {
            isLoginMode = !isLoginMode;
            updateUI();
        });

        // Add forgot password click listener
        binding.tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void updateUI() {
        if (isLoginMode) {
            binding.btnEmailAction.setText(R.string.login);
            binding.tvSwitchMode.setText(R.string.switch_to_signup);
            binding.tvTitle.setText(R.string.welcome_back);
            binding.tvForgotPassword.setVisibility(View.VISIBLE);
        } else {
            binding.btnEmailAction.setText(R.string.sign_up);
            binding.tvSwitchMode.setText(R.string.switch_to_login);
            binding.tvTitle.setText(R.string.create_account);
            binding.tvForgotPassword.setVisibility(View.GONE);
        }
    }

    private void signInWithGoogle() {
        showProgress(true);
        oneTapClient.beginSignIn(signInRequest)
                .addOnSuccessListener(this, result -> {
                    try {
                        IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(result.getPendingIntent().getIntentSender()).build();
                        googleSignInLauncher.launch(intentSenderRequest);
                    } catch (Exception e) {
                        showProgress(false);
                        Toast.makeText(this, "Couldn't launch sign-in: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Toast.makeText(this, "Sign-in failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    showProgress(false);
                    if (task.isSuccessful()) {
                        checkUserProfile();
                    } else {
                        Toast.makeText(AuthActivity.this, "Google authentication failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loginWithEmail() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgress(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showProgress(false);
                    if (task.isSuccessful()) {
                        checkUserProfile();
                    } else {
                        String errorMessage = "Authentication failed";
                        if (task.getException() != null) {
                            errorMessage = task.getException().getMessage();
                        }
                        Toast.makeText(AuthActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void registerWithEmail() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgress(true);
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showProgress(false);
                    if (task.isSuccessful()) {
                        startActivity(new Intent(AuthActivity.this, SetupProfileActivity.class));
                        finish();
                    } else {
                        String errorMessage = "Registration failed";
                        if (task.getException() != null) {
                            errorMessage = task.getException().getMessage();
                        }
                        Toast.makeText(AuthActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkUserProfile() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        FirebaseUtil.getUserRef(userId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                startActivity(new Intent(AuthActivity.this, MainActivity.class));
                finish();
            } else {
                startActivity(new Intent(AuthActivity.this, SetupProfileActivity.class));
                finish();
            }
        });
    }

    private void showForgotPasswordDialog() {
        // Inflate the forgot password layout
        View dialogView = LayoutInflater.from(this).inflate(R.layout.forgot_password, null);

        TextInputEditText etResetEmail = dialogView.findViewById(R.id.etResetEmail);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        MaterialButton btnSendReset = dialogView.findViewById(R.id.btnSendReset);
        ProgressBar progressBar = dialogView.findViewById(R.id.progressBar);

        // Pre-fill email if available
        String currentEmail = binding.etEmail.getText().toString().trim();
        if (!currentEmail.isEmpty()) {
            etResetEmail.setText(currentEmail);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSendReset.setOnClickListener(v -> {
            String email = etResetEmail.getText().toString().trim();

            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email address", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show progress
            progressBar.setVisibility(View.VISIBLE);
            btnSendReset.setEnabled(false);
            btnCancel.setEnabled(false);

            // Send password reset email
            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        // Hide progress
                        progressBar.setVisibility(View.GONE);
                        btnSendReset.setEnabled(true);
                        btnCancel.setEnabled(true);

                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Password reset email sent successfully. Please check your inbox.", Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                        } else {
                            String errorMessage = "Failed to send password reset email";
                            if (task.getException() != null) {
                                String exceptionMessage = task.getException().getMessage();
                                if (exceptionMessage != null) {
                                    if (exceptionMessage.contains("user-not-found")) {
                                        errorMessage = "No account found with this email address";
                                    } else if (exceptionMessage.contains("invalid-email")) {
                                        errorMessage = "Invalid email address";
                                    } else if (exceptionMessage.contains("too-many-requests")) {
                                        errorMessage = "Too many requests. Please try again later";
                                    } else {
                                        errorMessage = exceptionMessage;
                                    }
                                }
                            }
                            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        dialog.show();
    }

    private void showProgress(boolean show) {
        if (show) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnEmailAction.setEnabled(false);
            binding.btnGoogle.setEnabled(false);
            binding.tvSwitchMode.setEnabled(false);
            binding.tvForgotPassword.setEnabled(false);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnEmailAction.setEnabled(true);
            binding.btnGoogle.setEnabled(true);
            binding.tvSwitchMode.setEnabled(true);
            binding.tvForgotPassword.setEnabled(true);
        }
    }
}
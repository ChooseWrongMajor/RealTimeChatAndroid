package com.project_android.realtimechat.activities;

import android.content.Intent;import android.os.Bundle;import android.util.Patterns;import android.view.View;import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;import androidx.activity.result.contract.ActivityResultContracts;import androidx.appcompat.app.AppCompatActivity;

import com.facebook.AccessToken;import com.facebook.CallbackManager;import com.facebook.FacebookCallback;import com.facebook.FacebookException;import com.facebook.login.LoginManager;import com.facebook.login.LoginResult;import com.google.android.gms.auth.api.signin.GoogleSignIn;import com.google.android.gms.auth.api.signin.GoogleSignInAccount;import com.google.android.gms.auth.api.signin.GoogleSignInClient;import com.google.android.gms.auth.api.signin.GoogleSignInOptions;import com.google.android.gms.common.api.ApiException;import com.google.firebase.auth.AuthCredential;import com.google.firebase.auth.FacebookAuthProvider;import com.google.firebase.auth.FirebaseAuth;import com.google.firebase.auth.FirebaseUser;import com.google.firebase.auth.GoogleAuthProvider;import com.google.firebase.firestore.DocumentSnapshot;import com.google.firebase.firestore.FirebaseFirestore;import com.project_android.realtimechat.R;import com.project_android.realtimechat.databinding.ActivitySignInBinding;import com.project_android.realtimechat.utilities.Constants;import com.project_android.realtimechat.utilities.PreferenceManager;

import java.util.Arrays;import java.util.HashMap;

public class SignInActivity extends AppCompatActivity {

    private ActivitySignInBinding binding;
    private PreferenceManager preferenceManager;
    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;
    private CallbackManager callbackManager;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleGoogleSignInResult(result.getData());
                } else {
                    loading(false);
                    showToast("Google sign in cancelled");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferenceManager = new PreferenceManager(getApplicationContext());

        if (preferenceManager.getBoolean(Constants.KEY_IS_SIGNED_IN)) {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        binding = ActivitySignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseAuth = FirebaseAuth.getInstance();
        setupGoogleSignIn();
        setupFacebookSignIn();
        setListeners();
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions);
    }

    private void setupFacebookSignIn() {
        callbackManager = CallbackManager.Factory.create();

        LoginManager.getInstance().registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                handleFacebookAccessToken(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {
                loading(false);
                showToast("Facebook sign in cancelled");
            }

            @Override
            public void onError(FacebookException error) {
                loading(false);
                showToast(error.getMessage());
            }
        });
    }

    private void setListeners() {
        binding.textCreateNewAccount.setOnClickListener(v ->
                startActivity(new Intent(getApplicationContext(), SignUpActivity.class)));

        binding.buttonSignIn.setOnClickListener(v -> {
            if (isValidSignInDetails()) {
                signIn();
            }
        });

        binding.buttonSignInWithGoogle.setOnClickListener(v -> {
            loading(true);
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });

        binding.buttonSignInWithFacebook.setOnClickListener(v -> {
            loading(true);
            LoginManager.getInstance().logInWithReadPermissions(
                    this,
                    Arrays.asList("email", "public_profile")
            );
        });
    }

    private void signIn() {
        loading(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_USERS)
                .whereEqualTo(Constants.KEY_EMAIL, binding.inputEmail.getText().toString().trim())
                .whereEqualTo(Constants.KEY_PASSWORD, binding.inputPassword.getText().toString().trim())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()
                            && task.getResult() != null
                            && task.getResult().getDocuments().size() > 0) {
                        DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
                        saveUserToPreference(
                                documentSnapshot.getId(),
                                documentSnapshot.getString(Constants.KEY_NAME),
                                documentSnapshot.getString(Constants.KEY_IMAGE)
                        );
                        goToMainActivity();
                    } else {
                        loading(false);
                        showToast("Unable to sign in");
                    }
                });
    }

    private void handleGoogleSignInResult(Intent data) {
        try {
            GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException.class);

            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            firebaseAuth.signInWithCredential(credential)
                    .addOnSuccessListener(authResult -> {
                        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            saveSocialUser(firebaseUser, "google");
                        } else {
                            loading(false);
                            showToast("Google sign in failed");
                        }
                    })
                    .addOnFailureListener(e -> {
                        loading(false);
                        showToast(e.getMessage());
                    });

        } catch (ApiException e) {
            loading(false);
            showToast("Google sign in failed: " + e.getStatusCode());
        }
    }

    private void handleFacebookAccessToken(AccessToken token) {
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());

        firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                    if (firebaseUser != null) {
                        saveSocialUser(firebaseUser, "facebook");
                    } else {
                        loading(false);
                        showToast("Facebook sign in failed");
                    }
                })
                .addOnFailureListener(e -> {
                    loading(false);
                    showToast(e.getMessage());
                });
    }

    private void saveSocialUser(FirebaseUser firebaseUser, String provider) {
        FirebaseFirestore database = FirebaseFirestore.getInstance();

        String name = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "User";
        String email = "facebook_user_" + firebaseUser.getUid().substring(0, 8) + "@facebook.login";

        database.collection(Constants.KEY_COLLECTION_USERS)
                .whereEqualTo(Constants.KEY_EMAIL, email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot documentSnapshot = queryDocumentSnapshots.getDocuments().get(0);
                        saveUserToPreference(
                                documentSnapshot.getId(),
                                documentSnapshot.getString(Constants.KEY_NAME),
                                documentSnapshot.getString(Constants.KEY_IMAGE)
                        );
                        goToMainActivity();
                    } else {
                        HashMap<String, Object> user = new HashMap<>();
                        user.put(Constants.KEY_NAME, name);
                        user.put(Constants.KEY_EMAIL, email);
                        user.put(Constants.KEY_PASSWORD, "");
                        user.put(Constants.KEY_IMAGE, "");
                        user.put(Constants.KEY_FCM_TOKEN, "");
                        user.put(Constants.KEY_AUTH_PROVIDER, provider);
                        user.put(Constants.KEY_AVAILABILITY, 1);

                        database.collection(Constants.KEY_COLLECTION_USERS)
                                .add(user)
                                .addOnSuccessListener(documentReference -> {
                                    saveUserToPreference(documentReference.getId(), name, "");
                                    goToMainActivity();
                                })
                                .addOnFailureListener(e -> {
                                    loading(false);
                                    showToast(e.getMessage());
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    loading(false);
                    showToast(e.getMessage());
                });
    }

    private void saveUserToPreference(String userId, String name, String image) {
        preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN, true);
        preferenceManager.putString(Constants.KEY_USER_ID, userId);
        preferenceManager.putString(Constants.KEY_NAME, name != null ? name : "");
        preferenceManager.putString(Constants.KEY_IMAGE, image != null ? image : "");
    }

    private void goToMainActivity() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loading(Boolean isLoading) {
        if (isLoading) {
            binding.buttonSignIn.setVisibility(View.INVISIBLE);
            binding.buttonSignInWithGoogle.setVisibility(View.INVISIBLE);
            binding.buttonSignInWithFacebook.setVisibility(View.INVISIBLE);
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.INVISIBLE);
            binding.buttonSignIn.setVisibility(View.VISIBLE);
            binding.buttonSignInWithGoogle.setVisibility(View.VISIBLE);
            binding.buttonSignInWithFacebook.setVisibility(View.VISIBLE);
        }
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private Boolean isValidSignInDetails() {
        if (binding.inputEmail.getText().toString().trim().isEmpty()) {
            showToast("Enter email");
            return false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(binding.inputEmail.getText().toString().trim()).matches()) {
            showToast("Enter valid email");
            return false;
        } else if (binding.inputPassword.getText().toString().trim().isEmpty()) {
            showToast("Enter password");
            return false;
        } else {
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (callbackManager != null) {
            callbackManager.onActivityResult(requestCode, resultCode, data);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    }
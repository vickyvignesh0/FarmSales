package com.example.farmsales;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.hbb20.CountryCodePicker;
import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_ID = 44;
    private final int RC_SIGN_IN = 1;
    GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private final String TAG = "MainActivity";
    private EditText edtPhone;
    private ArrayList<String> product_data = new ArrayList<String>();
    private ArrayList<String> product_name = new ArrayList<String>();
    private final ArrayList<String> product_quantity = new ArrayList<String>();
    private ArrayList<String> product_price = new ArrayList<String>();
    private CountryCodePicker country_code;
    private CallbackManager mCallbackManager;
    public FusedLocationProviderClient mFusedLocationClient;
    private double latitude,longitude;
    private String city;
    private Geocoder geocoder;
    private List<Address> addressList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        edtPhone = findViewById(R.id.phone_num_view);
        Button sendOTP = findViewById(R.id.otp_btn);
        Button loginButton = findViewById(R.id.facebook_btn);
        Button email = findViewById(R.id.login_btn);
        country_code = findViewById(R.id.countryCodePicker);
        Button google = findViewById(R.id.google_btn);
        mAuth = FirebaseAuth.getInstance();

//        via phone
        sendOTP.setOnClickListener(v -> {
            if (edtPhone.getText().toString().length() != 10) {
                Toast.makeText(MainActivity.this, "Please enter a valid phone number.", Toast.LENGTH_SHORT).show();
            } else {
                String Phone_number = "+" + country_code.getSelectedCountryCode() + edtPhone.getText().toString();
                Intent in = new Intent(MainActivity.this, Verify_otp.class);
                in.putExtra("phone", Phone_number);
                startActivity(in);
            }
        });
//        via google
        google.setOnClickListener(v -> {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();

            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
            signIn();
        });

//        via facebook
        FacebookSdk.sdkInitialize(getApplicationContext());
        mCallbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                handleFacebookToken(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {

            }

            @Override
            public void onError(FacebookException error) {
                Toast.makeText(MainActivity.this, "LogIn Failed", Toast.LENGTH_SHORT).show();
                System.out.println(error.getMessage());
            }
        });

        loginButton.setOnClickListener(v -> {
            LoginManager.getInstance().logIn(this,
                    Arrays.asList("email", "user_birthday", "public_profile")
            );
        });
        email.setOnClickListener(v -> {
            Intent signUpIntent = new Intent(this, EmailLogin.class);
            startActivity(signUpIntent);
        });

    }

    private void handleFacebookToken(AccessToken accessToken) {
        AuthCredential credential = FacebookAuthProvider.getCredential(accessToken.getToken());
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                updateUI(mAuth.getCurrentUser());
                Toast.makeText(MainActivity.this, "Logged In Successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "LogIn Failed", Toast.LENGTH_SHORT).show();
                System.out.println(task.getException().getMessage());
            }
        });
    }

    //for Google Sign In
    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            if (user.getDisplayName() != null) {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                db.collection("users").document(user.getUid()).addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(@Nullable @org.jetbrains.annotations.Nullable DocumentSnapshot value, @Nullable @org.jetbrains.annotations.Nullable FirebaseFirestoreException error) {
                        if(value.toString().contains("Farmer")){
                            Intent intent = new Intent(MainActivity.this, FarmerActivity.class);
                            Toast.makeText(MainActivity.this, "Signed In as " + user.getDisplayName(), Toast.LENGTH_SHORT).show();
                            startActivity(intent);
                        }else{
                            Intent intent = new Intent(MainActivity.this, Home_page.class);
                            Toast.makeText(MainActivity.this, "Signed In as " + user.getDisplayName(), Toast.LENGTH_SHORT).show();
                            startActivity(intent);
                        }
                    }
                });

            } else if (user.getPhoneNumber() != null) {
                Intent signUpIntent = new Intent(this, SignUp.class);
                signUpIntent.putExtra("phone", user.getPhoneNumber());
                startActivity(signUpIntent);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInTask(task);
        }
    }

    private void handleSignInTask(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            FirebaseGoogleAuth(account);
        } catch (ApiException e) {
            Toast.makeText(MainActivity.this, "Sign In Cancelled", Toast.LENGTH_SHORT).show();
        }
    }

    private void FirebaseGoogleAuth(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                updateUI(user);
            } else {
                updateUI(null);
            }
        });
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("users").document(mAuth.getCurrentUser().getUid()).get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    if (task.getResult().exists()) {
                        Intent intent = new Intent(this, Home_page.class);
                        Toast.makeText(this, "Signed In as " + user.getDisplayName(), Toast.LENGTH_SHORT).show();
                        startActivity(intent);
                    } else {
                        Intent intent = new Intent(MainActivity.this, SignUp.class);
                        intent.putExtra("phone", user.getPhoneNumber());
                        intent.putExtra("mail", user.getEmail());
                        intent.putExtra("name", user.getDisplayName());
                        startActivity(intent);
                        finish();
                    }
                }
            });

        }
    }

}
package com.yashovardhan99.healersdiary.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.yashovardhan99.healersdiary.R;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Login extends AppCompatActivity {

    GoogleSignInClient mGoogleSignInClient;
    FirebaseAuth mAuth;
    final int GOOGLE_SIGN_IN_RC = 1;
    LinearLayout signInProgress;
    private FirebaseAnalytics mFirebaseAnalytics;
    Bundle params;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        params = new Bundle();
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        signInProgress = findViewById(R.id.signInProgress);

        //configuring google sign in
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
         mGoogleSignInClient = GoogleSignIn.getClient(this,gso);
         mAuth = FirebaseAuth.getInstance();

         //when the Google sign in button is clicked
        SignInButton gsb = findViewById(R.id.GoogleSignInButton);
        gsb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent,GOOGLE_SIGN_IN_RC);
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        findViewById(R.id.GoogleSignInButton).setVisibility(View.GONE);
        signInProgress.setVisibility(View.VISIBLE);
        //adds the progress bar to not keep users waiting
        switch(requestCode){
            case GOOGLE_SIGN_IN_RC:
                try{
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                    //Google Sign In successful
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    //Now authenticate with firebase
                    FirebaseAuthWithGoogle(account);
                    Log.d("GOOGLE","SIGNED IN");
                }catch (Exception e){
                    Log.d("GOOGLE","SIGN IN FAILED");
                    signInProgress.setVisibility(View.INVISIBLE);
                    findViewById(R.id.GoogleSignInButton).setVisibility(View.VISIBLE);
                }
                break;
        }
    }
    void FirebaseAuthWithGoogle(GoogleSignInAccount account){
        //authenticate the google sign in with firebase
        Log.d("GOOGLE","FirebaseAuth ID = "+account.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(),null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if(task.isSuccessful()){
                            params.putString(FirebaseAnalytics.Param.METHOD,"Google");
                            signedIn();
                        }
                    }
                });
    }
    void signedIn()
    {
        //now the user is signed in
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        //create a user document in firestore
        Map<String,Object> user = new HashMap<>();
        user.put("uUd",mAuth.getUid());
        if(mAuth.getCurrentUser()!=null) {
            user.put("Name", mAuth.getCurrentUser().getDisplayName());
            user.put("Email", mAuth.getCurrentUser().getEmail());
            user.put("Phone", mAuth.getCurrentUser().getPhoneNumber());
        }
        DocumentReference userDoc = db.collection("users").document(Objects.requireNonNull(mAuth.getUid()));
        final boolean signup[] = {true};
        userDoc.get()
        .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    if (task.getResult().exists()) {
                        //repeat login
                        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, params);
                        signup[0] = false;
                    }
                    else {
                        //sign up
                        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, params);
                        signup[0] = true;
                    }
                }
            }});
        userDoc.set(user)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            if(signup[0])
                                Toast.makeText(Login.this, "Welcome " + mAuth.getCurrentUser().getDisplayName() + ". Tap the + button to get started!", Toast.LENGTH_LONG).show();
                            Log.d("FIRESTORE", "Added user document");
                        } else {
                            Log.d("FIRESTORE", task.getResult().toString() + " : " + Objects.requireNonNull(task.getException()).toString());
                            Toast.makeText(Login.this, "Some problem Occured. You have been logged in but some features may not work properly", Toast.LENGTH_LONG).show();
                        }
                    }
                });
        //now we can start the main activity
        Intent done = new Intent(this,MainActivity.class);
        done.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(done);
    }
}

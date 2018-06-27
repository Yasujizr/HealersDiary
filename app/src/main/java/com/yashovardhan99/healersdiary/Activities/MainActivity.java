package com.yashovardhan99.healersdiary.Activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.yashovardhan99.healersdiary.Adapters.MainListAdapter;
import com.yashovardhan99.healersdiary.Objects.Patient;
import com.yashovardhan99.healersdiary.R;

import java.util.ArrayList;

import javax.annotation.Nullable;

public class MainActivity extends AppCompatActivity {

    private RecyclerView.Adapter mAdapter;
    private ArrayList<Patient> patientList;
    Toolbar mainActivityToolbar;
    private FirebaseAnalytics mFirebaseAnalytics;
    FirebaseFirestore db;
    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        patientList = new ArrayList<>();

        mainActivityToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mainActivityToolbar);
        getSupportActionBar().setTitle(R.string.app_name);

        //check login and handle
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser mUser = mAuth.getCurrentUser();
        if(mUser==null) {//not signed in

            startActivity(new Intent(this, Login.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return;
        }

        //firestore
        db = FirebaseFirestore.getInstance();
        CollectionReference patients = db.collection("users")
                .document(mUser.getUid())
                .collection("patients");
        //to instantly make any changes reflect here
        patients.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                if(e!=null){
                    Log.d("FIRESTORE","ERROR : "+e.getMessage());
                    return;
                }
                Log.d("FIRESTORE","Data fetced");
                for(DocumentChange dc:queryDocumentSnapshots.getDocumentChanges()){
                    //getting changes in documents
                    Log.d("FIRESTORE",dc.getDocument().getData().toString());

                    switch (dc.getType()){

                        case ADDED:
                            //add new patient to arrayList
                            Patient patient = new Patient();
                            patient.name = dc.getDocument().get("Name").toString();
                            patient.uid = dc.getDocument().getId();
                            patientList.add(patient);
                            mAdapter.notifyItemInserted(patientList.indexOf(patient));
                            break;

                        case MODIFIED:
                            //modify patient name
                            String id = dc.getDocument().getId();
                            for(Patient patient1: patientList){
                                if(patient1.getUid().equals(id)){
                                    patient1.name = dc.getDocument().get("Name").toString();
                                    mAdapter.notifyItemChanged(patientList.indexOf(patient1));
                                    break;
                                }
                            }
                            break;
                        case REMOVED:
                            //remove patient record
                            String id2 = dc.getDocument().getId();
                            for(Patient patient1: patientList){
                                if(patient1.getUid().equals(id2)){
                                    int pos = patientList.indexOf(patient1);
                                    patientList.remove(patient1);
                                    mAdapter.notifyItemRemoved(pos);
                                    break;
                                }
                            }
                            break;
                    }
                }
            }
        });

        //Recycler view setup
        RecyclerView mRecyclerView;
        mRecyclerView = findViewById(R.id.recycler_main);
        mRecyclerView.setHasFixedSize(false);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new MainListAdapter(patientList);
        mRecyclerView.setAdapter(mAdapter);

        DividerItemDecoration itemLine = new DividerItemDecoration(mRecyclerView.getContext(), DividerItemDecoration.VERTICAL);
        mRecyclerView.addItemDecoration(itemLine);

        //new patient record button
        FloatingActionButton newPatientButton = findViewById(R.id.new_fab);
        newPatientButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this,NewPatient.class));
            }
        });
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        switch (item.getTitle().toString()){
            case "Edit":
                Snackbar.make(findViewById(R.id.recycler_main),"Not yet implemented",Snackbar.LENGTH_LONG).show();
                return true;
            case "Delete":
                final AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(MainActivity.this);
                confirmBuilder.setMessage("This will delete all patient records permanently. This action cannot be undone")
                        .setTitle("Are you sure?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                DeletePatientRecord(item.getGroupId());
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //action cancelled
                            }
                        });
                AlertDialog confirm = confirmBuilder.create();
                confirm.show();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }
    void DeletePatientRecord(int id){
        //now to delete this record, we first delete all healing and payment history of this patient
        final CollectionReference healings = db.collection("users")
                .document(mAuth.getUid())
                .collection("patients")
                .document(patientList.get(id).getUid())
                .collection("healings");
        healings.get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()){
                    for(QueryDocumentSnapshot documentSnapshot:task.getResult()){
                        documentSnapshot.getReference().delete();
                    }
                }
            }
        });

        CollectionReference payments = healings.getParent().collection("payments");
        payments.get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()){
                    for(QueryDocumentSnapshot documentSnapshot:task.getResult()){
                        documentSnapshot.getReference().delete();
                    }
                }
            }
        });

        //now deleting patient document
        DocumentReference patient = db.collection("users")
                .document(mAuth.getUid())
                .collection("patients")
                .document(patientList.get(id).getUid());
        patient.delete();
        Snackbar.make(findViewById(R.id.recycler_main),"Record Deleted",Snackbar.LENGTH_LONG).show();
    }
}

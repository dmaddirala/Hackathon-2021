package com.example.homeautomation_hackathonwifi;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String FAN_ON = "/FanOn";
    private static final String FAN_OFF = "/FanOff";
    private static final String LIGHT_ON = "/LightOn";
    private static final String LIGHT_OFF = "/LightOff";
    private static final String EVERYTHING_ON = "/EverythingOn";
    private static final String EVERYTHING_OFF = "/EverythingOff";


    private Button btnFan, btnLight;
    private String url = "http://192.168.31.201";
    private LinearLayout buttonsLayout;
    private ProgressBar progressBar;
    private TextView tvMessgae;
    private FloatingActionButton btnVoiceRecognition;
    private LottieAnimationView lottieAnimationView;
    private Switch online;

    private SpeechRecognizer mSpeechRecognizer;
    private Intent mSpeechRecognizerIntent;
    private int record_audio = 1;

    private boolean fanFlag=false, lightFlag = false;
    private Dialog dialogInfo;
    private TextView tvInfo;

    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference myRef = database.getReference("test");

    public static final String SHARED_PREFS = "sharedPrefs";
    public static final String URL = "URL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        incomingCalls();
        checkPermission();

        dialogInfo = new Dialog(MainActivity.this);
        dialogInfo.setContentView(R.layout.dialog_info);
        dialogInfo.getWindow().setBackgroundDrawable(getDrawable(R.drawable.background));
        dialogInfo.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialogInfo.setCancelable(true);
        tvInfo = dialogInfo.findViewById(R.id.tv_info);


        btnFan = findViewById(R.id.btn_fan);
        btnLight =  findViewById(R.id.btn_light);
        buttonsLayout = findViewById(R.id.buttons_layout);
        progressBar = findViewById(R.id.progress_bar);
        tvMessgae = findViewById(R.id.tv_message);
        btnVoiceRecognition = findViewById(R.id.fab_voice);
        lottieAnimationView = findViewById(R.id.lottie_animation);
        online = findViewById(R.id.switch_indicator);

        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        mSpeechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognition();

        btnFan.setOnClickListener(view -> {
            if (online.isChecked()){
                if(fanFlag){
                    onlineSwitch(FAN_ON);
                }else{
                    onlineSwitch(FAN_OFF);
                }

            }else{
                if(fanFlag){
                    getHttpRequest(url + FAN_ON);
                }else{
                    getHttpRequest(url + FAN_OFF);
                }
                fanFlag = !fanFlag;
            }
        });

        btnLight.setOnClickListener(view -> {
            if (online.isChecked()){
                if(lightFlag){
                    onlineSwitch(LIGHT_ON);
                }else{
                    onlineSwitch(LIGHT_OFF);
                }
            }else{
                if(lightFlag){
                    getHttpRequest(url + LIGHT_ON);
                }else{
                    getHttpRequest(url + LIGHT_OFF);
                }
                lightFlag = !lightFlag;
            }


        });

        btnVoiceRecognition.setOnTouchListener((v, event) -> {

            switch (event.getAction()){

                case MotionEvent.ACTION_DOWN:
                    lottieAnimationView.setVisibility(View.VISIBLE);
                    buttonsLayout.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    lottieAnimationView.playAnimation();
                    tvMessgae.setText("");
                    mSpeechRecognizer.startListening(mSpeechRecognizerIntent);
                    break;

                case MotionEvent.ACTION_UP:
                    mSpeechRecognizer.stopListening();
                    lottieAnimationView.setVisibility(View.GONE);
                    buttonsLayout.setVisibility(View.VISIBLE);
                    lottieAnimationView.cancelAnimation();
                    break;

            }
            return false;
        });

        online.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            if(isChecked){
                stopLoading();
                Toast.makeText(this, "Going Online..", Toast.LENGTH_SHORT).show();
                online.setText("ONLINE");
            }else{
                getHttpRequest(url);
                Toast.makeText(this, "Going Offline..", Toast.LENGTH_SHORT).show();
                online.setText("OFFLINE");
            }
        });
    }

    private void incomingCalls(){
        myRef.child("IP").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                url = "http://" + dataSnapshot.getValue(String.class);
                getHttpRequest(url);
                Log.i("TAG", "Firebase Success :" + url);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                getHttpRequest(url);
            }
        });
    }

    private void onlineSwitch(String query){

        if(!isNetworkAvailable()){
            Toast.makeText(this, "Internet not available.", Toast.LENGTH_LONG).show();
            return ;
        }

        if (query==FAN_ON){
            myRef.child("Fan").setValue( 1 );
            fanFlag = false;
        }
        else if (query==FAN_OFF){
            myRef.child("Fan").setValue( 0 );
            fanFlag = true;
        }
        else if (query==LIGHT_ON){
            myRef.child("Light").setValue( 1 );
            lightFlag = false;
        }
        else if (query==LIGHT_OFF){
            myRef.child("Light").setValue( 0 );
            lightFlag = true;
        }
        else if (query==EVERYTHING_ON){ myRef.child("Everything").setValue( 1 ); }
        else if (query==EVERYTHING_OFF){ myRef.child("Everything").setValue( 0 ); }

    }

    private void startLoading(){
        buttonsLayout.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
    }

    private void stopLoading(){
        progressBar.setVisibility(View.GONE);
        buttonsLayout.setVisibility(View.VISIBLE);
    }

    private void disableAllViews(){
        progressBar.setVisibility(View.GONE);
        buttonsLayout.setVisibility(View.GONE);
        lottieAnimationView.setVisibility(View.GONE);
        btnVoiceRecognition.setVisibility(View.GONE);
    }

    private void enableAllViews(){
        buttonsLayout.setVisibility(View.VISIBLE);
        btnVoiceRecognition.setVisibility(View.VISIBLE);
    }

    private void getHttpRequest(String url1) {
        startLoading();
        RequestQueue queue = Volley.newRequestQueue(MainActivity.this);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url1, response -> {
            //mTextView.setText(response);

            stopLoading();
            if (response.equals("Hello World!!")) {
                Toast.makeText(MainActivity.this, "Ready..!", Toast.LENGTH_SHORT).show();
            } else if (response.equals("FAN")) {
                Toast.makeText(MainActivity.this, "FAN Switched", Toast.LENGTH_SHORT).show();
            } else if (response.equals("LIGHT")) {
                Toast.makeText(MainActivity.this, "LIGHT Switched", Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(MainActivity.this, ""+response, Toast.LENGTH_SHORT).show();
            }


        }, error -> {
            stopLoading();
            Toast.makeText(MainActivity.this, "Change Your WiFi Network", Toast.LENGTH_LONG).show();

        });
        queue.add(stringRequest);
    }

    //Saves the URL Locally
    public void saveLocalData(String url_this) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString(URL, url_this);
        editor.apply();

    }

    //Loads the Locally saved URL
    public void loadLocalData() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        url = sharedPreferences.getString(URL, "http://192.168.31.201");

    }

    private void checkPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){

            if((ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED)){
                if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO))
                {
                    Toast.makeText(this, "Please grant permission to record audio", Toast.LENGTH_SHORT).show();

                    ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO},
                            record_audio);
                } else{
                    ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO},
                            record_audio);
                }

//                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
//                startActivity(intent);
//                finish();
            }
        }
    }

    private void detectCommand(String command){
        String temp = new String(command);
        String query = "";
        Log.i("TAG", "Light Command: "+ temp.contains("light") + "");
        if(temp.contains("light") && temp.contains("on")){
            Log.i("TAG", "COMMAND: Lights ON");
            query = "/LightOn";
        }else if(temp.contains("light") && temp.contains("off")){
            Log.i("TAG", "COMMAND: Lights OFF");
            query = "/LightOff";
        }
        else if(temp.contains("fan") && temp.contains("on")){
            Log.i("TAG", "COMMAND: Fan ON");
            query = "/FanOn";
        }else if(temp.contains("fan") && temp.contains("off")){
            Log.i("TAG", "COMMAND: Fan OFF");
            query = "/FanOff";
        } else if(temp.contains("everything") && temp.contains("on")){
            Log.i("TAG", "COMMAND: Everything OFF");
            query = "/EverythingOn";
        }else if(temp.contains("everything") && temp.contains("off")){
            Log.i("TAG", "COMMAND: Everything OFF");
            query = "/EverythingOff";
        }else{
            return;
        }
        if(online.isChecked()){
            onlineSwitch(query);

        }else{
            getHttpRequest(url + query);
        }

    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void recognition(){
        mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> collection = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String info = "";
                if (collection != null){
                    info = collection.get(0);
                    tvMessgae.setText(info);
                }
                disableAllViews();
                dialogInfo.show();
                tvInfo.setText(info + "");
                String finalInfo = info;
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //Do something after 100ms
                        enableAllViews();
                        dialogInfo.dismiss();
                        detectCommand(finalInfo);
                    }
                }, 1200);
            }
            @Override
            public void onReadyForSpeech(Bundle params) { }

            @Override
            public void onBeginningOfSpeech() { }

            @Override
            public void onRmsChanged(float rmsdB) { }

            @Override
            public void onBufferReceived(byte[] buffer) { }

            @Override
            public void onEndOfSpeech() { }

            @Override
            public void onError(int error) {
                Toast.makeText(MainActivity.this, "Couldn't recognize..", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPartialResults(Bundle partialResults) { }

            @Override
            public void onEvent(int eventType, Bundle params) { }

        });
    }
}
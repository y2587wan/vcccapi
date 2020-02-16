package com.canon.ccapisample;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.speech.RecognizerIntent;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.DateTimeException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.canon.ccapisample.Constants.CCAPI.Method.GET;


public class MainActivity extends AppCompatActivity implements View.OnClickListener, AuthenticateListener, WebAPIResultListener, DisconnectListener,
        TextToSpeech.OnInitListener{
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CODE = 1;
    private Spinner mEventMethodSpinner;
    private WifiMonitoringThread mWifiMonitoringThread;
    private Handler mHandler;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private void createSpeechIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
        startActivityForResult(intent, 30);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Access permission of the external storage.
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // Request permission, if not permitted by the user.
            ActivityCompat.requestPermissions(
                    this,
                    new String[] {
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        mHandler = new Handler();

        setContentView(R.layout.activity_main);
        mEventMethodSpinner = (Spinner) findViewById(R.id.EventSpinner);
        mEventMethodSpinner.setSelection(1); // polling(continue)
        mEventMethodSpinner.setVisibility(View.INVISIBLE);
        findViewById(R.id.DiscoveryButton).setOnClickListener(this);
        findViewById(R.id.DisconnectButton).setOnClickListener(this);
        findViewById(R.id.ToRemoteCaptureButton).setOnClickListener(this);
        Button deviceInfo = findViewById(R.id.ToDeviceInformationButton); //.setOnClickListener(this);
        deviceInfo.setVisibility(View.INVISIBLE);
        Button cameraInfo = findViewById(R.id.ToCameraSettingButton);//.setOnClickListener(this);
        cameraInfo.setVisibility(View.INVISIBLE);
        findViewById(R.id.ToContentsViewerButton).setOnClickListener(this);
        EditText editText = findViewById(R.id.inputView);
        Button voiceButton = findViewById(R.id.VoiceButton);
        voiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createSpeechIntent();
            }
        });
        createSpeechIntent();
    }

    String mAnswer = "";

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if(mWifiMonitoringThread != null) {
            mWifiMonitoringThread.interrupt();
        }
    }
    String prefer = "192.168.1.234";
    @Override
    public void onClick(View v){
        if (v != null) {
            switch (v.getId()) {
                case R.id.DiscoveryButton: {
                    Button discovery = (Button) findViewById(R.id.DiscoveryButton);
                    EditText editText = findViewById(R.id.inputView);
                    discovery.setEnabled(false);
                    WifiConnection wifiConnection = new WifiConnection(editText.getText().toString());
                    asyncConnect(wifiConnection);
                    break;
                }
                case R.id.DisconnectButton: {
                    WebAPI.getInstance().setListener(this, this);
                    WebAPI.getInstance().enqueueRequest(new WebAPIQueueDataSet(Constants.RequestCode.POST_FUNCTIONS_NETWORKCONNECTION, null, new WebAPIResultListener() {
                        @Override
                        public void onWebAPIResult(WebAPIResultDataSet result) {
                            Context context = getApplicationContext();
                            if (context != null) {
                                if (result.isError()) {
                                    Toast.makeText(context, result.getErrorMsg(), Toast.LENGTH_SHORT).show();
                                }
                                else{
                                    Toast.makeText(context, "Disconnect Accepted.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }));
                    break;
                }
                case R.id.ToRemoteCaptureButton: {
                    moveToSubActivity(SubActivity.Screen.RemoteCapture);
                    break;
                }
                case R.id.ToDeviceInformationButton: {
                    moveToSubActivity(SubActivity.Screen.DeviceInformation);
                    break;
                }
                case R.id.ToCameraSettingButton: {
                    moveToSubActivity(SubActivity.Screen.CameraFunctions);
                    break;
                }
                case R.id.ToContentsViewerButton: {
                    moveToSubActivity(SubActivity.Screen.ContentsViewer);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private void setConnectionState(boolean isConnect, String message){
        Button discovery = (Button) findViewById(R.id.DiscoveryButton);
        Button disconnect = (Button) findViewById(R.id.DisconnectButton);

        if ( isConnect ) {
            discovery.setVisibility(View.GONE);
            disconnect.setVisibility(View.VISIBLE);
        }
        else{
            discovery.setVisibility(View.VISIBLE);
            disconnect.setVisibility(View.GONE);
        }

        findViewById(R.id.ToRemoteCaptureButton).setEnabled(isConnect);
        findViewById(R.id.ToDeviceInformationButton).setEnabled(isConnect);
        findViewById(R.id.ToCameraSettingButton).setEnabled(isConnect);
        findViewById(R.id.ToContentsViewerButton).setEnabled(isConnect);

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void asyncConnect(final WifiConnection wifiConnection){
        AsyncTask<Void, Void, String> asyncTask = new AsyncTask<Void, Void, String>(){
            @Override
            protected String doInBackground(Void... params) {
                return wifiConnection.execute();
            }

            @Override
            protected void onPostExecute(String result){
                callbackResult(result);
            }
        };
        asyncTask.execute();
    }

    public void callbackResult(String url){
        Button discovery = (Button) findViewById(R.id.DiscoveryButton);
        discovery.setEnabled(true);

        if(url != null) {
            Log.d(TAG, "Connect Success.");
            Log.d(TAG, url);

            WebAPI.getInstance().start(url);
            WebAPI.getInstance().setListener(this, this);

            // Get all APIs.
            // Generate the API list and update the screen, if they are got.
            Bundle args = new Bundle();
            String[] params = new String[]{GET, url, null};
            args.putStringArray(Constants.RequestCode.ACT_WEB_API.name(), params);
            WebAPI.getInstance().enqueueRequest(new WebAPIQueueDataSet(Constants.RequestCode.ACT_WEB_API, args, this));
        }
        else{
            Toast.makeText(this, "Connect Failed.", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Connect Failed.");
        }
    }

    private void moveToSubActivity(SubActivity.Screen screen){
        String event = mEventMethodSpinner.getSelectedItem().toString();
        Constants.EventMethod method;

        switch (event) {
            case "monitoring":
                method = Constants.EventMethod.MONITORING;
                break;
            case "polling(continue)":
                method = Constants.EventMethod.POLLING_CONTINUE;
                break;
            case "polling":
            default:
                method = Constants.EventMethod.POLLING;
                break;
        }

        // End the monitoring of the Wi-Fi connection.
        if(mWifiMonitoringThread != null) {
            mWifiMonitoringThread.interrupt();
        }


        Intent intent = new Intent(this, SubActivity.class);
        intent.putExtra(SubActivity.SCREEN, screen);
        intent.putExtra(SubActivity.EVENT_METHOD, method);
        startActivityForResult(intent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult");
        switch (requestCode) {
            case (REQUEST_CODE):
                // When returning from the SubActivity by the Wi-Fi disconnection.
                if (resultCode == RESULT_OK) {
                    Bundle bundle = data.getExtras();
                    String message = bundle.getString(SubActivity.MESSAGE);
                    Boolean isDisconnection = bundle.getBoolean(SubActivity.IS_DISCONNECTION);
                    if (message != null) {
                        onNotifyDisconnect(message, isDisconnection);
                    }
                }
                // When returning from the SubActivity using the return key.
                else if(resultCode == RESULT_CANCELED){
                    startWifiMonitoringThread();
                }
                break;
            case(30):
                if (resultCode == RESULT_OK) {
                    List<String> results = data.getStringArrayListExtra
                            (RecognizerIntent.EXTRA_RESULTS);
                    mAnswer = results.get(0).toLowerCase();
                    if(mAnswer.contains("discovery")) {
                        Button discovery = (Button) findViewById(R.id.DiscoveryButton);
                        discovery.setEnabled(false);
                        EditText editText = findViewById(R.id.inputView);
                        WifiConnection wifiConnection = new WifiConnection(editText.getText().toString());
                        asyncConnect(wifiConnection);
                        createSpeechIntent();
                    } else if (mAnswer.contains("disconnect")) {
                        WebAPI.getInstance().setListener(this, this);
                        WebAPI.getInstance().enqueueRequest(new WebAPIQueueDataSet(Constants.RequestCode.POST_FUNCTIONS_NETWORKCONNECTION, null, new WebAPIResultListener() {
                            @Override
                            public void onWebAPIResult(WebAPIResultDataSet result) {
                                Context context = getApplicationContext();
                                if (context != null) {
                                    if (result.isError()) {
                                        Toast.makeText(context, result.getErrorMsg(), Toast.LENGTH_SHORT).show();
                                    }
                                    else{
                                        Toast.makeText(context, "Disconnect Accepted.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }
                        }));
                        createSpeechIntent();
                    } else if (mAnswer.contains("remote")) {
                        moveToSubActivity(SubActivity.Screen.RemoteCapture);
                    } else if (mAnswer.contains("information")) {
                        moveToSubActivity(SubActivity.Screen.DeviceInformation);
                    } else if (mAnswer.contains("setting")) {
                        moveToSubActivity(SubActivity.Screen.CameraFunctions);
                    } else if (mAnswer.contains("content")) {
                        moveToSubActivity(SubActivity.Screen.ContentsViewer);
                    } else {
                        Button discovery = findViewById(R.id.DiscoveryButton);
                        discovery.setEnabled(false);
                        EditText editText = findViewById(R.id.inputView);
                        editText.getText().clear();
                        for (char c : results.get(0).toCharArray())
                            editText.getText().append(c);
                        WifiConnection wifiConnection = new WifiConnection(editText.getText().toString());
                        asyncConnect(wifiConnection);
                        createSpeechIntent();
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onWebAPIResult(WebAPIResultDataSet result) {
        if(!result.isError()){
            WebAPI.getInstance().setAPIDataList(result.getResponseBody());
            setConnectionState(true, "Connect Success.");
            startWifiMonitoringThread();
        }
        else{
            Toast.makeText(this, "Connect Failed.\n" + result.getErrorMsg(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void showAuthDialog(DialogFragment dialogFragment, DialogInterface.OnDismissListener onDismissListener) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        dialogFragment.show(fragmentManager, "Authentication Required");
        fragmentManager.executePendingTransactions();
        dialogFragment.getDialog().setOnDismissListener(onDismissListener);
    }

    @Override
    public void onNotifyDisconnect(final String message, final Boolean isDisconnection) {
        Log.d(TAG, "onNotifyDisconnect");
        if(isDisconnection) {
            // Clear digest authentication information when the wireless connection disconnected.
            WebAPI.getInstance().clearDigestAuthInfo();
        }
        else{
            startWifiMonitoringThread();
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                setConnectionState(!isDisconnection, message);
            }
        });
    }

    void startWifiMonitoringThread(){
        try {
            URL url = new URL(WebAPI.getInstance().getUrl());
            mWifiMonitoringThread = new WifiMonitoringThread(url.getHost(), this);
            mWifiMonitoringThread.start();
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
            Log.d(TAG, "WifiMonitoringThread cannot start.");
        }
    }

    @Override
    public void onInit(int i) {

    }
}

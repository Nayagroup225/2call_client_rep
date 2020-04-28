package com.block.client;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.block.client.model.BaseResponse;
import com.block.client.preferences.ITelephony;
import com.block.client.preferences.ProgressHelper;
import com.block.client.retrofit.ApiCall;
import com.block.client.retrofit.IApiCallback;
import com.block.client.retrofit.RestClient;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.iid.FirebaseInstanceId;
import com.koushikdutta.ion.Ion;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.Voice;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;

import retrofit2.Response;

import static android.Manifest.permission.READ_PHONE_NUMBERS;

public class VoiceActivity extends AppCompatActivity implements IApiCallback<BaseResponse>, Handler.Callback{

    private static final String TAG = "VoiceActivity";
    private static String identity = null;
    public static final String adminIdentify = "antipolice204";
    /*
     * You must provide the URL to the publicly accessible Twilio access token server route
     *
     * For example: https://myurl.io/accessToken
     *
     * If your token server is written in PHP, TWILIO_ACCESS_TOKEN_SERVER_URL needs .php extension at the end.
     *
     * For example : https://myurl.io/accessToken.php
     */
    private static String TWILIO_ACCESS_TOKEN_SERVER_URL = RestClient.BASE_URL+"accessToken";

    private static final int MIC_PERMISSION_REQUEST_CODE = 1;

    private String accessToken;
    private AudioManager audioManager;
    private int savedAudioMode = AudioManager.MODE_INVALID;

    private boolean isReceiverRegistered = false;
    private VoiceBroadcastReceiver voiceBroadcastReceiver;

    // Empty HashMap, never populated for the Quickstart
    HashMap<String, String> params = new HashMap<>();

    private RelativeLayout coordinatorLayout;
    private View speakerActionFab;
    private Chronometer chronometer;

    private NotificationManager notificationManager;
    private AlertDialog alertDialog;
    private CallInvite activeCallInvite;
    private Call activeCall;
    private int activeCallNotificationId;

    RegistrationListener registrationListener = registrationListener();
    Call.Listener callListener = callListener();

    View incomingView, talkingView;
    ImageView firstScreen;
    String mPhoneNumber = "1", mDeviceId = "0";
    TextView tvIdentity;
    Handler mHandler;

    SoundPoolManager mSoundManager;

    private PhoneCallStateReceiver mCallreceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice);
//        tvIdentity = findViewById(R.id.tv_identity);
//        tvIdentity.setText(adminIdentify);
        // These flags ensure that the activity can be launched when the screen is locked.
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(getResources().getColor(R.color.color3));

        mSoundManager = new SoundPoolManager(this);

        incomingView = findViewById(R.id.incoming_layout);
        talkingView = findViewById(R.id.talking_layout);
        firstScreen = findViewById(R.id.first_screen);
        mHandler = new Handler(this);

        coordinatorLayout = findViewById(R.id.coordinator_layout);
        chronometer = findViewById(R.id.chronometer);
        speakerActionFab = findViewById(R.id.speaker_menu_item);

        speakerActionFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setSpeaker();
            }
        });

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        /*
         * Setup the broadcast receiver to be notified of FCM Token updates
         * or incoming call invite in this Activity.
         */
        voiceBroadcastReceiver = new VoiceBroadcastReceiver();
        registerReceiver();

        /*
         * Needed for setting/abandoning audio focus during a call
         */
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);

        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        /*
         * Setup the UI
         */
        resetUI();

        /*
         * Displays a call dialog if the intent contains a call invite
         */
        if(getIntent().getAction().equals("android.intent.action.MAIN")) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (!checkPermissionForMicrophone() || !checkPermissionForPhoneNumber() || !checkPermissionForPhoneState()) {
                    requestPermission();
                } else {
                    getDeviceId();
                }
            }else{
                getDeviceId();
            }

        }else{
            handleIncomingCallIntent(getIntent());
        }

        /*
         * Ensure the microphone permission is enabled
         */
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingCallIntent(intent);
    }

    private RegistrationListener registrationListener() {
        return new RegistrationListener() {
            @Override
            public void onRegistered(@NonNull String accessToken, @NonNull String fcmToken) {
                Log.d(TAG, "Successfully registered FCM " + fcmToken);
            }

            @Override
            public void onError(@NonNull RegistrationException error,
                                @NonNull String accessToken,
                                @NonNull String fcmToken) {
                String message = String.format(
                        Locale.US,
                        "Registration Error: %d, %s",
                        error.getErrorCode(),
                        error.getMessage());
                Log.e(TAG, message);
                Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show();
            }
        };
    }

    private Call.Listener callListener() {
        return new Call.Listener() {
            /*
             * This callback is emitted once before the Call.Listener.onConnected() callback when
             * the callee is being alerted of a Call. The behavior of this callback is determined by
             * the answerOnBridge flag provided in the Dial verb of your TwiML application
             * associated with this client. If the answerOnBridge flag is false, which is the
             * default, the Call.Listener.onConnected() callback will be emitted immediately after
             * Call.Listener.onRinging(). If the answerOnBridge flag is true, this will cause the
             * call to emit the onConnected callback only after the call is answered.
             * See answeronbridge for more details on how to use it with the Dial TwiML verb. If the
             * twiML response contains a Say verb, then the call will emit the
             * Call.Listener.onConnected callback immediately after Call.Listener.onRinging() is
             * raised, irrespective of the value of answerOnBridge being set to true or false
             */
            @Override
            public void onRinging(@NonNull Call call) {
                Log.d(TAG, "Ringing");
                /*
                 * When [answerOnBridge](https://www.twilio.com/docs/voice/twiml/dial#answeronbridge)
                 * is enabled in the <Dial> TwiML verb, the caller will not hear the ringback while
                 * the call is ringing and awaiting to be accepted on the callee's side. The application
                 * can use the `SoundPoolManager` to play custom audio files between the
                 * `Call.Listener.onRinging()` and the `Call.Listener.onConnected()` callbacks.
                 */
                if (BuildConfig.playCustomRingback) {
                    mSoundManager.playRinging();
                }
            }

            @Override
            public void onConnectFailure(@NonNull Call call, @NonNull CallException error) {
                setAudioFocus(false);
                if (BuildConfig.playCustomRingback) {
                    mSoundManager.stopRinging();
                }
                Log.d(TAG, "Connect failure");
                String message = String.format(
                        Locale.US,
                        "Call Error: %d, %s",
                        error.getErrorCode(),
                        error.getMessage());
                Log.e(TAG, message);
                Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show();
                resetUI();
                finish();
            }

            @Override
            public void onConnected(@NonNull Call call) {
                setAudioFocus(true);
                if (BuildConfig.playCustomRingback) {
                    mSoundManager.stopRinging();
                }
                Log.d(TAG, "Connected");
                activeCall = call;
            }

            @Override
            public void onReconnecting(@NonNull Call call, @NonNull CallException callException) {
                Log.d(TAG, "onReconnecting");
            }

            @Override
            public void onReconnected(@NonNull Call call) {
                Log.d(TAG, "onReconnected");
            }

            @Override
            public void onDisconnected(@NonNull Call call, CallException error) {
                setAudioFocus(false);
                if (BuildConfig.playCustomRingback) {
                    mSoundManager.stopRinging();
                }
                Log.d(TAG, "Disconnected");
                if (error != null) {
                    String message = String.format(
                            Locale.US,
                            "Call Error: %d, %s",
                            error.getErrorCode(),
                            error.getMessage());
                    Log.e(TAG, message);
                    Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show();
                }
                resetUI();
                finish();
            }
        };
    }

    /*
     * The UI state when there is an active call
     */
    private void setCallUI() {
        chronometer.setVisibility(View.VISIBLE);
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.start();
    }

    /*
     * Reset UI elements
     */
    private void resetUI() {
        incomingView.setVisibility(View.INVISIBLE);
        chronometer.setVisibility(View.INVISIBLE);
        chronometer.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver();
        this.unregisterReceiver(this.mCallreceiver);
    }

    @Override
    public void onDestroy() {
        mSoundManager.release();
        super.onDestroy();
    }

    private void handleIncomingCallIntent(Intent intent) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            activeCallInvite = intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE);
            activeCallNotificationId = intent.getIntExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, 0);

            switch (action) {
                case Constants.ACTION_INCOMING_CALL:
                    handleIncomingCall();
                    break;
                case Constants.ACTION_INCOMING_CALL_NOTIFICATION:
                    showIncomingCallView();
                    break;
                case Constants.ACTION_CANCEL_CALL:
                    handleCancel();
                    break;
                case Constants.ACTION_FCM_TOKEN:
                    if (identity!=null) {
                        retrieveAccessToken(identity);
                    }else{
                        getDeviceId();
                    }
                    break;
                case Constants.ACTION_ACCEPT:
                    answer();
                    break;
                default:
                    break;
            }
        }
    }

    private void handleIncomingCall() {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            showIncomingCallView();
//        } else {
//            if (isAppVisible()) {
//                showIncomingCallView();
//            }
//        }
    }

    private void handleCancel() {
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.cancel();
        }
        mSoundManager.stopRinging();
        incomingView.setVisibility(View.INVISIBLE);
        finish();
    }

    private void registerReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Constants.ACTION_INCOMING_CALL);
            intentFilter.addAction(Constants.ACTION_CANCEL_CALL);
            intentFilter.addAction(Constants.ACTION_FCM_TOKEN);
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    voiceBroadcastReceiver, intentFilter);
            isReceiverRegistered = true;
        }

        this.mCallreceiver = new PhoneCallStateReceiver();
        this.registerReceiver(this.mCallreceiver,new IntentFilter("android.intent.action.PHONE_STATE"));

    }

    private void unregisterReceiver() {
        if (isReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(voiceBroadcastReceiver);
            isReceiverRegistered = false;
        }
    }

    private class VoiceBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && (action.equals(Constants.ACTION_INCOMING_CALL) || action.equals(Constants.ACTION_CANCEL_CALL))) {
                /*
                 * Handle the incoming or cancelled call invite
                 */
                handleIncomingCallIntent(intent);
            }
        }
    }

    private void answerCallClickListener() {
//        return (dialog, which) -> {
            Log.d(TAG, "Clicked accept");
            Intent acceptIntent = new Intent(getApplicationContext(), IncomingCallNotificationService.class);
            acceptIntent.setAction(Constants.ACTION_ACCEPT);
            acceptIntent.putExtra(Constants.INCOMING_CALL_INVITE, activeCallInvite);
            acceptIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, activeCallNotificationId);
            Log.d(TAG, "Clicked accept startService");
            startService(acceptIntent);
//        };
    }

    private void declineCall() {
//        return (dialogInterface, i) -> {
            mSoundManager.stopRinging();
            if (activeCallInvite != null) {
                Intent intent = new Intent(VoiceActivity.this, IncomingCallNotificationService.class);
                intent.setAction(Constants.ACTION_REJECT);
                intent.putExtra(Constants.INCOMING_CALL_INVITE, activeCallInvite);
                startService(intent);
            }
            if (alertDialog != null && alertDialog.isShowing()) {
                alertDialog.dismiss();
            }
            disconnect();
            resetUI();
            finish();
//        };
    }

    private DialogInterface.OnClickListener cancelCallClickListener() {
        return (dialogInterface, i) -> {
            mSoundManager.stopRinging();
            if (activeCallInvite != null) {
                Intent intent = new Intent(VoiceActivity.this, IncomingCallNotificationService.class);
                intent.setAction(Constants.ACTION_REJECT);
                intent.putExtra(Constants.INCOMING_CALL_INVITE, activeCallInvite);
                startService(intent);
            }
            if (alertDialog != null && alertDialog.isShowing()) {
                alertDialog.dismiss();
                incomingView.setVisibility(View.INVISIBLE);
            }
        };
    }

    /*
     * Register your FCM token with Twilio to receive incoming call invites
     */
    private void registerForCallInvites() {
        FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(this, instanceIdResult -> {
            String fcmToken = instanceIdResult.getToken();
            Log.i(TAG, "Registering with FCM");
            Voice.register(accessToken, Voice.RegistrationChannel.FCM, fcmToken, registrationListener);
        });
    }

    /*
     * Accept an incoming Call
     */
    private void answer() {
        mSoundManager.stopRinging();
        activeCallInvite.accept(this, callListener);
        notificationManager.cancel(activeCallNotificationId);
        setCallUI();
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
    }

    /*
     * Disconnect from Call
     */
    private void disconnect() {
        if (activeCall != null) {
            activeCall.disconnect();
            activeCall = null;
        }
    }

    private void applyFabState(FloatingActionButton button, boolean enabled) {
        // Set fab as pressed when call is on hold
        ColorStateList colorStateList = enabled ?
                ColorStateList.valueOf(ContextCompat.getColor(this,
                        R.color.colorPrimaryDark)) :
                ColorStateList.valueOf(ContextCompat.getColor(this,
                        R.color.colorAccent));
        button.setBackgroundTintList(colorStateList);
    }

    private void setAudioFocus(boolean setFocus) {
        if (audioManager != null) {
            if (setFocus) {
                savedAudioMode = audioManager.getMode();
                // Request audio focus before making any device switch.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(i -> {
                            })
                            .build();
                    audioManager.requestAudioFocus(focusRequest);
                } else {
                    audioManager.requestAudioFocus(
                            focusChange -> { },
                            AudioManager.STREAM_VOICE_CALL,
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                }
                /*
                 * Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
                 * required to be in this mode when playout and/or recording starts for
                 * best possible VoIP performance. Some devices have difficulties with speaker mode
                 * if this is not set.
                 */
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            } else {
                audioManager.setMode(savedAudioMode);
                audioManager.abandonAudioFocus(null);
            }
        }
    }

    private boolean checkPermissionForMicrophone() {
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkPermissionForPhoneState() {
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkPermissionForPhoneNumber() {
        int result = ContextCompat.checkSelfPermission(this, READ_PHONE_NUMBERS);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
//        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
//            Snackbar.make(coordinatorLayout,
//                    "Microphone permissions needed. Please allow in your application settings.",
//                    Snackbar.LENGTH_LONG).show();
//        } else {
        String[] permissions = {Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECORD_AUDIO, READ_PHONE_NUMBERS};
        ActivityCompat.requestPermissions(
                    this,
                    permissions,
                    MIC_PERMISSION_REQUEST_CODE);
//        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        /*
         * Check if microphone permissions is granted
         */
        if (requestCode == MIC_PERMISSION_REQUEST_CODE && permissions.length > 0) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(coordinatorLayout,
                        "Permissions needed. Please allow in your application settings.",
                        Snackbar.LENGTH_LONG).show();
            } else {
                getDeviceId();
            }
        }
    }

    public void setSpeaker() {
        if (audioManager.isSpeakerphoneOn()) {
            audioManager.setSpeakerphoneOn(false);
//            speakerActionFab.setImageResource(R.drawable.ic_phonelink_ring_white_24dp);
        } else {
            audioManager.setSpeakerphoneOn(true);
//            speakerActionFab.setImageResource(R.drawable.ic_volume_up_white_24dp);
        }
        return;
    }


    private void showIncomingCallView() {
        mSoundManager.playRinging();
        if (activeCallInvite != null) {
            firstScreen.setVisibility(View.INVISIBLE);
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setStatusBarColor(getResources().getColor(R.color.color1));
            incomingView.setVisibility(View.VISIBLE);
            ((TextView)incomingView.findViewById(R.id.tv_incoming_number)).setText(activeCallInvite.getFrom());
            ((TextView)incomingView.findViewById(R.id.tv_incoming_number_again)).setText("휴대전화 "+activeCallInvite.getFrom());
            ((TextView)findViewById(R.id.tv_talking_number)).setText(activeCallInvite.getFrom());
            ((ImageView)incomingView.findViewById(R.id.iv_receive_call)).setVisibility(View.VISIBLE);
            ((ImageView)incomingView.findViewById(R.id.iv_receive_call)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    answerCallClickListener();
                    getWindow().setStatusBarColor(getResources().getColor(R.color.color2));
                    talkingView.setVisibility(View.VISIBLE);
                }
            });

            ((ImageView)incomingView.findViewById(R.id.iv_decline_call)).setVisibility(View.VISIBLE);
            ((ImageView)incomingView.findViewById(R.id.iv_decline_call)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    declineCall();
                }
            });

            ((ImageView)talkingView.findViewById(R.id.iv_talking_decline_call)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    declineCall();
                }
            });
        }
    }

    private boolean isAppVisible() {
        return ProcessLifecycleOwner
                .get()
                .getLifecycle()
                .getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
    }

    /*
     * Get an access token from your Twilio access token server
     */
    private void retrieveAccessToken(String identity) {
        Ion.with(this).load(TWILIO_ACCESS_TOKEN_SERVER_URL + "?identity=" + identity)
                .asString()
                .setCallback((e, accessToken) -> {
                    if (e == null) {
                        Log.d(TAG, "Access token: " + accessToken);
                        VoiceActivity.this.accessToken = accessToken;
                        registerForCallInvites();
                        mHandler.sendEmptyMessageDelayed(0, 2000);
                    } else {
                        Snackbar.make(coordinatorLayout,
                                "Error retrieving access token. Unable to make calls",
                                Snackbar.LENGTH_LONG).show();
                        mHandler.sendEmptyMessageDelayed(0, 2000);
                    }
                });
    }

    @Override
    public boolean handleMessage(Message message) {
        switch (message.what){
            case 0:
                finish();
                break;
        }
        return false;
    }

    protected void getDeviceId(){
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                mDeviceId = telephonyManager.getDeviceId();
            }
        }else{
            mDeviceId = telephonyManager.getDeviceId();
        }
        if ( ActivityCompat.checkSelfPermission(this, READ_PHONE_NUMBERS) ==
                        PackageManager.PERMISSION_GRANTED) {
            mPhoneNumber = telephonyManager.getLine1Number();
        }
        addOrGetCallId();
    }

    protected void addOrGetCallId(){
        ProgressHelper.showDialog(this);
        ApiCall.getInstance().addOrGetCallId(mDeviceId, mPhoneNumber, adminIdentify, this);
    }

    @Override
    public void onSuccess(String type, Response<BaseResponse> response) {
        ProgressHelper.dismiss();

        if(type.equals("check")){
            if(response.isSuccessful()){
                if (response.body().getErrorCode().equals("0")) {
                    identity = response.body().getErrorMsg();
//                    showToast(identity);
                    retrieveAccessToken(identity);
                }else{
                    showToast("Error : "+ response.body().getErrorMsg());
                }
            }else{
                showToast("Failed");
            }
        }
    }

    @Override
    public void onFailure(Object data) {
        ProgressHelper.dismiss();
        showToast("네트워크상태를 확인해주세요.");
    }

    protected void showToast(String text){
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
    }

    class PhoneCallStateReceiver extends BroadcastReceiver {
        private TelephonyManager mTelephonyManager;
        public boolean isListening = false;
        ITelephony telephonyService;

        @Override
        public void onReceive(final Context context, Intent intent) {

            mTelephonyManager = (TelephonyManager) context.getSystemService(context.TELEPHONY_SERVICE);

            PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String incomingNumber) {
                    super.onCallStateChanged(state, incomingNumber);

                    switch (state) {
                        case TelephonyManager.CALL_STATE_IDLE:
                            break;
                        case TelephonyManager.CALL_STATE_RINGING:

                            Toast.makeText(context, "CALL_STATE_RINGING : "+ "  "+ incomingNumber, Toast.LENGTH_SHORT).show();
                            try{
                                Method m = mTelephonyManager.getClass().getDeclaredMethod("getITelephony");
                                m.setAccessible(true);
                                telephonyService = (ITelephony) m.invoke(mTelephonyManager);
                                telephonyService.endCall();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            break;
                        case TelephonyManager.CALL_STATE_OFFHOOK:
                            break;
                    }
                }
            };

            if(!isListening) {
                mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
                isListening = true;
            }
        }
    }
}

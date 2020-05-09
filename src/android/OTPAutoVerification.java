/*
 * Developer
 * Sandeep Dillerao (India)
 * sandydillerao@gmail.com
 * +91 8483094292
 * */
package org.apache.cordova.OTPAutoVerification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class echoes a string called from JavaScript.
 */
public class OTPAutoVerification extends CordovaPlugin {

    private IntentFilter filter;
    private static final String TAG = OTPAutoVerification.class.getSimpleName();

    public static int OTP_LENGTH = 0;
    public JSONArray options;
    public CallbackContext callbackContext;
    private Context mContext;
    @Override
    public boolean execute(String action, JSONArray options, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("startOTPListener")) {
            Log.i(TAG, options.toString());
            this.options = options;
            this.callbackContext = callbackContext;
            this.mContext = this.cordova.getActivity().getApplicationContext();
            startOTPListener(options, callbackContext);

            return true;
        }else if (action.equals("stopOTPListener")) {
            stopOTPListener();
            return true;
        }
        return false;
    }

    private void startOTPListener(JSONArray options, final CallbackContext callbackContext) {
        /* take init parameter from JS call */
        try {
            OTP_LENGTH = options.getJSONObject(0).getInt("length");

            SMSListener.bindListener(new Common.OTPListener() {
                @Override
                public void onOTPReceived(String otp) {
                    Log.e(TAG, "OTP received: " + otp);
                    stopOTPListener();
                    callbackContext.success(otp);
                }

                @Override
                public void onOTPTimeOut() {
                    Log.e(TAG, "OTP Timeout: ");
                    stopOTPListener();
                    callbackContext.error("TIMEOUT");
                }
            });
            startSMSListener();

        } catch (JSONException e) {
            e.printStackTrace();
        }
        filter = new IntentFilter();
        filter.addAction("android.provider.Telephony.SMS_RECEIVED");
        cordova.getActivity().registerReceiver(new SMSListener(), filter);
        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
        Log.i("SMS pluginResult", pluginResult.toString());
    }

    private void stopOTPListener(){
        Log.d("OTPAutoVerification", "stopOTPListener");
        SMSListener.unbindListener();
    }

    private void startSMSListener() {
        // Get an instance of SmsRetrieverClient, used to start listening for a matching
        // SMS message.

        // Starts SmsRetriever, which waits for ONE matching SMS message until timeout
        // (5 minutes). The matching SMS message will be sent via a Broadcast Intent with
        // action SmsRetriever#SMS_RETRIEVED_ACTION.
        val task = SmsRetriever.getClient(mContext).startSmsUserConsent(null);

        // Listen for success/failure of the start Task. If in a background thread, this
        // can be made blocking using Tasks.await(task, [timeout]);
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                // Successfully started retriever, expect broadcast intent
                // ...
                Log.d("smsListener", "SUCCESS");
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // Failed to start retriever, inspect Exception for more details
                // ...
                Log.d("smsListener", "FAILED");
            }
        });
    }


    /*
     * Interface for OTP sms Listener
     * */
    public interface Common {
        interface OTPListener {
            void onOTPReceived(String otp);
            void onOTPTimeOut();
        }
    }

    /*
     * broadcast listener to listen for MESSAGE
     * @return originalMessage and Sender
     * onOTPReceived(smsMessage.getDisplayMessageBody(), senderAddress);
     * */
    public static class SMSListener extends BroadcastReceiver {

        private static OTPAutoVerification.Common.OTPListener mListener; // this listener will do the magic of throwing the extracted OTP to all the bound views.

        @Override
        public void onReceive(Context context, Intent intent) {

            // this function is trigged when each time a new SMS is received on device.

            if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
                Bundle extras = intent.getExtras();
                Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);

                switch(status.getStatusCode()) {
                    case CommonStatusCodes.SUCCESS:
                        // Get SMS message contents
                        Intent consentIntent = extras.get(SmsRetriever.EXTRA_CONSENT_INTENT);
                        try {
                        // Start activity to show consent dialog to user, activity must be started in
                        // 5 minutes, otherwise you'll receive another TIMEOUT intent
                        startActivityForResult(consentIntent, SMS_CONSENT_REQUEST);
                    } catch (ActivityNotFoundException e) {
                        // Handle the exception ...
                    }
                    break;
                    case CommonStatusCodes.TIMEOUT:
                        // Waiting for SMS timed out (5 minutes)
                        // Handle the error ...
                        mListener.onOTPTimeOut();
                        Log.d("failed","this is failed");
                        break;
                }
            }
        }
        
        
        @Override
public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    switch (requestCode) {
        // ...
        case SMS_CONSENT_REQUEST:
            if (resultCode == RESULT_OK) {
                // Get SMS message content
                String message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE);
                // Extract one-time code from the message and complete verification
                // `sms` contains the entire text of the SMS message, so you will need
                // to parse the string.
                String oneTimeCode = message; // define this function

                mListener.onOTPReceived(oneTimeCode);
            } else {
                // Consent canceled, handle the error ...
            }
            break;
    }
}

        public static void bindListener(OTPAutoVerification.Common.OTPListener listener) {
            mListener = listener;
        }

        public static void unbindListener() {
            mListener = null;
        }
    }
}

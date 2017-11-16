package org.cloudsky.cordovaPlugins;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.cloudsky.cordovaPlugins.ZBarScannerActivity;

public class ZBar extends CordovaPlugin {

    // Configuration ---------------------------------------------------

    private Context appCtx;

    // State -----------------------------------------------------------

    private CallbackContext scanCallbackContext;
    private Boolean isReceiverRegistered = false;

    // Plugin API ------------------------------------------------------

    @Override
    public boolean execute (String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        appCtx = cordova.getActivity().getApplicationContext();
        
        if(action.equals("scan")) {
            JSONObject params = args.optJSONObject(0);   
            Intent scanIntent = new Intent(appCtx, ZBarScannerActivity.class);                                
            scanIntent.putExtra(ZBarScannerActivity.EXTRA_PARAMS, params.toString());
        
            scanCallbackContext = callbackContext; 

            if (!isReceiverRegistered) {
                LocalBroadcastManager.getInstance(appCtx).registerReceiver(mMessageReceiver, new IntentFilter("scanner"));
                isReceiverRegistered = true;
            }

            scanIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            cordova.getActivity().startActivity(scanIntent);
            return true;

        } else if (action.equals("cancel")) {
            Intent killIntent = new Intent(appCtx, ZBarScannerActivity.class);
            killIntent.putExtra("killExtra", true);
            cordova.getActivity().startActivity(killIntent);
            return true;

        } else {
            return false;

        }
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(appCtx).unregisterReceiver(mMessageReceiver);
    }

    // External results handler ----------------------------------------

    //receive multiple scan events while keeping scanner activity alive
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          // Extract data included in the Intent
          try {
            int resultCode = intent.getIntExtra("EXTRA_RESULT", 0);
            switch(resultCode) {
                case ZBarScannerActivity.EXTRA_RESULT_OK:
                    String barcodeValue = intent.getStringExtra(ZBarScannerActivity.EXTRA_QRVALUE);
                    scanCallbackContext.success(barcodeValue);
                    break;
                case ZBarScannerActivity.EXTRA_RESULT_CANCEL:
                    scanCallbackContext.error("cancelled");
                    break;
                case ZBarScannerActivity.EXTRA_RESULT_ERROR:
                    scanCallbackContext.error("Scan failed due to error");
                    break;
                default:
                    scanCallbackContext.error("Unknown error");
                }
                scanCallbackContext = null;

            } catch (NullPointerException e) {
                //do nothing
            }
        }
      };
}
package org.cloudsky.cordovaPlugins;

import java.io.IOException;
import java.lang.RuntimeException;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.AutoFocusCallback;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.content.pm.PackageManager;
import android.view.Surface;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;
import net.sourceforge.zbar.Config;

public class ZBarScannerActivity extends Activity
implements SurfaceHolder.Callback {

    //for barcode types
    private Collection<ZBarcodeFormat> mFormats = null;

    // Config ----------------------------------------------------------

    private static int autoFocusInterval = 2000; // Interval between AFcallback and next AF attempt.

    // Public Constants ------------------------------------------------

    public static final String EXTRA_QRVALUE = "qrValue";
    public static final String EXTRA_PARAMS = "params";
    public static final int EXTRA_RESULT_OK = 1;
    public static final int EXTRA_RESULT_CANCEL = 0;
    public static final int EXTRA_RESULT_ERROR = -1;
    public static final int RESULT_ERROR = RESULT_FIRST_USER + 1;
    private static final int CAMERA_PERMISSION_REQUEST = 1;

    // State -----------------------------------------------------------

    private Camera camera;
    private Handler autoFocusHandler;
    private SurfaceView scannerSurface;
    private SurfaceHolder holder;
    private ImageScanner scanner;
    private int surfW, surfH;
    private List<Camera.Size> supportedPreviewSizes;
    private Camera.Size previewSize;
    private BroadcastReceiver screenReceiver;
    private Boolean isCameraSetup = false;
    private String oldBarcode = "0";

    // Customisable stuff
    String whichCamera;
    String flashMode;

    // For retrieving R.* resources, from the actual app package
    // (we can't use actual.application.package.R.* in our code as we
    // don't know the applciation package name when writing this plugin).
    private String package_name;
    private Resources resources;

    // Static initialisers (class) -------------------------------------

    static {
        // Needed by ZBar??
        System.loadLibrary("iconv");
    }

    // Activity Lifecycle ----------------------------------------------

    @Override
    public void onCreate (Bundle savedInstanceState) {
        
        try {
            if (this.getIntent().getStringExtra("killExtra").contains("kill")) {
                Intent dieIntent = new Intent("scanner");
                dieIntent.putExtra("EXTRA_RESULT", EXTRA_RESULT_CANCEL);
                sendMessage(dieIntent);
                finish(); //immediately kill activity
                return; //do not continue with remainder of onCreate
            }
        } catch (Exception e) {
            //do nothing
        }

        if ((this.getIntent().getFlags() | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) > 0) {
            ActivityManager tasksManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            tasksManager.moveTaskToFront(getTaskId(), ActivityManager.MOVE_TASK_NO_USER_ACTION);
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);        

        int permissionCheck = ContextCompat.checkSelfPermission(this.getBaseContext(), Manifest.permission.CAMERA);

        if(permissionCheck == PackageManager.PERMISSION_GRANTED){

            if (!isCameraSetup)
            setUpCamera();

        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);

        }
        super.onCreate(savedInstanceState);

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        try {
            if (intent.getStringExtra("killExtra").contains("kill")) onBackPressed();
        } catch (Exception e) {
            //do nothing
        }
    }

    private void setWindowParams(float heightFloat) {
        WindowManager.LayoutParams params2 = getWindow().getAttributes();  
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;
        height = (int) (height * heightFloat + getStatusBarHeight() );
        surfH = height;
        surfW = width;
        Window window = getWindow();
        window.setGravity(Gravity.TOP);
        window.setLayout(width, height);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.getDecorView().requestFocus();

    }

    private int getStatusBarHeight() {
        Rect rectangle = new Rect();
        Window window = getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(rectangle);
        int statusBarHeight = rectangle.top;
        int contentViewTop =
                window.findViewById(Window.ID_ANDROID_CONTENT).getTop();
        int titleBarHeight= contentViewTop - statusBarHeight;
        return titleBarHeight;
    }

    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case CAMERA_PERMISSION_REQUEST: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setUpCamera();

                } else {
                   onBackPressed();

                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
    private void setUpCamera() {
        // If request is cancelled, the result arrays are empty.

        // Get parameters from JS
        Intent startIntent = getIntent();
        String paramStr = startIntent.getStringExtra(EXTRA_PARAMS);
        JSONObject params;
        try { params = new JSONObject(paramStr); }
        catch (JSONException e) { params = new JSONObject(); }
        String textTitle = params.optString("text_title");
        String textInstructions = params.optString("text_instructions");
        Boolean drawSight = params.optBoolean("drawSight", true);
        whichCamera = params.optString("camera");
        flashMode = params.optString("flash");
        String windowHeight = params.optString("height");
        float windowHeightFloat;
        try {
            windowHeightFloat = Float.parseFloat(windowHeight);
        } catch (NumberFormatException e) {
            windowHeightFloat = 0.5f;
        }

        // Initiate instance variables
        autoFocusHandler = new Handler();
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);

        // Set the config for barcode formats
        for(ZBarcodeFormat format : getFormats()) {
            scanner.setConfig(format.getId(), Config.ENABLE, 1);
        }

        // Set content view
        setContentView(getResourceId("layout/cszbarscanner"));

        setWindowParams(windowHeightFloat);

        // Draw/hide the sight
        if(!drawSight) {
            findViewById(getResourceId("id/csZbarScannerSight")).setVisibility(View.INVISIBLE);
            findViewById(getResourceId("id/csZbarScannerSightVertical")).setVisibility(View.INVISIBLE);
        }

        // Create preview SurfaceView
        scannerSurface = new SurfaceView (this) {
            @Override
            public void onSizeChanged (int w, int h, int oldW, int oldH) {
                surfW = w;
                surfH = h;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                final int width = getMeasuredWidth();
                final int height = getMeasuredHeight();
                
                setMeasuredDimension(width, height);
                
                if (supportedPreviewSizes != null) {
                    previewSize = getOptimalPreviewSize(supportedPreviewSizes, width, height);
                }
            }
        };
        scannerSurface.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        ));
        scannerSurface.getHolder().addCallback(this);

        // Add preview SurfaceView to the screen
        FrameLayout scannerView = (FrameLayout) findViewById(getResourceId("id/csZbarScannerView"));
        scannerView.addView(scannerSurface);

        findViewById(getResourceId("id/csZbarScannerSightContainer")).bringToFront();
        findViewById(getResourceId("id/csZbarScannerSight")).bringToFront();
        findViewById(getResourceId("id/csZbarScannerSightVertical")).bringToFront();
        scannerView.requestLayout();
        scannerView.invalidate();
        isCameraSetup = true;
    }

    @Override
    public void onResume ()
    {
        super.onResume();

        try {
            openCamera();
            supportedPreviewSizes = camera.getParameters().getSupportedPreviewSizes();
            tryStartPreview();
        } catch (NullPointerException e) {
            die ("Camera is not setup");
        }
    }

    private void openCamera() {
        try {
            if(whichCamera.equals("front")) {
                int numCams = Camera.getNumberOfCameras();
                CameraInfo cameraInfo = new CameraInfo();
                for(int i=0; i<numCams; i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if(cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
                        camera = Camera.open(i);
                    }
                }
            } else {
                camera = Camera.open();
            }

            if(camera == null) throw new Exception ("Error: No suitable camera found.");
        } catch (RuntimeException e) {
            //die("Error: Could not open the camera.");
            return;
        } catch (Exception e) {
           // die(e.getMessage());
            return;
        }
    }

    private void setCameraDisplayOrientation(Activity activity ,int cameraId) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result = 0;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        
        if(camera != null) {
            camera.setDisplayOrientation(result);
        }
    }
    @Override
    public void onPause ()
    {
        tryStopPreview();
        releaseCamera();
        super.onPause();
    }

    @Override
    public void onDestroy ()
    {
        if(scanner != null) scanner.destroy();
        super.onDestroy();
    }

    // Event handlers --------------------------------------------------

    @Override
    public void onBackPressed ()
    {
        Intent dieIntent = new Intent("scanner");
        dieIntent.putExtra("EXTRA_RESULT", EXTRA_RESULT_CANCEL);
        if (sendMessage(dieIntent)) {
            releaseCamera();
            super.onBackPressed();
        }
    }

    public void cancelScan(View view) {
        onBackPressed();
    }

    // SurfaceHolder.Callback implementation ---------------------------

    @Override
    public void surfaceCreated (SurfaceHolder hld)
    {
        tryStopPreview();
        holder = hld;
        tryStartPreview();
    }

    @Override
    public void surfaceDestroyed (SurfaceHolder holder)
    {
        // No surface == no preview == no point being in this Activity.
        die("The camera surface was destroyed");

    }

    @Override
    public void surfaceChanged (SurfaceHolder hld, int fmt, int w, int h)
    {
        // Sanity check - holder must have a surface...
        if(hld.getSurface() == null) die("There is no camera surface");

        tryStopPreview();
        holder = hld;
        tryStartPreview();

    }

    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        openCamera();
        tryStopPreview();
        tryStartPreview();

    }

    public void toggleFlash(View view) {
        camera.startPreview();
        android.hardware.Camera.Parameters camParams = camera.getParameters();
        //If the flash is set to off
        try {
            if (camParams.getFlashMode().equals(Parameters.FLASH_MODE_OFF) && !(camParams.getFlashMode().equals(Parameters.FLASH_MODE_TORCH)) && !(camParams.getFlashMode().equals(Parameters.FLASH_MODE_ON)))
                camParams.setFlashMode(Parameters.FLASH_MODE_TORCH);
            else //if(camParams.getFlashMode() == Parameters.FLASH_MODE_ON || camParams.getFlashMode()== Parameters.FLASH_MODE_TORCH)
                camParams.setFlashMode(Parameters.FLASH_MODE_OFF);
        }   catch(RuntimeException e) {

        }

    
		try {
            camera.setParameters(camParams);
        } catch(RuntimeException e) {
            Log.d("csZBar", (new StringBuilder("Unsupported camera parameter reported for flash mode: ")).append(flashMode).toString());
        } //catch (IOException e) {
        	//Log.d("csZBar", (new StringBuilder("Wrong holder data")).append(flashMode).toString());
		//}
    }
    // Continuously auto-focus -----------------------------------------
    // For API Level < 14

    private AutoFocusCallback autoFocusCb = new AutoFocusCallback()
    {
        public void onAutoFocus(boolean success, Camera camera) {
            // some devices crash without this try/catch and cancelAutoFocus()... (#9)
            try {
                camera.cancelAutoFocus();
                autoFocusHandler.postDelayed(doAutoFocus, autoFocusInterval);
            } catch (Exception e) {}
        }
    };

    private Runnable doAutoFocus = new Runnable()
    {
        public void run() {
            if(camera != null) camera.autoFocus(autoFocusCb);
        }
    };

    // Camera callbacks ------------------------------------------------

    // Receives frames from the camera and checks for barcodes.
    private PreviewCallback previewCb = new PreviewCallback()
    {
        public void onPreviewFrame(byte[] data, Camera camera) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            Image barcode = new Image(size.width, size.height, "Y800");
            barcode.setData(data);

            if (scanner.scanImage(barcode) != 0) {
                String qrValue = "";
                SymbolSet syms = scanner.getResults();

                for (Symbol sym : syms) {
                    qrValue = sym.getData();
                    Log.d("cszbar", "Code: " + qrValue + " Old Code: " + oldBarcode);
                    if (qrValue.equalsIgnoreCase(oldBarcode)) {
                        // Return 1st found QR code value to the calling Activity if two frames match
                        Intent result = new Intent ("scanner");
                        result.putExtra(EXTRA_QRVALUE, qrValue);
                        result.putExtra("EXTRA_RESULT", EXTRA_RESULT_OK);
                        boolean messageSent = sendMessage(result);
                    }
                    oldBarcode = qrValue;                                        
                }
            }
        }
    };

    private boolean sendMessage(Intent result) {
        return LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcast(result);
        
    }

    // Misc ------------------------------------------------------------

    // finish() due to error
    private void die (String msg)
    {
        //setResult(RESULT_ERROR);
        Intent dieIntent = new Intent("scanner");
        dieIntent.putExtra("EXTRA_RESULT", EXTRA_RESULT_ERROR);
        if (sendMessage(dieIntent))
            finish();
    }

    private int getResourceId (String typeAndName)
    {
        if(package_name == null) package_name = getApplication().getPackageName();
        if(resources == null) resources = getApplication().getResources();
        return resources.getIdentifier(typeAndName, null, package_name);
    }

    // Release the camera resources and state.
    private void releaseCamera ()
    {
        if (camera != null) {
            autoFocusHandler.removeCallbacks(doAutoFocus);
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    // Stop the camera preview safely.
    private void tryStopPreview () {
        // Stop camera preview before making changes.
        try {
            camera.stopPreview();
        } catch (Exception e){
          // Preview was not running. Ignore the error.
        }
    }

    public Collection<ZBarcodeFormat> getFormats() {
        if(mFormats == null) {
            return ZBarcodeFormat.ALL_FORMATS;
        }
        return mFormats;
    }

    // Start the camera preview if possible.
    // If start is attempted but fails, exit with error message.
    private void tryStartPreview () {
        if(holder != null) {
            try {
                // 90 degrees rotation for Portrait orientation Activity.
                setCameraDisplayOrientation(this, 0);

                //camParams.setFlashMode(Parameters.FLASH_MODE_TORCH);

                try {
                    android.hardware.Camera.Parameters camParams = camera.getParameters();
                    camParams.set("orientation", "portrait");
                    camParams.setPreviewSize(previewSize.width, previewSize.height);
                    camParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                    camera.setParameters(camParams);
                } catch (Exception e) {
					// TODO: don't swallow
                }

                tryStopPreview();
                camera.setPreviewDisplay(holder);
                camera.setPreviewCallback(previewCb);
                camera.startPreview();

                if (android.os.Build.VERSION.SDK_INT >= 14) {
                    camera.autoFocus(autoFocusCb); // We are not using any of the
                        // continuous autofocus modes as that does not seem to work
                        // well with flash setting of "on"... At least with this
                        // simple and stupid focus method, we get to turn the flash
                        // on during autofocus.
                }
            } catch (IOException e) {
                die("Could not start camera preview: " + e.getMessage());
            }
        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio=(double)w/h;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        
        return optimalSize;
    }
}

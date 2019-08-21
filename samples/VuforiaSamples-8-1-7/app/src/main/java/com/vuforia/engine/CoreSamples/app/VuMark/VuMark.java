/*===============================================================================
Copyright (c) 2019 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.VuMark;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.transition.TransitionManager;
import android.transition.Transition;
import android.transition.Fade;

import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.HINT;
import com.vuforia.ObjectTracker;
import com.vuforia.PositionalDeviceTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.TrackableResult;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;
import com.vuforia.engine.CoreSamples.ui.SampleAppMessage;
import com.vuforia.engine.CoreSamples.videochatinterface.ServerCall;
import com.vuforia.engine.CoreSamples.videochatinterface.ServerCaller;
import com.vuforia.engine.SampleApplication.SampleActivityBase;
import com.vuforia.engine.SampleApplication.utils.SampleAppTimer;
import com.vuforia.engine.SampleApplication.SampleApplicationControl;
import com.vuforia.engine.SampleApplication.SampleApplicationException;
import com.vuforia.engine.SampleApplication.SampleApplicationSession;
import com.vuforia.engine.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.engine.SampleApplication.utils.SampleApplicationGLView;
import com.vuforia.engine.SampleApplication.utils.Texture;
import com.vuforia.engine.CoreSamples.R;
import com.vuforia.engine.CoreSamples.ui.SampleAppMenu.SampleAppMenu;
import com.vuforia.engine.CoreSamples.ui.SampleAppMenu.SampleAppMenuGroup;
import com.vuforia.engine.CoreSamples.ui.SampleAppMenu.SampleAppMenuInterface;

import java.util.ArrayList;
import java.util.Vector;


public class VuMark extends SampleActivityBase implements SampleApplicationControl,
        SampleAppMenuInterface, ServerCaller
{
    private static final String LOGTAG = "VuMark";
    
    private SampleApplicationSession vuforiaAppSession;
    
    private DataSet mCurrentDataset;

    private SampleApplicationGLView mGlView;

    private VuMarkRenderer mRenderer;
    
    private GestureDetector mGestureDetector;
    
    // The textures we will use for rendering:
    private Vector<Texture> mTextures;

    private boolean mDeviceTracker = false;

    private RelativeLayout mUILayout;
    
    private SampleAppMenu mSampleAppMenu;
    ArrayList<View> mSettingsAdditionalViews = new ArrayList<>();
    
    final LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);

    private SampleAppMessage mSampleAppMessage;
    private SampleAppTimer mRelocalizationTimer;
    private SampleAppTimer mStatusDelayTimer;

    private int mCurrentStatusInfo;

    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;
    
    private boolean mIsDroidDevice = false;

    private Bundle extras;
    private int userId, contactId;

    private ImageButton resetButton;
    private TextView playerCards;

    private static VuMark vuMarkInstance;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {

        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);

        vuMarkInstance = this;

        extras = getIntent().getExtras();
        userId = extras.getInt("userId");
        contactId = extras.getInt("contactId");

        ServerCall serverCall = new ServerCall(this, userId);
        serverCall.execute(Integer.toString(ServerCall.SERVER_VUMARK_DATA_UPDATE),
                Integer.toString(userId),
                Integer.toString(contactId),
                "",
                "-1",
                "0",
                "0",
                "0");
        
        vuforiaAppSession = new SampleApplicationSession(this);
        
        startLoadingAnimation();

        vuforiaAppSession
            .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        mGestureDetector = new GestureDetector(this, new GestureListener());
        
        // Load any sample specific textures:
        mTextures = new Vector<>();
//        loadTextures();
        loadCards();

        mIsDroidDevice = Build.MODEL.toLowerCase().startsWith(
            "droid");

        // Relocalization timer and message
        mSampleAppMessage = new SampleAppMessage(this, mUILayout, mUILayout.findViewById(R.id.topbar_layout), false);
        mRelocalizationTimer = new SampleAppTimer(10000, 1000)
        {
            @Override
            public void onFinish()
            {
                if (vuforiaAppSession != null)
                {
                    vuforiaAppSession.resetDeviceTracker();
                }

                super.onFinish();
            }
        };

        mStatusDelayTimer = new SampleAppTimer(1000, 1000)
        {
            @Override
            public void onFinish()
            {
                if (mRenderer.isTargetCurrentlyTracked())
                {
                    super.onFinish();
                    return;
                }

                if (!mRelocalizationTimer.isRunning())
                {
                    mRelocalizationTimer.startTimer();
                }

                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mSampleAppMessage.show(getString(R.string.instruct_relocalize));
                    }
                });

                super.onFinish();
            }
        };
    }


    private class GestureListener extends
        GestureDetector.SimpleOnGestureListener
    {
        // Used to set autofocus one second after a manual focus is triggered
        private final Handler autofocusHandler = new Handler();
        
        
        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }


        // Process Single Tap event to trigger autofocus
        @Override
        public boolean onSingleTapUp(MotionEvent e)
        {
            boolean result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);
            if (!result)
                Log.e("SingleTapUp", "Unable to trigger focus");

            // Generates a Handler to trigger continuous auto-focus
            // after 1 second
            autofocusHandler.postDelayed(new Runnable()
            {
                public void run()
                {
                    final boolean autofocusResult = CameraDevice.getInstance().setFocusMode(
                            CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

                    if (!autofocusResult)
                        Log.e("SingleTapUp", "Unable to re-enable continuous auto-focus");
                }
            }, 1000L);
            
            return true;
        }
    }

    /*
    * Loads all the cards from the assets directory as Textures. This method is called in place of
    * loadTextures method which loads a single texture from the assets directory.
    */
    private void loadCards(){
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};

        String[] suits = {"H", "S", "D", "C"};

        String[] cards = new String[52];
        int index = 0;

        for (String rank : ranks){
            for (String suit : suits){
                cards[index] = rank + suit + ".png";
                index++;
            }
        }

        for (String card : cards) {
            mTextures.add(Texture.loadTextureFromApk(card, getAssets()));
        }
    }

    // Load specific textures from the APK, which we will later use for rendering.
    private void loadTextures()
    {
        mTextures.add(Texture.loadTextureFromApk("vumark_texture.png",
                getAssets()));
    }
    

    @Override
    protected void onResume()
    {
        Toast.makeText(this, "vuMark onResume", Toast.LENGTH_SHORT).show();
        Log.d(LOGTAG, "onResume");
        super.onResume();

        showProgressIndicator(true);
        
        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice)
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        
        vuforiaAppSession.onResume();
    }
    
    
    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged(Configuration config)
    {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);
        
        vuforiaAppSession.onConfigurationChanged();
    }
    

    @Override
    protected void onPause()
    {
        Toast.makeText(this, "vuMark stop", Toast.LENGTH_SHORT).show();
//        Log.d(LOGTAG, "onPause");

        super.onPause();

        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }

        vuforiaAppSession.onPause();

        deleteVuMarkDataServerCall();

        this.finish();
    }
    

    @Override
    protected void onDestroy()
    {
        Log.d(LOGTAG, "onDestroy");

        deleteVuMarkDataServerCall();

        super.onDestroy();
        
        try
        {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
        
        // Unload texture:
        mTextures.clear();
        mTextures = null;
        
        System.gc();
    }
    

    private void initApplicationAR()
    {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();
        
        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);
        
        mRenderer = new VuMarkRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);
        mGlView.setRenderer(mRenderer);
        mGlView.setPreserveEGLContextOnPause(true);

        setRendererReference(mRenderer);
        
    }
    
    
    private void startLoadingAnimation()
    {
        mUILayout = (RelativeLayout) View.inflate(this, R.layout.camera_overlay_reticle,
            null);
        
        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        RelativeLayout topbarLayout = mUILayout.findViewById(R.id.topbar_layout);
        topbarLayout.setVisibility(View.INVISIBLE);

//        TextView title = mUILayout.findViewById(R.id.topbar_title);
//        title.setText(getText(R.string.feature_vumark));

        resetButton = mUILayout.findViewById(R.id.btn_reset);
        resetButton.setVisibility(View.INVISIBLE);

        playerCards = mUILayout.findViewById(R.id.players_left);
        playerCards.setVisibility(View.INVISIBLE);

        mSettingsAdditionalViews.add(topbarLayout);
        mSettingsAdditionalViews.add(mUILayout.findViewById(R.id.alignment_overlay));

        loadingDialogHandler.mLoadingDialogContainer = mUILayout
            .findViewById(R.id.loading_indicator);
        
        // Shows the loading indicator at start
        loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);

        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        mUILayout.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                Transition transition = new Fade();
                transition.setDuration(600);
                transition.addTarget(resetButton);
                transition.addTarget(playerCards);
                TransitionManager.beginDelayedTransition(mUILayout, transition);

                long duration = 5000;
                long tick = 1000;

                if (event.getAction() == MotionEvent.ACTION_DOWN) {

                    new CountDownTimer(duration, tick) {
                        public void onTick(long millisUntilFinished) {
                            resetButton.setVisibility(View.VISIBLE);
                            playerCards.setVisibility(View.VISIBLE);
                        }

                        public void onFinish(){
                            resetButton.setVisibility(View.INVISIBLE);
                            playerCards.setVisibility(View.INVISIBLE);
                        }
                    }.start();
                }

                return view.onTouchEvent(event);
            }
        });
    }


    public ImageButton getResetButton() {
        return resetButton;
    }

    public TextView getPlayerCards() { return playerCards; }


    public void deleteVuMarkDataServerCall(){
        ServerCall serverCall = new ServerCall(this, userId);
        serverCall.execute(Integer.toString(ServerCall.SERVER_VUMARK_DATA_REMOVE), Integer.toString(userId), Integer.toString(contactId));
    }

    public void receiveServerCall(int type, boolean isSuccessful, String result){
        Log.d(LOGTAG, "In receiveServerCall");
        if(type == ServerCall.SERVER_VUMARK_DATA_REMOVE) {
            if (isSuccessful) {
//                    Log.d(LOGTAG, result);
                Log.d(LOGTAG, "called data remove finish");
                this.finish();
            }
        }
    }

    public static VuMark getVuMarkInstance() {
        return vuMarkInstance;
    }

    @Override
    public boolean doLoadTrackersData()
    {
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
            .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;
        
        if (mCurrentDataset == null)
            mCurrentDataset = objectTracker.createDataSet();

        return mCurrentDataset != null
                && mCurrentDataset.load("Vuforia.xml", STORAGE_TYPE.STORAGE_APPRESOURCE)
                && objectTracker.activateDataSet(mCurrentDataset);
    }
    
    
    @Override
    public boolean doUnloadTrackersData()
    {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;
        
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
            .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;
        
        if (mCurrentDataset != null && mCurrentDataset.isActive())
        {
            if (objectTracker.getActiveDataSets().at(0).equals(mCurrentDataset)
                && !objectTracker.deactivateDataSet(mCurrentDataset))
            {
                result = false;
            } else if (!objectTracker.destroyDataSet(mCurrentDataset))
            {
                result = false;
            }
            
            mCurrentDataset = null;
        }
        
        return result;
    }


    @Override
    public void onVuforiaResumed()
    {
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }


    // Called once Vuforia has been initialized or
    // an error has caused Vuforia initialization to stop
    @Override
    public void onInitARDone(SampleApplicationException exception)
    {
        
        if (exception == null)
        {
            initApplicationAR();

            mRenderer.setActive(true);

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
            
            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();

            mUILayout.setBackgroundColor(Color.TRANSPARENT);
            
            mSampleAppMenu = new SampleAppMenu(this, this, "VuMark",
                mGlView, mUILayout, mSettingsAdditionalViews);
            setSampleAppMenuSettings();

            vuforiaAppSession.startAR();

        }
        else
        {
            Log.e(LOGTAG, exception.getString());
            showInitializationErrorMessage(exception.getString());
        }
    }


    @Override
    public void onVuforiaStarted()
    {
        mRenderer.updateRenderingPrimitives();

        // Set camera focus mode
        if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO))
        {
            // If continuous autofocus mode fails, attempt to set to a different mode
            if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO))
            {
                CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
            }
        }

        showProgressIndicator(false);
    }


    private void showProgressIndicator(boolean show)
    {
        if (show)
        {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
        }
        else
        {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        }
    }
    

    private void showInitializationErrorMessage(String message)
    {
        final String errorMessage = message;
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (mErrorDialog != null)
                {
                    mErrorDialog.dismiss();
                }
                
                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                    VuMark.this);
                builder
                    .setMessage(errorMessage)
                    .setTitle(getString(R.string.INIT_ERROR))
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton("OK",
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int id)
                            {
                                finish();
                            }
                        });
                
                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }
    

    // Called every frame
    @Override
    public void onVuforiaUpdate(State state)
    {
    }
    
    
    @Override
    public boolean doInitTrackers()
    {
        // Indicate if the trackers were initialized correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker;
        
        // Trying to initialize the image tracker
        tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null)
        {
            Log.e(
                LOGTAG,
                "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else
        {
            Log.i(LOGTAG, "Tracker successfully initialized");
        }

        // Initialize the Positional Device Tracker
        PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker)
                tManager.initTracker(PositionalDeviceTracker.getClassType());

        if (deviceTracker != null)
        {
            Log.i(LOGTAG, "Successfully initialized Device Tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to initialize Device Tracker");
        }

        Vuforia.setHint(HINT.HINT_MAX_SIMULTANEOUS_IMAGE_TARGETS, 10);
        return result;
    }
    
    
    @Override
    public boolean doStartTrackers()
    {
        // Indicate if the trackers were started correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();

        Tracker objectTracker = trackerManager.getTracker(ObjectTracker.getClassType());

        if (objectTracker != null && objectTracker.start())
        {
            Log.i(LOGTAG, "Successfully started Object Tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to start Object Tracker");
            result = false;
        }

        if (isDeviceTrackingActive())
        {
            PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker) trackerManager
                    .getTracker(PositionalDeviceTracker.getClassType());

            if (deviceTracker != null && deviceTracker.start())
            {
                Log.i(LOGTAG, "Successfully started Device Tracker");
            }
            else
            {
                Log.e(LOGTAG, "Failed to start Device Tracker");
            }
        }
        
        return result;
    }
    
    
    @Override
    public boolean doStopTrackers()
    {
        // Indicate if the trackers were stopped correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();
        
        Tracker objectTracker = trackerManager.getTracker(ObjectTracker.getClassType());

        if (objectTracker != null)
        {
            objectTracker.stop();
            Log.i(LOGTAG, "Successfully stopped object tracker");
        }
        else
        {
            Log.e(LOGTAG, "Could not stop object tracker");
            result = false;
        }

        // Stop the device tracker
        if(isDeviceTrackingActive())
        {

            Tracker deviceTracker = trackerManager.getTracker(PositionalDeviceTracker.getClassType());

            if (deviceTracker != null)
            {
                deviceTracker.stop();
                Log.i(LOGTAG, "Successfully stopped device tracker");
            }
            else
            {
                Log.e(LOGTAG, "Could not stop device tracker");
            }
        }

        return result;
    }
    
    
    @Override
    public boolean doDeinitTrackers()
    {
        TrackerManager tManager = TrackerManager.getInstance();
        boolean result = tManager.deinitTracker(ObjectTracker.getClassType());
        tManager.deinitTracker(PositionalDeviceTracker.getClassType());
        
        return result;
    }
    
    
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        // Process the Gestures
        return ((mSampleAppMenu != null && mSampleAppMenu.processEvent(event))
                || mGestureDetector.onTouchEvent(event));
    }
    
    
    private boolean isDeviceTrackingActive()
    {
        return mDeviceTracker;
    }


    // Menu options
    private final static int CMD_BACK = -1;
    private final static int CMD_DEVICE_TRACKER = 1;

    private void setSampleAppMenuSettings()
    {
        SampleAppMenuGroup group;
        
        group = mSampleAppMenu.addGroup("", false);
        group.addTextItem(getString(R.string.menu_back), -1);
        
        group = mSampleAppMenu.addGroup("", true);
        group.addSelectionItem(getString(R.string.menu_device_tracker),
                CMD_DEVICE_TRACKER, false);

        mSampleAppMenu.attachMenu();
    }


    // In this function you can define the desired behavior for each menu option
    // Each case corresponds to a menu option
    @Override
    public boolean menuProcess(int command)
    {
        
        boolean result = true;
        
        switch (command)
        {
            case CMD_BACK:
                finish();
                break;

            case CMD_DEVICE_TRACKER:
                TrackerManager trackerManager = TrackerManager.getInstance();
                PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker)
                        trackerManager.getTracker(PositionalDeviceTracker.getClassType());

                if (deviceTracker != null)
                {
                    if (!mDeviceTracker)
                    {
                        if (!deviceTracker.start())
                        {
                            Log.e(LOGTAG,"Failed to start device tracker");
                            result = false;
                        }
                        else
                        {
                            Log.d(LOGTAG,"Successfully started device tracker");
                        }
                    }
                    else
                    {
                        deviceTracker.stop();
                        clearSampleAppMessage();
                    }
                }
                else
                {
                    Log.e(LOGTAG, "Device tracker is null!");
                    result = false;
                }

                if (result)
                {
                    mDeviceTracker = !mDeviceTracker;
                }
                else
                {
                    clearSampleAppMessage();
                }
                
                break;
            
            default:
                break;
        }
        
        return result;
    }


    public void checkForRelocalization(final int statusInfo)
    {
        if (mCurrentStatusInfo == statusInfo)
        {
            return;
        }

        mCurrentStatusInfo = statusInfo;

        if (mCurrentStatusInfo == TrackableResult.STATUS_INFO.RELOCALIZING)
        {
            // If the status is RELOCALIZING, start the timer
            if (!mStatusDelayTimer.isRunning())
            {
                mStatusDelayTimer.startTimer();
            }
        }
        else
        {
            // If the status is not RELOCALIZING, stop the timers and hide the message
            if (mStatusDelayTimer.isRunning())
            {
                mStatusDelayTimer.stopTimer();
            }

            if (mRelocalizationTimer.isRunning())
            {
                mRelocalizationTimer.stopTimer();
            }

            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    mSampleAppMessage.hide();
                }
            });
        }
    }


    private void clearSampleAppMessage()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (mSampleAppMessage != null)
                {
                    mSampleAppMessage.hide();
                }
            }
        });
    }
}

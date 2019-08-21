/*===============================================================================
Copyright (c) 2019 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.VuMark;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.vuforia.CameraCalibration;
import com.vuforia.Device;
import com.vuforia.DeviceTrackableResult;
import com.vuforia.Image;
import com.vuforia.InstanceId;
import com.vuforia.Matrix44F;
import com.vuforia.PIXEL_FORMAT;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackableResult;
import com.vuforia.TrackableResultList;
import com.vuforia.Vec2F;
import com.vuforia.Vec3F;
import com.vuforia.VuMarkTarget;
import com.vuforia.VuMarkTargetResult;
import com.vuforia.VuMarkTemplate;
import com.vuforia.Vuforia;
import com.vuforia.engine.CoreSamples.R;
import com.vuforia.engine.CoreSamples.videochatinterface.ServerCall;
import com.vuforia.engine.CoreSamples.videochatinterface.ServerCaller;
import com.vuforia.engine.SampleApplication.SampleAppRenderer;
import com.vuforia.engine.SampleApplication.SampleAppRendererControl;
import com.vuforia.engine.SampleApplication.SampleApplicationSession;
import com.vuforia.engine.SampleApplication.SampleRendererBase;
import com.vuforia.engine.SampleApplication.utils.CubeShaders;
import com.vuforia.engine.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.engine.SampleApplication.utils.SampleMath;
import com.vuforia.engine.SampleApplication.utils.SampleUtils;
import com.vuforia.engine.SampleApplication.utils.Texture;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;


/**
 * The renderer class for the VuMark sample.
 *
 * In the renderFrame() function you can render augmentations to display over the Target
 */
public class VuMarkRenderer extends SampleRendererBase implements SampleAppRendererControl, ServerCaller
{
    private static final String LOGTAG = "VuMarkRenderer";

    private final VuMark mActivity;

    private int shaderProgramID;
    private int vertexHandle;
    private int textureCoordHandle;
    private int mvpMatrixHandle;
    private int texSampler2DHandle;
    private int calphaHandle;

    private Renderer mRenderer;

    private double t0;

    // Object to be rendered
    private final Plane mPlaneObj;

    // Ratio to apply so that the augmentation surrounds the VuMark
    private static final float VUMARK_SCALE = 1.02f;
    private String currentVumarkIdOnCard;

    // Used to store vuMark ids that have already been tracked once
    private Map<String, Integer> vuDictionary = new HashMap<>();

    // Used to store vuMark ids that have been detected and are waiting for updated data
    // from the server before being tracked
    private List<String> serverCallVuMarks = new ArrayList<>();

    // Contains fillCardIndices from 0 to 51 in random order
    private List<Integer> cardIndices = fillCardIndices();

    // Contains fillCardIndices already selected
    private List<Integer> selectedCardIndices = new ArrayList<>();

    private List<String> currentlyTrackedVuMarks = new ArrayList<>();

    private int discardTextureIndex = -1;

    private static final String INIT_DISCARD_VUMARK_ID = "VuMark95";
    private static final long DELAY_TIME_MILLIS = 2000;

    private ArrayList<String> discardVuMarkIds = new ArrayList<>();

    private boolean newDiscardVumarkId = false;
    private boolean lock = false;

    private boolean reshuffleCards = false;
    private boolean reshuffleDialogShowing = false;

    private Vec2F discardPileProjection;

    int userId, contactId;
    String contactName;

    private static final int TOTAL_CARDS = 52;

    private boolean mIsTargetCurrentlyTracked = false;

    Handler serverCallHandler;
    Runnable serverCallRunnable;

    private ImageButton resetButton;
    private boolean resetGame = false;

    private TextView playerCards;

    private boolean alertDialogShowing = false;

    VuMarkRenderer(VuMark activity, SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;
        t0 = -1.0;

        mPlaneObj = new Plane();

        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mSampleAppRenderer = new SampleAppRenderer(this, mActivity, Device.MODE.MODE_AR,
                vuforiaAppSession.getVideoMode(), false, .01f, 100f);

        //Retrieve the extras passed in with this activity (user's ID and contact's ID)
        Bundle args = mActivity.getIntent().getExtras();
        userId = args.getInt("userId", -1);
        contactId = args.getInt("contactId", -1);

        serverCallHandler = new Handler();
        serverCallRunnable = new Runnable() {
            @Override
            public void run() {
                serverCallGet();
            }
        };

        serverCallHandler.post(serverCallRunnable);

        resetButton = mActivity.getResetButton();

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);

                builder.setTitle(R.string.restart_title).setMessage(R.string.restart_msg);
                builder.setPositiveButton(R.string.restart, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        resetGame = true;
                        resetGame();
                    }
                });
                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
            }
        });

        playerCards = mActivity.getPlayerCards();

        discardVuMarkIds.add(INIT_DISCARD_VUMARK_ID);

        serverContactGet();
    }

    private void serverContactGet(){
        ServerCall serverCall = new ServerCall(this, userId);
        serverCall.execute(Integer.toString(ServerCall.SERVER_USER_GET), Integer.toString(contactId));
    }


    public void setActive(boolean active)
    {
        mSampleAppRenderer.setActive(active);
    }


    @Override
    public void initRendering()
    {
        mRenderer = Renderer.getInstance();

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
                : 1.0f);

        for (Texture t : mTextures)
        {
            GLES20.glGenTextures(1, t.mTextureID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, t.mData);
        }

        shaderProgramID = SampleUtils.createProgramFromShaderSrc(
                CubeShaders.CUBE_MESH_VERTEX_SHADER,
                CubeShaders.CUBE_MESH_FRAGMENT_SHADER);

        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexPosition");
        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
            "vertexTexCoord");
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "modelViewProjectionMatrix");
        texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID,
            "texSampler2D");
        calphaHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "calpha");

        mActivity.loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
    }


    // Determines whether the VuMark augmentation is displayed or not, causing a blinking effect
    private float blinkVumark(boolean reset)
    {
        if (reset || t0 < 0.0f)
        {
            t0 = System.currentTimeMillis();
        }
        if (reset) 
        {
            return 0.0f;
        }
        double time = System.currentTimeMillis();
        double delta = (time-t0);

        if (delta > 1000.0f)
        {
            return 1.0f;
        }

        if ((delta < 300.0f) || ((delta > 500.0f) && (delta < 800.0f)))
        {
            return 1.0f;
        }

        return 0.0f;
    }


    public void updateRenderingPrimitives()
    {
        mSampleAppRenderer.updateRenderingPrimitives();
    }


    // The render function.
    // This function is called from the SampleAppRenderer by using the RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling its lifecycle.
    // NOTE: State should not be cached outside this method.
    public void renderFrame(State state, float[] projectionMatrix)
    {

        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground(state);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        boolean gotVuMark = false;
        boolean isMainVuMark;

        String markerType = "";
        String markerValue = "";
        Bitmap markerBitmap = null;
        TrackableResult vuMarkToDisplay = null;

        // Set the device pose matrix as identity
        Matrix44F devicePoseMatrix = SampleMath.Matrix44FIdentity();

        // Read device pose from the state and create a corresponding view matrix (inverse of the device pose)
        if (state.getDeviceTrackableResult() != null)
        {
            int statusInfo = state.getDeviceTrackableResult().getStatusInfo();
            int trackerStatus = state.getDeviceTrackableResult().getStatus();

            mActivity.checkForRelocalization(statusInfo);

            if (trackerStatus != TrackableResult.STATUS.NO_POSE)
            {
                Matrix44F modelMatrix = Tool.convertPose2GLMatrix(state.getDeviceTrackableResult().getPose());

                // We transpose here because Matrix44FInverse returns a transposed matrix
                devicePoseMatrix = SampleMath.Matrix44FTranspose(SampleMath.Matrix44FInverse(modelMatrix));
            }
        }

        TrackableResultList trackableResultList = state.getTrackableResults();

        if (trackableResultList.size() > 0)
        {
            float minimumDistance = Float.MAX_VALUE;
            CameraCalibration cameraCalibration = state.getCameraCalibration();
            Vec2F screenSize = cameraCalibration.getSize();
            Vec2F screenCenter = new Vec2F(screenSize.getData()[0] / 2.0f, screenSize.getData()[1] / 2.0f);

//            currentlyTrackedVuMarks.clear();

            String discardVuMarkId = discardVuMarkIds.get(discardVuMarkIds.size() - 1);

            // Iterate over trackables to find the id and projection matrix of the discard vuMark
            for (TrackableResult result : trackableResultList)
            {
                if (result.isOfType(VuMarkTargetResult.getClassType()))
                {
                    VuMarkTargetResult vmtResult = (VuMarkTargetResult) result;
                    VuMarkTarget vmTgt = (VuMarkTarget) vmtResult.getTrackable();

                    Vec3F point = new Vec3F(0, 0, 0);
                    Vec2F projection = Tool.projectPoint(cameraCalibration, result.getPose(), point);

                    InstanceId instanceId = vmTgt.getInstanceId();
                    String vuMarkId = instanceIdToValue(instanceId);

                    if(vuMarkId.equals(INIT_DISCARD_VUMARK_ID)) {
                        discardVuMarkIds.clear();
                        discardVuMarkIds.add(INIT_DISCARD_VUMARK_ID);

                        discardVuMarkId = vuMarkId;
                        discardPileProjection = projection;
                        continue;
                    }

                    Log.d(LOGTAG, "================================");
                    Log.d(LOGTAG, discardVuMarkIds.toString());
                    Log.d(LOGTAG, "================================");

                    if(vuMarkId.equals(discardVuMarkId)){
                        discardVuMarkId = vuMarkId;
                        discardPileProjection = projection;
                    }

                    if(discardVuMarkIds.contains(vuMarkId) && !vuMarkId.equals(discardVuMarkId)){
                        Log.d(LOGTAG, "++++++++++++++++++++++++++++++++++++++++++++++");
                        serverCallHandler.removeCallbacks(serverCallRunnable);
                        lock = true;
                        discardPileProjection = projection;
                        discardVuMarkIds.remove(discardVuMarkId);
                        discardVuMarkId = discardVuMarkIds.get(discardVuMarkIds.size() - 1);
                        discardTextureIndex = vuDictionary.get(discardVuMarkId);
                        newDiscardVumarkId = true;
                        currentlyTrackedVuMarks.add(discardVuMarkId);
                        lock = false;
                        serverCallHandler.post(serverCallRunnable);

                    }

                    if(!vuMarkId.equals(discardVuMarkId)) {
                        if(projection != null) {
                            if (isNewDisCard(projection)) {
                                serverCallHandler.removeCallbacks(serverCallRunnable);
                                lock = true;
                                discardPileProjection = projection;
                                currentlyTrackedVuMarks.remove(discardVuMarkId);
                                discardVuMarkIds.add(vuMarkId);
                                discardVuMarkId = vuMarkId;
                                discardTextureIndex = vuDictionary.get(discardVuMarkId);
                                newDiscardVumarkId = true;
                                lock = false;
                                serverCallHandler.post(serverCallRunnable);
                            }
                        }else{
                            continue;
                        }
                    }

                    Log.d(LOGTAG, "++++++++++++++++++++++++++++++++++++++++++++++++++");
                    Log.d(LOGTAG, "Cards tracked are " + currentlyTrackedVuMarks.toString());
                    Log.d(LOGTAG, "Cards left are: " + currentlyTrackedVuMarks.size());


                    Log.d(LOGTAG, "discard vuMark id is " + discardVuMarkId);
                }
            }
        }

        // Determine if target is currently being tracked
        setIsTargetCurrentlyTracked(trackableResultList);

        String vuMarkId = "";
        int textureIndex = 0;

        if(trackableResultList.size() > 0) {

            // Iterate through trackable results and render any augmentations
            for (TrackableResult result : trackableResultList) {
                Matrix44F modelViewMatrix_Vuforia = Tool
                        .convertPose2GLMatrix(result.getPose());
                float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();

                if (result.isOfType(VuMarkTargetResult.getClassType())) {
                    VuMarkTargetResult vmtResult = (VuMarkTargetResult) result;
                    VuMarkTarget vmTgt = (VuMarkTarget) vmtResult.getTrackable();

                    VuMarkTemplate vmTmp = vmTgt.getTemplate();

                    // user data contains the the SVG layer corresponding to the contour
                    // of the VuMark template
                    // look at the iOS sample app to see how this data can be used to dynamically
                    // render an OpenGL object on top of the contour.
                    // String userData = vmTmp.getVuMarkUserData();
                    // Log.d(LOGTAG, userData);

                    InstanceId instanceId = vmTgt.getInstanceId();
                    vuMarkId = instanceIdToValue(instanceId);

                    if(vuMarkId.equals(INIT_DISCARD_VUMARK_ID)) {
                        Log.d(LOGTAG, "discard Texture: " + discardTextureIndex);
                        if(discardTextureIndex != -1){
                            textureIndex = discardTextureIndex;
                        }else {
                            continue;
                        }
                    }
                    else if(vuDictionary.containsKey(vuMarkId)){
                        textureIndex = vuDictionary.get(vuMarkId);
                        Log.d(LOGTAG, "vuMark id in vuDictionary: " + vuMarkId);
                    }
                    else if(serverCallVuMarks.contains(vuMarkId)){
                        Log.d(LOGTAG, vuMarkId + " is waiting for server call to complete");
                        continue;
                    }
                    else{
                        serverCallVuMarks.add(vuMarkId);
                        serverCallGet();
                        continue;
                    }

                    isMainVuMark = ((vuMarkToDisplay == null) || (vuMarkToDisplay.equals(result)));
                    gotVuMark = true;

                    // Set the VuMark parameters for the one we've detected
                    if (isMainVuMark) {
                        // Hide the augmentation and reset the blink animation
                        // if this VuMark is not the one we've detected
                        if (!markerValue.equalsIgnoreCase(currentVumarkIdOnCard)) {
                            blinkVumark(true);
                        }
                    }

                    // Add a translation to recenter the augmentation
                    // at the center of the VuMark, with respect to the origin
                    Vec2F origin = vmTmp.getOrigin();
                    float translX = -origin.getData()[0];
                    float translY = -origin.getData()[1];
                    Matrix.translateM(modelViewMatrix, 0, translX, translY, 0);

                    // Scales the plane relative to the target
                    float vumarkWidth = vmTgt.getSize().getData()[0];
                    float vumarkHeight = vmTgt.getSize().getData()[1];

                    //=======================EDIT=============================//
                    //modify the size of the augmentation
                    Matrix.scaleM(modelViewMatrix, 0, vumarkWidth * 1.5f,
                            vumarkHeight * 1.5f, 1.0f);

                    //rotate the augmentation by 90 degrees along x-axis
                    Matrix.rotateM(modelViewMatrix, 0, 90, 0, 0, 1);
                } else {
                    continue;
                }

                renderModel(projectionMatrix, devicePoseMatrix.getData(), modelViewMatrix, isMainVuMark, textureIndex);
                SampleUtils.checkGLError("VuMark Render Frame");
            }
        }

        if(gotVuMark)
        {
            // If we have a detection, let's make sure
            // the card is visible
//            mActivity.showCard(markerType, markerValue, markerBitmap);
            currentVumarkIdOnCard = markerValue;
        }
        else
        {
            // We reset the state of the animation so that
            // it triggers next time a vumark is detected
            blinkVumark(true);
            // We also reset the value of the current value of the vumark on card
            // so that we hide and show the mumark if we redetect the same vumark instance
            currentVumarkIdOnCard = null;
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);

        mRenderer.end();
    }

    private void resetGame(){

        serverCallHandler.removeCallbacks(serverCallRunnable);

        vuDictionary.clear();
        selectedCardIndices.clear();
        serverCallVuMarks.clear();
        currentlyTrackedVuMarks.clear();

        discardTextureIndex = -1;
        discardVuMarkIds.clear();
        discardVuMarkIds.add(INIT_DISCARD_VUMARK_ID);
        discardPileProjection = null;

        serverCallUpdate();
    }

    private void reshuffleCardDeck(){
        vuDictionary.keySet().retainAll(currentlyTrackedVuMarks);

        selectedCardIndices = new ArrayList<>(vuDictionary.values());

        cardIndices = fillCardIndices();
        cardIndices.removeAll(selectedCardIndices);

        String discardVuMarkId = discardVuMarkIds.get(discardVuMarkIds.size() - 1);
        discardVuMarkIds.clear();
        discardVuMarkIds.add(INIT_DISCARD_VUMARK_ID);
        discardTextureIndex = -1;

        if(!discardVuMarkId.equals(INIT_DISCARD_VUMARK_ID)){
            discardVuMarkIds.add(discardVuMarkId);
            discardTextureIndex = vuDictionary.get(discardVuMarkId);
        }

        showReshuffleDialog();
        reshuffleDialogShowing = true;

        reshuffleCards = true;

        serverCallUpdate();
    }

    private boolean isNewDisCard(Vec2F cardProjection){

        // Threshold distance between two cards.
        float threshold = 100.0f;

        float distance = distance(distanceSquared(cardProjection, discardPileProjection));

        return distance < threshold;

    }

    private int getCardsLeft(){
        int cardsLeft = currentlyTrackedVuMarks.size();
        String discardVuMarkId = discardVuMarkIds.get(discardVuMarkIds.size() - 1);
        if(currentlyTrackedVuMarks.contains(discardVuMarkId)){
            cardsLeft = cardsLeft - 1;
        }

        return cardsLeft;
    }

    private List<Integer> fillCardIndices(){

        List<Integer> indices = new ArrayList<>(TOTAL_CARDS);
        for (int i = 0; i < TOTAL_CARDS; i++) {
            indices.add(i);
        }

        Collections.shuffle(indices);
        return indices;
    }

    // Call the server to update list of cards already selected
    private void serverCallGet(){

        ServerCall serverCall = new ServerCall(VuMarkRenderer.this, userId);
        serverCall.execute(Integer.toString(ServerCall.SERVER_VUMARK_DATA_GET), Integer.toString(userId), Integer.toString(contactId));

        Log.d(LOGTAG, "called VuMark data get");
    }

    // Update the server record with the new selected card
    private void serverCallUpdate(){

        ServerCall serverCall = new ServerCall(VuMarkRenderer.this, userId);
        serverCall.execute(Integer.toString(ServerCall.SERVER_VUMARK_DATA_UPDATE),
                Integer.toString(userId),
                Integer.toString(contactId),
                convertIntListToStr(selectedCardIndices),
                Integer.toString(discardTextureIndex),
                Integer.toString(resetGame ? userId : 0),
                Integer.toString(getCardsLeft()),
                "0");

        Log.d(LOGTAG, "called VuMark data update");
    }

    private List<Integer> convertStrToIntList(String str){

        List<String> cardStr = Arrays.asList(str.split(",|\"\""));

        Log.d(LOGTAG, "Received cards are: " + str);

        List<Integer> selected = new ArrayList<>();
        Log.d(LOGTAG, "In convertStrToIntList");
        if(cardStr.size() > 0) {
            for (String card : cardStr) {
                if(!card.equals("")) {
                    selected.add(Integer.valueOf(card));
                }
            }
        }

        return selected;
    }

    private String convertIntListToStr(List<Integer> selectedCards){
        String selectedCardsString = "";

        if(selectedCards.size() > 0) {
            selectedCardsString = String.valueOf(selectedCards.get(0));

            for (int i = 1; i < selectedCards.size(); i++) {
                selectedCardsString += "," + selectedCards.get(i);
            }
        }

        Log.d(LOGTAG, "Updated selected cards are: " + selectedCardsString);
        return selectedCardsString;
    }

    private void showRestartDialog(){
        if(!alertDialogShowing) {
            alertDialogShowing = true;
            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);

            builder.setTitle(R.string.restart_title).setMessage(R.string.restart_info);
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    resetGame();
                }
            });

            AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        }
    }

    private void showReshuffleDialog(){
        if(!reshuffleDialogShowing) {
            alertDialogShowing = true;
            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);

            builder.setTitle(R.string.reshuffle_title).setMessage(R.string.reshuffle_msg);
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    reshuffleDialogShowing = false;
                }
            });

            AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        }
    }

    @Override
    public void receiveServerCall(int type, boolean isSuccessful, String result){
        Log.d(LOGTAG, "In receiveServerCall");
        switch(type){
            case ServerCall.SERVER_VUMARK_DATA_GET:
                if(isSuccessful){
                    try {
                        Log.d(LOGTAG, "In vuMark Get Successful");

                        //Parse JSON object from result data
                        JSONObject vuMarkData = new JSONObject(result);

                        if(contactName != null){
                            int cardsLeft = vuMarkData.getInt("receiver_cards");

                            if(userId == vuMarkData.getInt("receiver_id")) {
                                cardsLeft = vuMarkData.getInt("sender_cards");
                            }

                            playerCards.setText(contactName + " has " + cardsLeft + " card(s)");
                        }

                        int gameRestart = vuMarkData.getInt("restart_flag");
                        if(gameRestart != 0){
                            if(resetGame == false && alertDialogShowing == false) {
                                showRestartDialog();
                            }
                        }else {
                            if(lock){
                                try {
                                    Thread.sleep(100);
                                }catch(java.lang.InterruptedException e){

                                }
                            }
                            if (!newDiscardVumarkId) {
                                discardTextureIndex = vuMarkData.getInt("discard_card_id");
                                String discardVuMarkId = discardVuMarkIds.get(discardVuMarkIds.size() - 1);
                                vuDictionary.put(discardVuMarkId, discardTextureIndex);
                            }

                            newDiscardVumarkId = false;
                            resetGame = false;
                            alertDialogShowing = false;

                            //Retrieve list of selected cards from vuMarkData
                            List<Integer> receivedCards = new ArrayList<>();
                            receivedCards.addAll(convertStrToIntList(vuMarkData.getString("selected_cards")));

                            Log.d(LOGTAG, "receivedCards are " + receivedCards.toString());

                            for (Integer card : receivedCards) {
                                if (!selectedCardIndices.contains(card)) {
                                    selectedCardIndices.add(card);
                                }
                            }

                            if (selectedCardIndices.size() >= TOTAL_CARDS || cardIndices.size() <= 0) {
                                reshuffleCardDeck();
                                return;
                            }

                            Log.d(LOGTAG, "selectedCards are " + selectedCardIndices.toString());

                            // Remove already selected cards from the list of cards that can be selected
                            cardIndices.removeAll(selectedCardIndices);

                            if (!serverCallVuMarks.isEmpty()) {

                                // Select a card from the list of available cards
                                Integer cardNum = cardIndices.get(0);
                                cardIndices.remove(0);

                                // Add the selected card to the list of selectedCards
                                selectedCardIndices.add(cardNum);

                                String vuMarkId = serverCallVuMarks.get(0);
                                serverCallVuMarks.remove(0);
                                vuDictionary.put(vuMarkId, cardNum);
                                currentlyTrackedVuMarks.add(vuMarkId);

                                if (selectedCardIndices.size() >= TOTAL_CARDS || cardIndices.size() <= 0) {
                                    reshuffleCardDeck();
                                }
                            }
                        }

                        serverCallHandler.removeCallbacks(serverCallRunnable);
                        serverCallHandler.postDelayed(serverCallRunnable, DELAY_TIME_MILLIS);

                        // Update the server with the latest list of selected cards
                        serverCallUpdate();

                        Log.d(LOGTAG, "exiting vuMark Get");

                    }catch(JSONException e){
                        e.printStackTrace();
                    }
                }else{
                    Log.d(LOGTAG, "In vuMark Get Failed");
                    Log.d(LOGTAG, "called data get finish");
                    mActivity.finish();
                }
                break;
            case ServerCall.SERVER_VUMARK_DATA_UPDATE:
                if(isSuccessful){
                    Log.d(LOGTAG, "===========  " + result);
                    if(result.equals("restart game")){
                        serverCallHandler.removeCallbacks(serverCallRunnable);
                        showRestartDialog();
                        serverCallHandler.postDelayed(serverCallRunnable, DELAY_TIME_MILLIS);
                    }

                    if(reshuffleCards){
                        reshuffleCards = false;
                        serverCallHandler.removeCallbacks(serverCallRunnable);
                        serverCallGet();
                    }
                    Log.d(LOGTAG, result);
                }
                break;
            case ServerCall.SERVER_VUMARK_DATA_REMOVE:
                if(isSuccessful){
//                    Log.d(LOGTAG, result);
                    Log.d(LOGTAG, "called data remove finish");
                    mActivity.finish();
                }
                break;
            case ServerCall.SERVER_USER_GET:
                if(isSuccessful){
                    try{
                        //Parse JSON object from result data
                        JSONObject userDetails = new JSONObject(result);

                        //Retrieve username from JSON object
                        contactName = userDetails.getString("username");

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                break;
        }
    }


    private void renderModel(float[] projectionMatrix, float[] viewMatrix, float[] modelMatrix, boolean isMainVuMark, int textureIndex)
    {
        float[] modelViewProjection = new float[16];

        // Combine device pose (view matrix) with model matrix
        Matrix.multiplyMM(modelMatrix, 0, viewMatrix, 0, modelMatrix, 0);

        // Do the final combination with the projection matrix
        Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelMatrix, 0);

        // activate the shader program and bind the vertex/normal/tex coords
        GLES20.glUseProgram(shaderProgramID);

        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                false, 0, mPlaneObj.getVertices());
        GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                GLES20.GL_FLOAT, false, 0, mPlaneObj.getTexCoords());

        GLES20.glEnableVertexAttribArray(vertexHandle);
        GLES20.glEnableVertexAttribArray(textureCoordHandle);

        // activate texture 0, bind it, and pass to shader
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                mTextures.get(textureIndex).mTextureID[0]);

        GLES20.glUniform1i(texSampler2DHandle, 0);
        GLES20.glUniform1f(calphaHandle, isMainVuMark ? blinkVumark(false) : 1.0f);

        // pass the model view matrix to the shader
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                modelViewProjection, 0);

        // finally draw the plane
        GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                mPlaneObj.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT,
                mPlaneObj.getIndices());

        // disable the enabled arrays
        GLES20.glDisableVertexAttribArray(vertexHandle);
        GLES20.glDisableVertexAttribArray(textureCoordHandle);
    }


    private float distanceSquared(Vec2F p1, Vec2F p2)
    {
        return (float) (Math.pow(p1.getData()[0] - p2.getData()[0], 2.0) +
                        Math.pow(p1.getData()[1] - p2.getData()[1], 2.0));
    }

    private float distance(float distanceSquared){
        return (float) Math.sqrt(distanceSquared);
    }


    public void setTextures(Vector<Texture> textures)
    {
        mTextures = textures;
    }

    private static final String hexTable = "0123456789abcdef";

    // Parse the VuMark value into a String
    private String instanceIdToValue(InstanceId instanceId)
    {
        ByteBuffer instanceIdBuffer = instanceId.getBuffer();
        byte[] instanceIdBytes = new byte[instanceIdBuffer.remaining()];
        instanceIdBuffer.get(instanceIdBytes);

        String instanceIdStr;
        switch(instanceId.getDataType())
        {
            case InstanceId.ID_DATA_TYPE.STRING:
                instanceIdStr = new String(instanceIdBytes, Charset.forName("US-ASCII"));
                break;

            case InstanceId.ID_DATA_TYPE.BYTES:
                StringBuilder instanceIdStrBuilder = new StringBuilder();

                for (int i = instanceIdBytes.length - 1; i >= 0; i--)
                {
                    byte byteValue = instanceIdBytes[i];

                    instanceIdStrBuilder.append(hexTable.charAt((byteValue & 0xf0) >> 4));
                    instanceIdStrBuilder.append(hexTable.charAt(byteValue & 0x0f));
                }

                instanceIdStr = instanceIdStrBuilder.toString();
                break;

            case InstanceId.ID_DATA_TYPE.NUMERIC:
                BigInteger instanceIdNumeric = instanceId.getNumericValue();
                Long instanceIdLong = instanceIdNumeric.longValue();
                instanceIdStr = Long.toString(instanceIdLong);
                break;

            default:
                return "Unknown";
        }

        return instanceIdStr;
    }

    private void setIsTargetCurrentlyTracked(TrackableResultList trackableResultList)
    {
        for(TrackableResult result : trackableResultList)
        {
            // Check the tracking status for result types
            // other than DeviceTrackableResult. ie: VuMarkTargetResult
            if (!result.isOfType(DeviceTrackableResult.getClassType()))
            {
                int currentStatus = result.getStatus();
                int currentStatusInfo = result.getStatusInfo();

                // The target is currently being tracked if the status is TRACKED|NORMAL
                if (currentStatus == TrackableResult.STATUS.TRACKED
                        || currentStatusInfo == TrackableResult.STATUS_INFO.NORMAL)
                {
                    mIsTargetCurrentlyTracked = true;
                    return;
                }
            }
        }

        mIsTargetCurrentlyTracked = false;
    }


    boolean isTargetCurrentlyTracked()
    {
        return mIsTargetCurrentlyTracked;
    }
}

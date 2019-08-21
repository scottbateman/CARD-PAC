package com.vuforia.engine.CoreSamples.videochatinterface;

import android.animation.Animator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.vuforia.engine.CoreSamples.app.VuMark.VuMark;
import com.vuforia.engine.CoreSamples.overlay.TextOverlay;
import com.vuforia.engine.CoreSamples.R;


/*
    This class represents the activity launched when a video call is started between two users.
    It will manage starting the UDP connection, as well as displaying the frames coming in from the server

    @author Nathan Sonier
 */
public class CallActivity extends AppCompatActivity implements ServerCaller {


    private ImageButton endCallButton, switchCameraButton, moreButton, muteButton, micButton, playButton;

    //This image view is used to render the incoming frames (from the server)
    public LocalDrawView localView;

    //This surface view is used to render the frames from the camera directly (local view, what the other user sees)
    public DrawView streamView;

    //The UDP Connection object that will manage the flow of data, as well as sending/receiving packets
    public UDPConnection connection;

    //The user's ID, as well as the other user's ID (the combination of both allows us to make this call unique)
    private int userId, contactId;

    //This instance of display metrics will help us retrieve the size of the user's screen (in pixels) to make the size of the local view relative to the screen size (based on local divider)
    private DisplayMetrics metrics;

    //Whether or not the "More.." menu is open (three dots on the left)
    private boolean moreOpen;

    //These flags determine whether or not to mute the speakers (or the microphone)
    private boolean volumeMuted, micMuted;

    Intent udpIntent;

    public static CallActivity callActivity;

    private UDPService udpService;

    boolean mBound = false;

    private ServiceConnection serviceConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        //Retrieve the image view from the layout and set its scale type to fill the screen
        localView = (LocalDrawView) findViewById(R.id.localView);
        localView.setWillNotDraw(false);

        //Set the on touch listener for the local view, so the user can click on the screen to interact with it.
        //If the user touches the local view (small camera view at the bottom left), the app will switch the stream camera view with the local view
        //If the user touches anywhere on the stream view, the app will check if they touched a QR Code (being displayed on the screen). If so the app will display a zoomed in version of the QR code (for readability)
        localView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //If touch is within local view, switch camera
                if(localView != null && localView.viewBounds != null && localView.viewBounds.contains((int)event.getX(), (int)event.getY())){
                    if(connection != null){
                        connection.switchView();
                    }
                }else{
                    //If touch is within stream view, check for qr code bounds

                    //Get the active QR codes (being displayed)
                    int[] ids = streamView.getActiveData();

                    //If there is any QR code being displayed, check to see if user touched them
                    if(ids != null){
                        //Iterate through list of active codes
                        for(int i = 0; i < ids.length;i++){

                            //Retrieve the text overlay instance from the view (mapped to its ID)
                            TextOverlay overlay = streamView.getOverlayFromMap(ids[i]);

                            //Retrieve the bounds associated with the overlay
                            RectF bounds = new RectF(overlay.getBounds().left, overlay.getBounds().top, overlay.getBounds().right, overlay.getBounds().bottom);

                            //If text is NOT rotated
                            if(!overlay.getIsRotated()){
                                //If drawn at center
                                if(overlay.getDrawAtCenter()){
                                    bounds.set(overlay.getPosition().x - (bounds.width()/2), overlay.getPosition().y - (bounds.height()/2), overlay.getPosition().x + (bounds.width()/2), overlay.getPosition().y + (bounds.height()/2));
                                }else{
                                    //If not drawn at center
                                    bounds.set(overlay.getPosition().x, overlay.getPosition().y, overlay.getPosition().x + bounds.width(), overlay.getPosition().y + bounds.height());
                                }

                                //Get the point where the user touched the screen
                                PointF offsetPoint = new PointF(event.getX(), event.getY());

                                //Offset the point on the screen (because of the margin gap that causes the coordinates to be incorrect
                                offsetPoint.x -= (localView.getWidth() - streamView.getWidth()) / 2;
                                offsetPoint.y -= (localView.getHeight() - streamView.getHeight()) / 2;

                                //Check if point is inside bounds
                                if(bounds.contains(offsetPoint.x, offsetPoint.y)){
                                    //Display a dialog containing the QR code's contents (and set the text font size to 25 by default)
                                    AlertDialog.Builder builder = new AlertDialog.Builder(CallActivity.this).setMessage(overlay.getContent());
                                    AlertDialog alert = builder.create();
                                    alert.show();
                                    TextView t = (TextView) alert.findViewById(android.R.id.message);
                                    t.setTextSize(25);
                                    break;
                                }
                            }
                        }
                    }
                }


                return false;
            }
        });

        //Retrieve the surface view from the layout
        streamView = (DrawView) findViewById(R.id.streamView);
        streamView.setWillNotDraw(false);

        //Initialize the display metrics object
        metrics = new DisplayMetrics();

        //Assign the default display metrics to the new variable
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        //Set the local view's fixed size to a portion of the screen size (calculated using the LOCAL_VIEW_DIVIDER constant)
        //localView.setLayoutParams().setFixedSize(width/LOCAL_VIEW_DIVIDER, height/LOCAL_VIEW_DIVIDER);

        //Set the UDP connection to null, to initialize it later
        connection = null;

        //Retrieve the extras passed in with this activity (user's ID and contact's ID)
        Bundle args = getIntent().getExtras();
        userId = args.getInt("userId", -1);
        contactId = args.getInt("contactId", -1);

        //Retrieve the button to end the call from the layout, and set it's on click listener to call the endCall method
        endCallButton = (ImageButton) findViewById(R.id.endCallButton);
        endCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endCall();
            }
        });

        //Retrieve button to switch between the front and back camera from the view
        switchCameraButton = (ImageButton) findViewById(R.id.switchCameraButton);
        switchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(udpService != null){
                    udpService.requestSwitchCamera();
                }
            }
        });

        //Set the default state of the extras menu to be closed by default
        moreOpen = false;

        //Retrieve the three extra buttons from the view
        moreButton = (ImageButton) findViewById(R.id.moreButton);
        muteButton = (ImageButton) findViewById(R.id.muteButton);
        micButton = (ImageButton) findViewById(R.id.micButton);

        //Set the mute speakers and mute microphone flags to false by default
        volumeMuted = false;
        micMuted = false;

        //Set the on click listener of the open button (three dots) so that it reveals the two other buttons when pressed (and hides them when pressed again)
        moreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(moreOpen){
                    moreOpen = !moreOpen;
                    micButton.animate()
                            .alpha(0.0f)
                            .setListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animation) {

                                }

                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    micButton.setVisibility(View.GONE);
                                    muteButton.animate()
                                            .alpha(0.0f)
                                            .setListener(new Animator.AnimatorListener() {
                                                @Override
                                                public void onAnimationStart(Animator animation) {

                                                }

                                                @Override
                                                public void onAnimationEnd(Animator animation) {
                                                    muteButton.setVisibility(View.GONE);
                                                }

                                                @Override
                                                public void onAnimationCancel(Animator animation) {

                                                }

                                                @Override
                                                public void onAnimationRepeat(Animator animation) {

                                                }
                                            });
                                }

                                @Override
                                public void onAnimationCancel(Animator animation) {

                                }

                                @Override
                                public void onAnimationRepeat(Animator animation) {

                                }
                            });
                }else{
                    moreOpen = !moreOpen;

                    muteButton.setVisibility(View.VISIBLE);
                    muteButton.setAlpha(0.0f);
                    muteButton.animate()
                            .setDuration(100)
                            .alpha(1.0f)
                            .setListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animation) {

                                }

                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    micButton.setVisibility(View.VISIBLE);
                                    micButton.setAlpha(0.0f);
                                    micButton.animate()
                                            .setDuration(100)
                                            .alpha(1.0f)
                                            .setListener(null);
                                }

                                @Override
                                public void onAnimationCancel(Animator animation) {

                                }

                                @Override
                                public void onAnimationRepeat(Animator animation) {

                                }
                            });

                }
            }
        });

        //Set the on click listener of the mute speakers button so that it mutes/unmutes the speakers depending on the flag
        muteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(udpService != null){
                    if(volumeMuted && udpService.requestUnmuteSpeakers()){
                        volumeMuted = !volumeMuted;
                        muteButton.setImageResource(R.drawable.ic_baseline_volume_up_24px);
                    }else if(!volumeMuted && udpService.requestMuteSpeakers()){
                        volumeMuted = !volumeMuted;
                        muteButton.setImageResource(R.drawable.ic_baseline_volume_off_24px);
                    }
                }
            }
        });

        //Set the on click listener of the mute microphone button so that it mutes/unmutes the microphone depending on the flag
        micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(udpService != null){
                    if(micMuted && udpService.requestUnmuteMic()){
                        micMuted = !micMuted;
                        micButton.setImageResource(R.drawable.ic_baseline_mic_24px);
                    }else if(!micMuted && udpService.requestMuteMic()){
                        micMuted = !micMuted;
                        micButton.setImageResource(R.drawable.ic_baseline_mic_off_24px);
                    }
                }
            }
        });


        callActivity = this;

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                UDPService.LocalBinder binder = (UDPService.LocalBinder) service;
                udpService = binder.getService();
                mBound = true;

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //Initialize the play cards button
                        playButton = findViewById(R.id.playCardGameButton);
                        playButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view){

                                Intent intent = new Intent(CallActivity.this, VuMark.class);
                                intent.putExtra("userId", userId);
                                intent.putExtra("contactId", contactId);
                                startActivity(intent);
                            }
                        });
                    }
                }, 5000);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mBound = false;
            }
        };

        String serverAddress = GlobalVariablesClass.getServerAddress().replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)","");

        udpIntent = new Intent(CallActivity.this, UDPService.class);
        udpIntent.putExtra("server", serverAddress);
        udpIntent.putExtra("isHosting", true);
        udpIntent.putExtra("userId", userId);
        udpIntent.putExtra("contactId", contactId);
        startService(udpIntent);
        bindService(udpIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        //Initialize the UDP connection instance, starting the connection process (using the hcidev.cs.unb.ca address by default)
//        connection = new UDPConnection(CallActivity.this, "hcidev.cs.unb.ca", true, userId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //endCall();
        if(udpService != null)
            udpService.stopCamera();
       //connection.stopCamera();
        Toast.makeText(this, "ON PAUSE", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();

//        deleteVuMarkDataServerCall();

        //connection.startCamera();
        if(udpService != null)
            udpService.startCamera();
        Toast.makeText(this, "ON RESUME", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy(){
        Log.d("CallActivity", "onDestroy Called");

        unbindService(serviceConnection);
        mBound = false;

        deleteVuMarkDataServerCall();

        if(VuMark.getVuMarkInstance() != null) {
            VuMark.getVuMarkInstance().finish();
        }

        stopService(udpIntent);

        super.onDestroy();
    }

    /*
        This method is called when the current call has been ended, and will let the server know to erase the session
     */
    public void endCall(){
        connection = udpService.getConnection();
        Log.d("CallActivity", "endCall Called");
        if(connection != null){
            connection.callEnded = true;
        }

        //Call server to quit call (remove the session from the database)
        ServerCall serverCall = new ServerCall(CallActivity.this, userId);
        serverCall.execute(Integer.toString(ServerCall.SERVER_SESSIONS_REMOVE), Integer.toString(userId), Integer.toString(contactId));
    }

    public void deleteVuMarkDataServerCall(){
        ServerCall serverCall = new ServerCall(CallActivity.this, userId);
        serverCall.execute(Integer.toString(ServerCall.SERVER_VUMARK_DATA_REMOVE), Integer.toString(userId), Integer.toString(contactId));
    }

    @Override
    /*
        This method handles the responses from the server for calls that were made from this class
        @param type the type of response (this matches the type of the request sent that triggered this response)
        @param isSuccessful this boolean represents whether or not the server request was completed successfully (true) or not (false)
        @param result the resulting data from the request (passed in through a string, but could be a multitude of different data types)
     */
    public void receiveServerCall(int type, boolean isSuccessful, String result) {

        connection = udpService.getConnection();

        //If the server is responding to the request to remove the session (end the call) and it is successful, shut down the connection and return to the main activity
        if(type == ServerCall.SERVER_SESSIONS_REMOVE && isSuccessful){
            //Stop UDPConnection completely
            //If connection is started, shut it down (and nullify it)
            if(connection != null){
                Log.d("CallActivity", "receiveServerCall");
                connection.shutDown();
                connection = null;
            }

            //Return to MainActivity
            this.finish();
        }
    }
}

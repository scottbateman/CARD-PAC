package com.vuforia.engine.CoreSamples.videochatinterface;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

/*
    This class will work with the UDPConnection class to take in audio data (byte arrays) and play it back to the user
    It will also record new audio and send it to UDPConnection to be sent over the internet

    @author Nathan Sonier
 */
public class AudioRecorder implements Runnable{

    //Reference to activity that was used to call this class
    private CallActivity activity;

    //Reference to UDPConnection class that initialized this class
    private UDPConnection connection;

    //AudioRecord instance that will be used to record the microphone's audio
    private AudioRecord audioRecord;

    //Byte Buffer that will hold the recorded audio until it is sent to UDPConnection
    private byte[] buffer;

    //Whether or not the class is recording the microphone (this halts the operations done in this class)
    private boolean isRecording;

    //Whether or not the user muted the incoming stream, or if they muted their own microphone
    private boolean isMuted, isMicMuted;

    //Byte array that will hold the audio received from the other host (that needs to be played)
    public byte[] audioReceived;

    //AudioTrack instance that will handle playing the byte array received from the other host (above)
    private AudioTrack audioTrack;

    //This class will do the actual work to play the audio track received
    private AudioTrackThread audioTrackThread;

    //This thread will be used to run the AudioTrackThread created above
    private Thread audioThread;

    //Buffer size for the audio that we will record
    private final int BUFFER_SIZE = 4000;

    //Sample rate for the audio that we will record
    private final int SAMPLE_RATE = 16000;

    //Audio Manager object, that will help us manage settings for both recording and playback of audio data
    private AudioManager audioManager;

    /*
        Constructor for AudioRecorder
        @param activityIn the activity that created this instance
        @param connectionIn the UDPConnection class that is currently being used (to get and send data)

        @return the new AudioRecorder instance
     */
    public AudioRecorder(CallActivity activityIn, UDPConnection connectionIn){
        //Initialize instance variables
        activity = activityIn;
        connection = connectionIn;
        audioRecord = null;
        buffer = null;
        isRecording = false;
        audioReceived = null;
        audioTrackThread = null;
        audioThread = null;
        isMuted = false;
        isMicMuted = false;
    }

    @Override
    /*
        This method will be called once the thread holding this instance will be started
        This method takes care of recording audio data, as well as checking if there is audio data ready to be played
     */
    public void run() {
        //Check to make sure the application has the proper permission to record audio from the microphone
        if(ActivityCompat.checkSelfPermission(activity.getApplicationContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            //Get the audio manager from the system services
            audioManager = (AudioManager)activity.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

            //Set audio manager mode to MODE_IN_COMMUNICATION (to help us with echo cancellation)
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

            //Reroute audio from the call to the phone's speakers
            audioManager.setSpeakerphoneOn(true);

            //Create a new Audio Record instance (using the settings defined in the instance variables
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)*10);
        }else{
            //If phone does not have permission to record audio, cancel the thread (return nothing)
            return;
        }

        //Make sure the audio record instance has been properly initialized
        if(audioRecord != null && audioRecord.getState() != AudioRecord.STATE_INITIALIZED){
            //If the audio record instance has not been properly initialized, notify the user with a toast and return. (The rest of the call will still work)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, "NOT INIT", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        //Initialize buffer
        buffer = new byte[AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 10];

        //Begin recording the audio from the audio source
        audioRecord.startRecording();
        isRecording = true;

        //Initialize and start the audio track instance that we will then use to play the incoming audio
        audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 10, AudioTrack.MODE_STREAM, audioRecord.getAudioSessionId());
        audioTrack.play();

        //Initialize the audio track thread, and start the thread that will do the work
        audioTrackThread = new AudioTrackThread();
        audioThread = new Thread(audioTrackThread);
        audioThread.start();

        //While this class is "recording", keep reading from the audio source to fill the buffer, and then send it to the UDPConnection class (to be sent)
        while(isRecording){
            int read = BUFFER_SIZE;

            if(!isMicMuted){
                //If the Audio Record instance is not null, read bytes from the microphone
                if(audioRecord != null){
                    read = audioRecord.read(buffer, 0, buffer.length);
                }
                connection.receiveFromMicrophone(buffer, read);
            }

            //At the end of each iteration, check if there is audio to be played (if so, play it)
            listen();
        }
    }

    //This method will set the state of this class (recording or not)
    public void setRecording(boolean isRecordingIn){
        isRecording = isRecordingIn;
    }

    //This method stops the execution of this class, and clears/releases all of its data/objects
    public void stop(){
        if(audioManager != null){
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        if(audioRecord != null){
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        if(audioTrack != null){
            if(audioTrack.getState() != AudioTrack.STATE_UNINITIALIZED) {
                audioTrack.stop();
                audioTrack.release();
            }
        }
    }

    //This method will check if there is audio ready to be played, and will send it to the thread (to be played)
    public void listen(){
        if(audioReceived != null){
            audioTrackThread.audioToPlay = audioReceived;
        }
    }

    //This method sets the mute speakers flag to true
    public void muteStream(){
        isMuted = true;
    }

    //This method sets the mute microphone flag to true
    public void muteMic(){
        isMicMuted = true;
    }

    //This method sets the mute speakers flag to false
    public void unmuteStream(){
        isMuted = false;
    }

    //This method sets the mute microphone flag to false
    public void unmuteMic(){
        isMicMuted = false;
    }

    //This thread is responsible for running the audio playing operation
    private class AudioTrackThread implements Runnable{

        //Audio data that still needs to be played
        public byte[] audioToPlay;

        public AudioTrackThread(){
            audioToPlay = null;
        }

        @Override
        public void run() {
            //While this class is running, and there is audio to play, play it to the device speakers
            while(isRecording){
                //If there is audio to play, flush the current audio track buffer (reset the playback) and start playing the new data
                if(audioToPlay != null && audioTrack != null && !isMuted){
                    try{
                        audioTrack.flush();
                        audioTrack.write(audioToPlay, 0, audioToPlay.length);
                        audioTrack.flush();
                        audioToPlay = null;
                    }catch(IllegalStateException e){
                        Log.d("AudioThread", "" + e.getMessage());
                    }
                }
            }
        }
    }
}

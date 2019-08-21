package com.vuforia.engine.CoreSamples.videochatinterface;

import android.content.Intent;
import android.app.Service;
import android.util.Log;
import android.os.Binder;
import android.os.IBinder;
import android.os.Bundle;
import android.widget.Toast;

public class UDPService extends Service {

    private UDPConnection connection;
    private IBinder mBinder = new LocalBinder();
    Intent udpIntent;

    public class LocalBinder extends Binder {
        UDPService getService(){
            return UDPService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent){
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Toast.makeText(this, "Service started by user.", Toast.LENGTH_LONG).show();

        udpIntent = intent;

        Bundle extras = intent.getExtras();
        String server = extras.getString("server");
        boolean isHosting = extras.getBoolean("isHosting");
        int userId = extras.getInt("userId");

        connection = new UDPConnection(CallActivity.callActivity, server, isHosting, userId);


        return START_STICKY;
    }

    public void startCamera(){
        if(connection != null)
            connection.startCamera();
    }

    public void stopCamera(){
        if(connection != null)
            connection.stopCamera();
    }

    public UDPConnection getConnection(){
        return connection;
    }

    public boolean requestUnmuteSpeakers(){
        if(connection != null)
            return connection.requestUnmuteSpeakers();
        else
            return false;
    }

    public boolean requestMuteSpeakers(){
        if(connection != null)
            return connection.requestMuteSpeakers();
        else
            return false;
    }

    public boolean requestUnmuteMic(){
        if(connection != null)
            return connection.requestUnmuteMic();
        else
            return false;
    }

    public boolean requestMuteMic(){
        if(connection != null)
            return connection.requestMuteMic();
        else
            return false;
    }

    public void requestSwitchCamera(){
        if(connection != null){
            connection.requestSwitchCamera();
        }
    }

    @Override
    public void onDestroy() {

        Log.d("UDPService", "onDestroy Called");
        stopSelf();

        super.onDestroy();
        Toast.makeText(this, "Service destroyed by user.", Toast.LENGTH_LONG).show();
    }

}

package com.vuforia.engine.CoreSamples.videochatinterface;

public interface ServerCaller {

    void receiveServerCall(int type, boolean isSuccessful, String result);

}
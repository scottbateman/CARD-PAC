package com.vuforia.engine.CoreSamples.videochatinterface;

public final class GlobalVariablesClass {

    private static String serverAddress = null;

    private GlobalVariablesClass(){}

    public static void setServerAddress(String address){
        serverAddress = address;
    }

    public static String getServerAddress(){
        return serverAddress;
    }
}

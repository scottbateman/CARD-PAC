package com.vuforia.engine.CoreSamples.videochatinterface;

/**
 * This class represents a basic session request (call request)
 *
 * @author Nathan Sonier
 */
class SessionRequest {

    /**
     * The user's ID (user who sent the session request)
     */
    private int userId;

    /**
     * The user's name (user who sent the session request)
     */
    private String name;

    /**
     * Constructs a SessionRequest instance and returns it
     *
     * @param idIn the sender's ID
     * @param nameIn the sender's name
     */
    public SessionRequest(int idIn, String nameIn){
        userId = idIn;
        name = nameIn;
    }

    public int getUserID(){
        return userId;
    }

    public String getName(){
        return name;
    }

    public void setUserId(int idIn){
        userId = idIn;
    }

    public void setName(String nameIn){
        name = nameIn;
    }
}

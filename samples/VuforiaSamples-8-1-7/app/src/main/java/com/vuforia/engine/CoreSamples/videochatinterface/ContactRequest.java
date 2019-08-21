package com.vuforia.engine.CoreSamples.videochatinterface;

/*
    This class represents a single contact request in the user's list of contact requests.
    It contains information about who sent the request (ID), as well as their name.

    @author Nathan Sonier
 */
public class ContactRequest {

    //The sender's user ID
    private int senderID;

    //The sender's full name
    private String name;

    /*
        This constructor creates a simple Contact Request instance, with an ID and a name
        @param idIn the sender's ID
        @param nameIn the sender's full name
     */
    public ContactRequest(int idIn, String nameIn){
        senderID = idIn;
        name = nameIn;
    }

    /*
        This method returns the sender's user ID

        @return the sender's ID
     */
    public int getID(){
        return senderID;
    }

    /*
        This method returns the sender's full name

        @return the sender's name
     */
    public String getName(){
        return name;
    }

    /*
        This method sets a new user ID for the sender
        @param idIn the new ID to be set
     */
    public void setID(int idIn){
        senderID = idIn;
    }

    /*
        This method sets a new name for the sender
        @param nameIn the new name to be set
     */
    public void setName(String nameIn){
        name = nameIn;
    }

}

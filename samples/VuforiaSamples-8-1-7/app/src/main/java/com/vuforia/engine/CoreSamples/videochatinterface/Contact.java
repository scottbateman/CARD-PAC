package com.vuforia.engine.CoreSamples.videochatinterface;

/*
    This class represents a single contact in a user's list of contacts. It contains basic information about the contact

    @author Nathan Sonier
 */
public class Contact {

    //The contact's user ID (on the server database)
    private int userID;

    //The contact's full name (not username)
    private String name;

    /*
        This constructor creates a basic Contact object, with a name and an ID
        @param idIn the contact's ID
        @param nameIn the contact's full name
     */
    public Contact(int idIn, String nameIn){
        userID = idIn;
        name = nameIn;
    }

    /*
        This method changes this contact's ID to a new ID
        @param newId the new ID of the contact
     */
    public void setUserID(int newId){
        userID = newId;
    }

    /*
        This method changes this contact's name to a new name
        @param newName the new name of the contact
     */
    public void setName(String newName){
        name = newName;
    }

    /*
        This method returns the contact's ID

        @return the contact's ID
     */
    public int getUserID(){
        return userID;
    }

    /*
        This method returns the contact's name

        @return the contact's name
     */
    public String getName(){
        return name;
    }
}

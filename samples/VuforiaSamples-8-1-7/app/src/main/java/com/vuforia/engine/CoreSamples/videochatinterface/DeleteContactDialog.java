package com.vuforia.engine.CoreSamples.videochatinterface;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

/*
    This class represents a dialog that asks the user if they want to remove a specific contact

    @author Nathan Sonier
 */
public class DeleteContactDialog extends DialogFragment {

    //The class that called this dialog
    private ContactDialog caller;

    //The contact instance that holds the contact details for the currently displayed user
    private Contact contact;

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        //Create a new Alert Dialog Builder object
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        //Retrieve the arguments passed in with this dialog
        Bundle args = getArguments();

        //Retrieve the contact object
        contact = new Contact(args.getInt("id"), args.getString("name"));

        //Set the builder's message, as well as both the positive and negative buttons
        builder.setMessage("Confirm to remove " + contact.getName())
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((MainActivity)getActivity()).deleteContact(contact);
                        caller.dismiss();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        //Create and return the dialog
        return builder.create();
    }

    /*
        This method sets the caller variable to the class that called this dialog
        @param cIn the class that called this dialog (can only be called by a ContactDialog object)
     */
    public void setCaller(ContactDialog cIn){
        caller = cIn;
    }
}

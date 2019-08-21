package com.vuforia.engine.CoreSamples.videochatinterface;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.vuforia.engine.CoreSamples.R;

/*
    This class represents a contact details dialog box

    @author Nathan Sonier
 */
public class ContactDialog extends DialogFragment {

    //Activity that called this dialog
    private MainActivity caller;

    //Contact instance that contains details about the contact being shown in the dialog box
    private Contact contact;

    //ID of the user looking at the contact details
    private int userId;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState){
        //Create the Alert Dialog Builder object
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        //Get the layout inflater to create the custom layout used by the dialog
        LayoutInflater inflater = requireActivity().getLayoutInflater();

        //Retrieve arguments passed in using the bundle
        Bundle args = getArguments();

        //Retrieve the contact object (by retrieving the individual variables) and the user's ID
        contact = new Contact(args.getInt("contactId"), args.getString("name"));
        userId = args.getInt("userId");

        //Inflate the Constraint Layout that is defined in the layout folder
        ConstraintLayout c = (ConstraintLayout) inflater.inflate(R.layout.contact_details, null);

        //Retrieve the view items from the layout (ImageButton and TextView)
        ImageButton callButton = (ImageButton) c.getViewById(R.id.callButton);
        ImageButton removeButton = (ImageButton) c.getViewById(R.id.removebutton);
        final TextView contactName = (TextView) c.getViewById(R.id.contactName);

        //Set the contactName text view to show the contact's name
        contactName.setText(contact.getName());

        //Set the on click listener of the call button to call the server to send a session request
        callButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            Log.d("TEST", "Call " + contact.getName() + " (id: " + contact.getUserID() + ") from user id: " + userId);
            caller.serverSendSessionRequest(userId, contact.getUserID(), contact.getName());
            dismiss();
            }
        });

        //Set the on click listener of the remove button to open a new dialog box (to confirm the removal of that contact)
        removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DeleteContactDialog dialog = new DeleteContactDialog();
                Bundle args = new Bundle();
                args.putInt("id", contact.getUserID());
                args.putString("name", contact.getName());
                dialog.setCaller(ContactDialog.this);
                dialog.setArguments(args);
                dialog.show(getFragmentManager(), "");
            }
        });

        //Set the dialog view to the layout
        builder.setView(c);

        //Create and return the dialog box
        return builder.create();
    }

    /*
        This method sets the calling activity's reference to a different activity (so we know who called this dialog)
        @param activityIn the activity that called this dialog
     */
    public void setCaller(MainActivity activityIn){
        caller = activityIn;
    }
}

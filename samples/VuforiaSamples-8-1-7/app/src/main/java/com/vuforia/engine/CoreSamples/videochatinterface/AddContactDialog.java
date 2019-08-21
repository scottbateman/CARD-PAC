package com.vuforia.engine.CoreSamples.videochatinterface;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.DialogFragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.vuforia.engine.CoreSamples.R;

/*
    This class builds a dialog box that allows the user to send contact requests to other users
    @author Nathan Sonier
 */
public class AddContactDialog extends DialogFragment {

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        //Create the Alert Dialog Builder object
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        //Get the layout inflater to create the custom layout used by the dialog
        LayoutInflater inflater = requireActivity().getLayoutInflater();

        //Retrieve arguments passed in using the bundle
        Bundle args = getArguments();

        //Retrieve the user's ID (that launched the dialog)
        final int userID = args.getInt("userId");

        //Inflate the Constraint Layout that is defined in the add_contact_dialog.xml file (layout folder)
        ConstraintLayout c = (ConstraintLayout) inflater.inflate(R.layout.add_contact_dialog, null);

        //Retrieve the EditText object created inside the Constraint Layout created above
        final EditText usernameInput = (EditText) c.getViewById(R.id.usernameText);

        //Add on key listener (so that the user can press enter to send the contact request)
        usernameInput.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if(keyCode == KeyEvent.KEYCODE_ENTER){
                    //Call the ServerCall class (wrapped in MainActivity) to make a call to server to send the contact request
                    ((MainActivity)getActivity()).serverSendContactRequest(userID, usernameInput.getText().toString());

                    //Dismiss the dialog (may be redundant)
                    dismiss();
                }

                return false;
            }
        });

        //Build the dialog
        builder.setMessage("Please enter username to add: ")
                .setView(c)
                .setPositiveButton("Add Contact", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Call the ServerCall class (wrapped in MainActivity) to make a call to server to send the contact request
                        ((MainActivity)getActivity()).serverSendContactRequest(userID, usernameInput.getText().toString());

                        //Dismiss the dialog (may be redundant)
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Cancel the dialog box (may also be redundant)
                        dialog.cancel();
                    }
                });

        //Return the newly built dialog with the specified settings
        return builder.create();
    }
}

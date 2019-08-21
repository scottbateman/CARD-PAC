package com.vuforia.engine.CoreSamples.videochatinterface;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import com.vuforia.engine.CoreSamples.R;

/*
    This class represents a dialog box that is used to accept/decline an incoming call (if being called), or cancel the call (if calling)
 */
public class CallDialog extends DialogFragment {

    //The MainActivity instance that called this dialog to open
    private MainActivity caller;

    //The calling user's ID, as well the ID of the user being called
    private int userId, contactId;

    //The name of the user being called (for UI purposes)
    private String name;

    @Override
    public void onStart() {
        super.onStart();

        //Retrieve the dialog instance and set it so it cannot be closed by tapping outside of the dialog box (have to use buttons)
        Dialog dialog = getDialog();
        if(dialog != null){
            dialog.setCanceledOnTouchOutside(false);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();

        //Retrieve the arguments passed in to this dialog instance
        Bundle args = getArguments();

        //Retrieve the sending argument, which represents whether or not the user that launched this dialog is the one calling, or is the one being called (changes which view appears)
        boolean sending = args.getBoolean("sending", false);

        if(sending){
            //Retrieve the user's ID as well as the other user's ID from the arguments
            userId = args.getInt("userId");
            contactId = args.getInt("contactId");

            //Inflate the view defined in the layout folder, and retrieve the view items from it (FloatingActionButton and TextView)
            ConstraintLayout c = (ConstraintLayout) inflater.inflate(R.layout.calling_dialog, null);
            FloatingActionButton cancelCallButton = (FloatingActionButton) c.findViewById(R.id.cancelCallButton);
            TextView title = (TextView) c.findViewById(R.id.title);

            //Set the title view to show the user who they are calling
            title.setText("Calling " + args.getString("name", "") + "...");

            //Set the on click listener for the cancel button to call the server to delete the session request (and dismiss the call dialog)
            cancelCallButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    caller.state = caller.STATE_IDLE;
                    caller.serverDeleteSessionRequest(userId, contactId);
                    dismiss();
                }
            });

            //Set the overall view of the dialog box
            builder.setView(c)
                    .setCancelable(false);
        }else{
            //Retrieve the user's ID as well as the other user's ID (and their name) from the arguments
            userId = args.getInt("userId");
            contactId = args.getInt("contactId");
            name = args.getString("name", "Unknown");

            //Inflate the view defined in the layout folder, and retrieve the view items from it (FloatingActionButton and TextView)
            ConstraintLayout c = (ConstraintLayout) inflater.inflate(R.layout.receiving_call_dialog, null);
            FloatingActionButton cancelCallButton = (FloatingActionButton) c.findViewById(R.id.cancelCallButton);
            FloatingActionButton acceptCallButton = (FloatingActionButton) c.findViewById(R.id.acceptCallButton);
            TextView title = (TextView) c.findViewById(R.id.title);

            //Set the title view to show the name of the user that is calling
            title.setText(name + " is calling...");

            //Set the on click listener for the cancel button to call the server to delete the session request, and dismiss the call dialog
            cancelCallButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    caller.state = caller.STATE_IDLE;
                    caller.serverDeleteSessionRequest(userId, contactId);
                    dismiss();
                }
            });

            //Set the on click listener for the accept button to call the server to accept the session request (which will create a session and set it up), and dismiss the call dialog
            acceptCallButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    caller.state = caller.STATE_IN_CALL;
                    caller.serverAcceptSessionRequest(userId, contactId, name);
                    dismiss();
                }
            });

            //Set the overall view of the dialog box
            builder.setView(c)
                    .setCancelable(false);
        }

        //Create and return the dialog box
        return builder.create();
    }

    /*
        This method takes in an instance of MainActivity, which represents who called this dialog to be created
        @param activityIn the activity that called the creation of this dialog box
     */
    public void setCaller(MainActivity activityIn){
        caller = activityIn;
    }

}

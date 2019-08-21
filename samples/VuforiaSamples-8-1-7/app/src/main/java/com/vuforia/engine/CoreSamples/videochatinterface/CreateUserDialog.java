package com.vuforia.engine.CoreSamples.videochatinterface;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import com.vuforia.engine.CoreSamples.R;

/*
    This class represents a dialog that is used to create new user accounts

    @author Nathan Sonier
 */
public class CreateUserDialog extends DialogFragment implements ServerCaller{

    //The class that called to open this dialog (must implement the ServerCaller interface)
    private ServerCaller caller;

    //The two textboxes that will contain the new username and name
    private TextView username, name;

    @Override
    public void onStart() {
        super.onStart();

        //Retrieve the Alert Dialog object
        AlertDialog dialog = (AlertDialog)getDialog();
        if(dialog != null){
            //Change the dialog so it can't be closed by tapping outside of the dialog box
            dialog.setCanceledOnTouchOutside(false);

            //Set the on click listener of the "create" button to call the server and create the account
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ServerCall serverCall = new ServerCall(CreateUserDialog.this, -1);
                    serverCall.execute(Integer.toString(ServerCall.SERVER_USER_CREATE),username.getText().toString(), name.getText().toString());

                }
            });
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        //Create a new Alert Dialog Builder
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        //Retrieve the layout inflater to inflate a custom layout
        LayoutInflater inflater = requireActivity().getLayoutInflater();

        //Inflate the custom layout defined in the layout folder
        ConstraintLayout c = (ConstraintLayout) inflater.inflate(R.layout.create_user_dialog, null);

        //Retrieve the view items from the custom layout (TextView)
        username = (TextView) c.findViewById(R.id.username);
        name = (TextView) c.findViewById(R.id.name);

        //Set the view in the builder, as well as the title, and both the positive and negative buttons
        builder.setView(c)
                .setTitle("Create new user")
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        username.setText("");
                        name.setText("");
                    }
                })
                .setCancelable(false);

        //Create and return the new dialog box
        return builder.create();
    }

    /*
        This method sets the caller variable to the class that called this dialog
        @param activityIn the class that called the dialog
     */
    public void setCaller(ServerCaller activityIn){
        caller = activityIn;
    }


    @Override
    /*
        This method handles responses from the server
        @param type the type of response that was received (the list of possible types are defined in ServerCall - in the final instance variables)
        @param isSuccessful this indicates whether or not the request was successful
        @param result this holds the data returned alongside the response
     */
    public void receiveServerCall(int type, boolean isSuccessful, String result) {
        if(isSuccessful){
            dismiss();
        }else{

        }
    }
}

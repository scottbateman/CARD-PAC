package com.vuforia.engine.CoreSamples.videochatinterface;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.vuforia.engine.CoreSamples.R;

/*
    This Activity class allows the user to enter their credentials to log in. It also allows new users to create a new account if they do not have one

    @author Nathan Sonier
 */
public class LoginActivity extends AppCompatActivity implements ServerCaller {

    //These hold the username and password entered by the user at the moment they press Log In
    private EditText usernameField, passwordField;

    //This text view holds the error message (if any occurs during login)
    private TextView errorText;

    //The loginButton allows the user to log in with their credentials, and the createAccountButton allows the user to create new credentials (and then log in)
    private Button loginButton, createAccountButton, setServerButton;

    //This progress bar will appear once the user attempt to log in, to show that the app is loading
    private ProgressBar progressBar;

    //This dialog will allow the user to input a username/name to create credentials
    private CreateUserDialog createUserDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        errorText = (TextView) findViewById(R.id.errorText);

        //Retrieve the login button from the view
        loginButton = (Button) findViewById(R.id.loginButton);

        //Set the on click listener of the login button to call the server to request a login
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(GlobalVariablesClass.getServerAddress() == null){
                    errorText.setText("Server address not set!");
                } else {
                    loginButton.setVisibility(View.GONE);
                    progressBar.setVisibility(View.VISIBLE);
                    login(usernameField.getText().toString(), "");
                }
            }
        });

        //Retrieve the create account button from the view
        createAccountButton = (Button) findViewById(R.id.createAccountButton);

        //Set the on click listener of the create account button to create a "Create New User" dialog
        createAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(GlobalVariablesClass.getServerAddress() == null){
                    errorText.setText("Server address not set!");
                } else {
                    createUserDialog = new CreateUserDialog();
                    createUserDialog.setCaller(LoginActivity.this);
                    if (!createUserDialog.isAdded()) {
                        createUserDialog.show(getSupportFragmentManager(), "");
                    }
                }
            }
        });

        setServerButton = findViewById(R.id.setServerButton);

        setServerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText editText = new EditText(LoginActivity.this);
                AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this);

                builder.setTitle(R.string.server_address_title).setMessage(R.string.server_address_msg);
                builder.setView(editText);
                builder.setPositiveButton(R.string.set, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        GlobalVariablesClass.setServerAddress(editText.getText().toString());
                    }
                });
                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        //Retrieve the username field EditText object from the view
        usernameField = (EditText) findViewById(R.id.usernameField);

        //Retrieve the password field EditText object from the view
        passwordField = (EditText) findViewById(R.id.passwordField);

        passwordField.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if(GlobalVariablesClass.getServerAddress() == null){
                    errorText.setText("Server Address not set!");
                } else {
                    if (keyCode == KeyEvent.KEYCODE_ENTER) {
                        loginButton.setVisibility(View.GONE);
                        progressBar.setVisibility(View.VISIBLE);
                        login(usernameField.getText().toString(), "");
                    }
                }
                return false;
            }
        });

        //Retrieve the progress bar object from the view
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        //Completely hide the progress bar at launch
        progressBar.setVisibility(View.GONE);
    }

    /*
        This method send a request to the server to create a login user the credentials specified (currently no password is needed)
        @param username the user's username
        @param password the user's password
     */
    private void login(String username, String password){

        errorText.setText("");

        if(!isOnline()){
            loginButton.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            errorText.setText("No internet connection");
            return;
        }

        ServerCall createUser = new ServerCall(this, -1);
        createUser.execute(Integer.toString(ServerCall.SERVER_USER_LOGIN), username);
    }

    @Override
    /*
        This method handles the responses received from the server calls we make
        @param type the type of response that was received (the list of possible types are defined in ServerCall - in the final instance variables)
        @param isSuccessful this indicates whether or not the request was successful
        @param result this holds the data returned alongside the response
     */
    public void receiveServerCall(int type, boolean isSuccessful, String result) {
        //If the server is responding to a server login request
        if(type == ServerCall.SERVER_USER_LOGIN){
            if(isSuccessful){
                //And it is successful, start a new MainActivity (with the logged in user)
                Log.d("SERVER RESPONSE", "" + result);
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra("id", Integer.parseInt(result));
                startActivityForResult(intent, 0);
            }else{
                //Otherwise, reset the login button, and wait for different credentials
                Log.d("SERVER RESPONSE", "" + result);
                errorText.setText(result);
                progressBar.setVisibility(View.GONE);
                loginButton.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    /*
        This method will be called once the user is done in the MainActivity (so when they log out). Upon being called, it resets the activity to clear data
        @param requestCode the code set when the activity was first started
        @param resultCode the code set now that the activity is finished
        @param data the data returned from the activity
     */
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        recreate();
    }

    public boolean isOnline(){
        if(ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED) {
            ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                return true;
            }
        }
        return false;
    }
}

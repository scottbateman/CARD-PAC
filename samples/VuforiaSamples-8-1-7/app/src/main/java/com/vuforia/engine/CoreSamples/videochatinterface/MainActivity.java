package com.vuforia.engine.CoreSamples.videochatinterface;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.vuforia.engine.CoreSamples.R;

import java.util.ArrayList;

/*
    This activity manages most of the UI, the flow of the app, as well as calls to the server.
    This activity is what the user sees once they are logged in to the app.
    They can see their contacts, call them (only video at the moment), or remove them
    They can also send contact requests (by username) to other users they would like to call - or respond to contact requests that the user has received
    Navigating around this UI is done simply by opening the left side panel, and moving to different tabs
    Currently there is (as defined by drawer_menu.xml): Contacts - Contact Requests - Settings - Log out

    @author Nathan Sonier
 */
public class MainActivity extends AppCompatActivity implements ServerCaller {

    //Recycler views containing contacts, contact requests, and settings
    private RecyclerView contactsList, contactRequestsList, settingsList;

    //Used to add buttons and title to the toolbar at the top of the screen
    private Toolbar toolBar;

    //Also used to manage a custom toolbar at the top of the screen
    private ActionBar actionBar;

    //This drawer layout is used to order things in the left drawer (panel) to navigate around the app
    private DrawerLayout drawerLayout;

    //Constraint layouts to hold the recycler views, which are holding contacts, contact requests, and settings
    private ConstraintLayout contactsView, contactRequestsView, settingsView;

    //This text view is used to show which user is currently logged in to the app (only seen in the drawer)
    private TextView headerUser;

    //Array list containing the user's contact requests that they received (the most up-to-date version of the list)
    private ArrayList<ContactRequest> globalContactRequests;

    //Array list containing the user's settings
    private ArrayList<Setting> globalSettings;

    //The view adapter to attach the list to the recycler view containing the contacts
    private ContactListAdapter contactListAdapter;

    //The view adapter to attach the list to the recycler view containing the contact requests
    private ContactRequestListAdapter contactRequestListAdapter;

    //The view adapter to attach the list to the recycler view containing the settings
    private SettingsListAdapter settingsListAdapter;

    //The button (showing a round refresh icon) that the user can press to refresh the lists (data)
    private ImageButton refreshButton;

    //The round button in the bottom right of the screen to add new contacts
    private FloatingActionButton addContactButton;

    //The global server call instance that will be used to make all the calls to the server
    private ServerCall serverCall;

    //The user's ID
    private int userID;

    //The user's username and name
    private String username, name;

    //The currently active session request received (calls), usually is the first request received
    private SessionRequest currentSessionRequest;

    //Whether or not the user is currently logged in
    private boolean loggedIn;

    //This thread will continually check the server for any new session requests (calls)
    private Thread serverCheckThread;

    //This call dialog will be launched if the thread above detects an incoming session request
    private CallDialog callDialog;

    //This represents the app's state, which is one of the final integers defined below (defaults to STATE_IDLE)
    public int state;

    //This call checker class will run in the serverCheckThread defined above to check with the server for any calls
    private CallChecker callChecker;

    /*
        These final integers represent different states of the app:
        STATE_IDLE: the app is not busy, and is looking for incoming calls from other users
        STATE_CALLING: the app is calling another user, so it is no longer looking for incoming calls (only checking periodically for the other user's answer)
        STATE_IN_CALL: the app is currently in a call, so it does not check for any data (no server calls during this time)
        STATE_RECEIVED_CALL: the app has received a call from another user, and is waiting for the user's answer (while checking to make sure the other user has not cancelled the call early)
     */
    public final int STATE_IDLE = 0,
                      STATE_CALLING = 1,
                      STATE_IN_CALL = 2,
                      STATE_RECEIVED_CALL = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Initialize toolbar (and set defaults)
        toolBar = findViewById(R.id.toolbar);
        setSupportActionBar(toolBar);
        actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_baseline_menu_24px);
        actionBar.setTitle("Contacts");

        //Initialize refresh button (and add fucntionality)
        refreshButton = (ImageButton) toolBar.getChildAt(0);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Refreshing...", Toast.LENGTH_SHORT).show();
                refreshData();
            }
        });

        //Initialize drawer layout
        drawerLayout = findViewById(R.id.drawer_layout);

        //Initialize navigation view that will be inside drawer layout (to navigate between tabs)
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.getMenu().getItem(0).setChecked(true);
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                menuItem.setChecked(true);
                drawerLayout.closeDrawers();

                //If user presses on contacts
                if(menuItem.getTitle().equals("Contacts")){
                    //Refresh list of contacts
                    serverGetUserContacts(userID);

                    //Switch view
                    enableContainer(contactsView);

                    //Change title
                    actionBar.setTitle("Contacts");
                //If user presses contact requests
                }else if(menuItem.getTitle().equals("Contact Requests")){
                    //Refresh list of contact requests
                    serverGetUserRequests(userID);

                    //Switch view
                    enableContainer(contactRequestsView);

                    //Change title
                    actionBar.setTitle("Contact Requests");
                //If user presses settings
                }else if(menuItem.getTitle().equals("Settings")){
                    //Switch view
                    enableContainer(settingsView);

                    //Change title
                    actionBar.setTitle("Settings");
                //If user presses log out
                }else if(menuItem.getTitle().equals("Log out")){
                    //Request to log out from app
                    serverLogout(username);
                }

                return true;
            }
        });

        //Retrieve a header view (at the top of the drawer layout)
        View header = navigationView.getHeaderView(0);
        headerUser = (TextView) header.findViewById(R.id.userLoggedIn);

        //Initialize all three tabs
        contactsView = (ConstraintLayout) findViewById(R.id.contactsView);
        contactRequestsView = (ConstraintLayout) findViewById(R.id.contactRequestsView);
        settingsView = (ConstraintLayout) findViewById(R.id.settingsView);

        globalContactRequests = new ArrayList<>();

        //Initialize recycler view to display the list of contacts (and set defaults)
        contactsList = (RecyclerView) findViewById(R.id.contactList);
        contactsList.setHasFixedSize(true);
        contactsList.setLayoutManager(new LinearLayoutManager(this));
        contactListAdapter = new ContactListAdapter(new ArrayList<Contact>(0));
        contactsList.setAdapter(contactListAdapter);

        //Initialize recycler view to display the list of contact requests (and set defaults)
        contactRequestsList = (RecyclerView) findViewById(R.id.contactRequestList);
        contactRequestsList.setHasFixedSize(true);
        contactRequestsList.setLayoutManager(new LinearLayoutManager(this));
        contactRequestListAdapter = new ContactRequestListAdapter(new ArrayList<ContactRequest>(0));
        contactRequestsList.setAdapter(contactRequestListAdapter);

        //Create and set an item decorator (a line dividing each entry in the list in this case)
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(contactsList.getContext(), DividerItemDecoration.VERTICAL);
        contactsList.addItemDecoration(dividerItemDecoration);

        //Initialize "add contact" button and add functionality
        addContactButton = (FloatingActionButton) findViewById(R.id.addContactButton);
        addContactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AddContactDialog addContactDialog = new AddContactDialog();
                Bundle args = new Bundle();
                args.putInt("userId", userID);
                addContactDialog.setArguments(args);
                addContactDialog.show(getSupportFragmentManager(), "");
            }
        });

        //Initialize a recycler view to hold the list of settings
        settingsList = (RecyclerView) findViewById(R.id.settingsList);
        settingsList.setHasFixedSize(true);
        settingsList.setLayoutManager(new LinearLayoutManager(this));

        //Initialize an array list that will hold every setting (and be available across the class)
        globalSettings = new ArrayList<>();
        /*
            Insert new settings here
        */



        /*
            End of settings
        */

        //Add a settings list adapter to the recycler view (containing the settings)
        settingsListAdapter = new SettingsListAdapter(globalSettings);
        settingsList.setAdapter(settingsListAdapter);

        //Initialize the ServerCall class, which will help us send and receive data from the server
        serverCall = new ServerCall(this, userID);

        //Get the logged in user's ID from the login process (done previously)
        userID = getIntent().getExtras().getInt("id");

        //Show the contacts view by default
        enableContainer(contactsView);

        //User is logged in
        loggedIn = true;

        //Request the app to refresh the data fetched from the server (and display any updated data)
        refreshData();

        //Set a default value to the call dialog
        callDialog = null;

        //Initialize the CallChecker class, which will ask the server every few seconds to see if there is an incoming call
        callChecker = new CallChecker();

        //Initialize a thread to put the call checker instance on it
        serverCheckThread = new Thread(callChecker);

        //Start the call checker thread
        serverCheckThread.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loggedIn = true;
        state = STATE_IDLE;
        callChecker = new CallChecker();
        serverCheckThread = new Thread(callChecker);
        serverCheckThread.start();
        refreshData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Toast.makeText(this, "on pause", Toast.LENGTH_SHORT).show();
        loggedIn = false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case android.R.id.home:
                drawerLayout.openDrawer(GravityCompat.START);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This method will show one of the views from the drawer view (contacts, settings, etc.)
     *
     * @param container the view to show
     */
    public void enableContainer(ConstraintLayout container){
        disableAllContainers();
        container.setVisibility(View.VISIBLE);
    }

    /**
     * This method will hide all visible containers (used when showing only one of them)
     */
    public void disableAllContainers(){
        if(contactsView.getVisibility() != View.GONE){
            contactsView.setVisibility(View.GONE);
        }
        if(contactRequestsView.getVisibility() != View.GONE){
            contactRequestsView.setVisibility(View.GONE);
        }
        if(settingsView.getVisibility() != View.GONE){
            settingsView.setVisibility(View.GONE);
        }
    }

    /**
     * This method will refresh the data displayed on the screen
     */
    public void refreshData(){
        //Call the server to retrieve the user's user data
        serverGetUser(userID);

        //Call the server to retrieve the user's contact data
        serverGetUserContacts(userID);

        //Call the server to retrieve the user's contact request data
        serverGetUserRequests(userID);
    }

    /**
     * This method sends a request to the server to create a user (using a username and a name).
     * The response from the server is handled in the receiveServerCall() method
     *
     * @param username the desired user's username
     * @param name the desired user's name
     */
    public void serverCreateUser(String username, String name){
        serverCall = new ServerCall(this, userID);
        serverCall.execute(Integer.toString(ServerCall.SERVER_USER_CREATE),username, name);
    }

    /**
     * This method sends a request to the server to log in an existing user to the app.
     * The response from the server is handled in the receiveServerCall() method
     *
     * @param username the user's username
     * @param deviceId the id (if existing) of the user's device
     */
    public void serverLogin(String username, int deviceId){
        serverCall = new ServerCall(this, userID);
        serverCall.execute(Integer.toString(ServerCall.SERVER_USER_LOGIN), username, Integer.toString(deviceId));
    }

    /**
     * This method sends a request to the server to log out a logged in user to the app.
     * The response from the server is handled in the receiveServerCall() method
     *
     * @param username the user's username
     */
    public void serverLogout(String username){
        serverCall = new ServerCall(this, userID);
        serverCall.execute(Integer.toString(ServerCall.SERVER_USER_LOGOUT), username);
    }

    /**
     * This method sends a request to the server to retrieve the logged in user's data
     * The response from the server is handled in the receiveServerCall() method
     *
     * @param userId the logged in user's ID
     */
    public void serverGetUser(int userId){
        serverCall = new ServerCall(this, userID);
        serverCall.execute(Integer.toString(ServerCall.SERVER_USER_GET), Integer.toString(userId));
    }

    /**
     * This method sends a request to the server to retrieve the logged in user's contacts
     * The response from the server is handled in the receiveServerCall() method
     *
     * @param userId the logged in user's ID
     */
    public void serverGetUserContacts(int userId){
        serverCall = new ServerCall(this, userID);
        serverCall.execute(Integer.toString(ServerCall.SERVER_CONTACTS_GET), Integer.toString(userId));
    }

    /**
     * This method sends a request to the server to retrieve the logged in user's contact requests
     * The response from the server is handled in the receiveServerCall() method
     *
     * @param userId the logged in user's ID
     */
    public void serverGetUserRequests(int userId){
        serverCall = new ServerCall(this, userID);
        serverCall.execute(Integer.toString(ServerCall.SERVER_CONTACT_REQUESTS_GET), Integer.toString(userId));
    }

    /**
     * This method sends a request to the server to send a contact request to another user
     * The response from the server is handled in the receiveServerCall() method
     *
     * @param userId the logged in user's ID
     * @param username the contact's username (the contact we want to add)
     */
    public void serverSendContactRequest(int userId, String username){
        serverCall = new ServerCall(this, userID);
        serverCall.execute(Integer.toString(ServerCall.SERVER_CONTACT_REQUESTS_ADD), Integer.toString(userId), username);
    }

    /**
     * This method sends a request to the server to accept an existing contact request (adding the contact)
     * The response from the server is handled in the receiveServerCall() method
     *
     * @param userId the logged in user's ID
     * @param contactId the contact that sent the request
     */
    public void serverAcceptRequest(int userId, int contactId){
        serverCall = new ServerCall(this, userID);
        serverCall.execute(Integer.toString(ServerCall.SERVER_CONTACT_REQUESTS_ACCEPT), Integer.toString(userId), Integer.toString(contactId));
    }

    /**
     * This method sends a request to the server to decline an existing contact request (removing it)
     * The response from the server is handled in the receiveServerCall() method
     *
     * @param userId the logged in user's ID
     * @param contactId the contact that sent the request
     */
    public void serverDeclineRequest(int userId, int contactId){
        contactRequestListAdapter.setData(globalContactRequests);
        serverCall = new ServerCall(this, userID);
        serverCall.execute(Integer.toString(ServerCall.SERVER_CONTACT_REQUESTS_REMOVE), Integer.toString(userId), Integer.toString(contactId));
    }

    /**
     * This method sends a request to the server to delete an existing contact
     * The response from the server is handled in the receiveServerCall() method
     *
     * @param userId the logged in user's ID
     * @param contactId the contact we want to delete
     */
    public void serverDeleteContact(int userId, int contactId){
        serverCall = new ServerCall(this, userID);
        serverCall.execute(Integer.toString(ServerCall.SERVER_CONTACTS_REMOVE), Integer.toString(userId), Integer.toString(contactId));
    }

    /**
     * This method sends a request to the server to send a call request to another contact (call the contact)
     * The response from the server is handled in the receiveServerCall() method
     *
     * @param userId the logged in user's ID
     * @param contactId the contact we want to call
     * @param name the name of the contact we want to call
     */
    public void serverSendSessionRequest(int userId, int contactId, String name){
        state = STATE_CALLING;
        currentSessionRequest = new SessionRequest(contactId, name);
        serverCall = new ServerCall(this, userID);
        serverCall.execute(Integer.toString(ServerCall.SERVER_SESSION_REQUESTS_ADD), Integer.toString(userId), Integer.toString(contactId));
    }

    /**
     * This method sends a request to the server to delete an incoming call request (decline the call)
     * The response from the server is handled in the receiveServerCall() method
     *
     * @param userId the logged in user's ID
     * @param contactId the contact that sent the call request
     */
    public void serverDeleteSessionRequest(int userId, int contactId){
        serverCall = new ServerCall(this, userID);
        serverCall.execute(Integer.toString(ServerCall.SERVER_SESSION_REQUESTS_REMOVE), Integer.toString(userId), Integer.toString(contactId));
    }

    /**
     * This method sends a request to the server to accept an incoming call request
     * The response from the server is handled in the receiveServerCall() method
     *
     * @param userId the logged in user's ID
     * @param contactId the contact that sent the call request
     * @param name the contact's name
     */
    public void serverAcceptSessionRequest(int userId, int contactId, String name){
        serverCall = new ServerCall(this, userID);
        serverCall.execute(Integer.toString(ServerCall.SERVER_SESSION_REQUESTS_ACCEPT), Integer.toString(userId), Integer.toString(contactId));
    }

    /**
     * This method handles any response sent by the server, and decides what to do based on the response
     *
     * @param type the type of response received
     * @param isSuccessful whether the request associated with the response was successful or not
     * @param result result data sent with the response (if any)
     */
    public void receiveServerCall(int type, boolean isSuccessful, String result){
        Log.d("STATUS", "RECEIVED SERVER CALL");
        switch(type){
            //Response from the app creating a new user
            case ServerCall.SERVER_USER_CREATE:
                if(isSuccessful){
                    Log.d("SERVER RESPONSE", "" + result);
                }
                break;
            //Response from the user logging out
            case ServerCall.SERVER_USER_LOGOUT:
                if(isSuccessful){
                    Log.d("SERVER RESPONSE", "" + result);
                    finish();
                }
                break;
            //Reponse from the user retrieving their list of contacts
            case ServerCall.SERVER_CONTACTS_GET:
                try {
                    //Parse the list of contacts (in JSON format)
                    JSONArray contactsJSON = new JSONArray(result);

                    //Create an array list to hold the contacts
                    ArrayList<Contact> newContacts = new ArrayList<>();

                    //Iterate through the parsed JSON, and create and store contact objects
                    for(int i=0;i<contactsJSON.length();i++){
                        //Retrieve a JSON object from the JSON array
                        JSONObject contact = contactsJSON.getJSONObject(i);

                        //Create contact object using the JSON object and store it into the array list
                        newContacts.add(new Contact(contact.getInt("user_id"), contact.getString("name")));
                    }

                    //Update the list of contacts in the recycler view
                    contactListAdapter.setData(newContacts);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;
            //Response from the user removing a contact
            case ServerCall.SERVER_CONTACTS_REMOVE:
                if(isSuccessful){
                    Log.d("SERVER RESPONSE", "" + result);
                    serverGetUserContacts(userID);
                }
                break;
            //Response from the user retrieving their list of contact requests
            case ServerCall.SERVER_CONTACT_REQUESTS_GET:
                try {
                    //Parse the list of contact requests (in JSON format)
                    JSONArray requestsJSON = new JSONArray(result);

                    //Create an array list to hold the contacts
                    ArrayList<ContactRequest> newRequests = new ArrayList<>();

                    //Iterate through the parsed JSON, and create and store contact request objects
                    for(int i=0;i<requestsJSON.length();i++){
                        //Retrieve a JSON object from the JSON array
                        JSONObject request = requestsJSON.getJSONObject(i);

                        //Create a new contact request object using the JSON object and store it in the array list
                        newRequests.add(new ContactRequest(request.getInt("sender_id"), request.getString("name")));
                    }

                    //Update the list of contact requests in the recycler view
                    contactRequestListAdapter.setData(newRequests);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;
            //Response from the user sending a contact request to another user
            case ServerCall.SERVER_CONTACT_REQUESTS_ADD:
                if(isSuccessful){
                    Log.d("SERVER RESPONSE", "" + result);
                }
                break;
            //Response from the user declining a contact request sent to them
            case ServerCall.SERVER_CONTACT_REQUESTS_REMOVE:
                if(isSuccessful){
                    Log.d("SERVER RESPONSE", "" + result);
                }
                break;
            //Response from the user accepting a contact request sent to them
            case ServerCall.SERVER_CONTACT_REQUESTS_ACCEPT:
                if(isSuccessful){
                    Log.d("SERVER RESPONSE", "" + result);
                }else{
                    Log.d("SERVER RESPONSE", "" + result);
                }
                break;
            //Response from the user removing an active session (call)
            case ServerCall.SERVER_SESSIONS_REMOVE:
                if(isSuccessful){
                    Log.d("SERVER RESPONSE", "" + result);
                }
                break;
            //Response from the user retrieving their incoming call requests (only the first one)
            case ServerCall.SERVER_SESSION_REQUESTS_GET:
                try {
                    //Parse the list of incoming call requests (in JSON format)
                    JSONArray tempList = new JSONArray(result);

                    //If there is at least one incoming call, parse it
                    if(tempList.length() > 0){
                        //Parse the first item in the JSON array
                        JSONObject currentRequestJSON = tempList.getJSONObject(0);

                        //If that item does not match the current session request (meaning it is not a duplicate), replace the old session request
                        if(currentRequestJSON.getInt("user_id") != currentSessionRequest.getUserID()){
                            currentSessionRequest = new SessionRequest(currentRequestJSON.getInt("user_id"), currentRequestJSON.getString("name"));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;
            //Response from the user sending a call request to another user
            case ServerCall.SERVER_SESSION_REQUESTS_ADD:
                if(isSuccessful && state == STATE_CALLING){
                    //Create a new call dialog (to show that the user is calling)
                    callDialog = new CallDialog();

                    //Create a new bundle for arguments
                    Bundle args = new Bundle();

                    //Put various arguments in the bundle (contact name, user's ID, contact's ID, whether or not the user is the one calling)
                    args.putString("name", currentSessionRequest.getName());
                    args.putInt("userId", userID);
                    args.putInt("contactId", currentSessionRequest.getUserID());
                    args.putBoolean("sending", true);

                    //Set the arguments in the dialog
                    callDialog.setArguments(args);

                    //Set the calling class to this class
                    callDialog.setCaller(MainActivity.this);

                    //Display the dialog on the screen
                    callDialog.show(getSupportFragmentManager(), "");

                    Log.d("SERVER RESPONSE", "" + result);
                }
                break;
            //Response from the user declining a call request
            case ServerCall.SERVER_SESSION_REQUESTS_REMOVE:
                if(isSuccessful){
                    Log.d("SERVER RESPONSE", "" + result);
                }
                break;
            //Response from the user accepting a call request
            case ServerCall.SERVER_SESSION_REQUESTS_ACCEPT:
                if(isSuccessful){
                    //Start call
                    Intent intent = new Intent(MainActivity.this, CallActivity.class);
                    intent.putExtra("userId", userID);
                    intent.putExtra("contactId", currentSessionRequest.getUserID());
                    startActivityForResult(intent, STATE_IN_CALL);
                    Log.d("SERVER RESPONSE", "" + result);
                }
                break;
            //Response from the user retrieve their information (username and name)
            case ServerCall.SERVER_USER_GET:
                if(isSuccessful){
                    try{
                        //Parse JSON object from result data
                        JSONObject userDetails = new JSONObject(result);

                        //Retrieve username from JSON object
                        username = userDetails.getString("username");

                        //Retrieve name from JSON object
                        name = userDetails.getString("name");

                        //Set the header text (drawer view) to show the user logged in
                        headerUser.setText(name);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                break;
        }
    }

    /**
     * This method requests the server to delete a contact (passed in as a contact object)
     *
     * @param cIn the contact to delete
     */
    public void deleteContact(Contact cIn){
        //Call the server to delete
        serverDeleteContact(userID, cIn.getUserID());

        //Retrieve the data currently in the recycler view
        ArrayList<Contact> c = contactListAdapter.getData();

        //Iterate through the data and find the matching contact
        for(int i=0;i<c.size();i++){
            //If contact matches the contact to delete, remove it from the list
            if(c.get(i).getUserID() == cIn.getUserID()){
                c.remove(i);
                break;
            }
        }

        //Update the data
        contactListAdapter.setData(c);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //If the activity that just ended was a call
        if(requestCode == STATE_IN_CALL){
            state = STATE_IDLE;
            callChecker = new CallChecker();
            serverCheckThread = new Thread(callChecker);
            serverCheckThread.start();
        }
    }

    //This class is the adapter that will hold the data for the contact list recycler view
    public class ContactListAdapter extends RecyclerView.Adapter<ContactListAdapter.ViewHolder> {

        //Array list containing contact objects
        private ArrayList<Contact> contacts;

        public ContactListAdapter(ArrayList<Contact> contactData) {
            contacts = contactData;
        }

        /**
         * This method sets the data that the recycler view will hold
         *
         * @param contactData the new data to display
         */
        public void setData(ArrayList<Contact> contactData){
            contacts = contactData;
            this.notifyDataSetChanged();
        }

        /**
         * This method returns the data displayed by the recycler view
         *
         * @return the data displayed
         */
        public ArrayList<Contact> getData(){
            return contacts;
        }

        //This inner class represents each individual item in the recycler view
        public class ViewHolder extends RecyclerView.ViewHolder {

            //This dialog will be displayed if the user presses on the item
            public ContactDialog dialog;

            //This text view holds the name of the contact associated with this item
            public TextView mTextView;

            //This is the contact's ID
            public int id;

            public ViewHolder(TextView v) {
                super(v);
                dialog = new ContactDialog();
                mTextView = v;
                mTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Bundle args = new Bundle();
                        args.putInt("userId", userID);
                        args.putInt("contactId", id);
                        args.putString("name", mTextView.getText().toString());
                        dialog.setArguments(args);
                        dialog.setCaller(MainActivity.this);
                        if(!dialog.isAdded()){
                            dialog.show(getSupportFragmentManager(), "");
                        }

                    }
                });
            }

            public void setID(int idIn){
                id = idIn;
            }
        }

        @Override
        public ContactListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                       int viewType) {
            TextView v = (TextView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.contact_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            Contact currentContact = contacts.get(position);
            holder.mTextView.setText(currentContact.getName());
            holder.setID(currentContact.getUserID());
        }

        @Override
        public int getItemCount() {
            return contacts.size();
        }

    }

    //This class is the adapter that will hold the data for the contact list recycler view
    public class ContactRequestListAdapter extends RecyclerView.Adapter<ContactRequestListAdapter.ViewHolder> {

        //This array list contains the contact requests
        private ArrayList<ContactRequest> contactRequests;

        public ContactRequestListAdapter(ArrayList<ContactRequest> contactRequestData) {
            contactRequests = contactRequestData;
        }

        /**
         * This method sets the data that the recycler view will hold
         *
         * @param contactRequestData the new data to display
         */
        public void setData(ArrayList<ContactRequest> contactRequestData){
            contactRequests = contactRequestData;
            this.notifyDataSetChanged();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            //The layout that will contain the different buttons and items
            public ConstraintLayout constraintLayout;

            //The name of the contact that sent the request
            public TextView name;

            //The button to accept the contact request
            public ImageButton acceptButton;

            //The button to decline the contact request
            public ImageButton declineButton;

            //The contact's ID
            public int id;

            //The item's position in the array list
            public int position;

            public ViewHolder(ConstraintLayout v) {
                super(v);
                constraintLayout = v;

                //Initialize the text view containing the name
                name = (TextView) v.getViewById(R.id.name);

                //Initialize the accept button and add functionality
                acceptButton = (ImageButton) v.getViewById(R.id.acceptButton);
                acceptButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        serverAcceptRequest(userID, id);
                        contactRequests.remove(position);
                        notifyDataSetChanged();
                        Toast.makeText(MainActivity.this, name.getText() + " is now your contact.", Toast.LENGTH_SHORT).show();
                    }
                });

                //Initialize the decline button and add functionality
                declineButton = (ImageButton) v.getViewById(R.id.declineButton);
                declineButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try{
                            serverDeclineRequest(userID, id);
                            contactRequests.remove(position);
                            notifyDataSetChanged();
                            Toast.makeText(MainActivity.this, "Contact Request Declined", Toast.LENGTH_SHORT).show();
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                    }
                });
            }

            public void setID(int idIn){
                id = idIn;
            }

            public void setPosition(int posIn){
                position = posIn;
            }
        }

        @Override
        public ContactRequestListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                                int viewType) {
            ConstraintLayout v = (ConstraintLayout) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.contact_request_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            ContactRequest currentContactRequest = contactRequests.get(position);
            holder.name.setText(currentContactRequest.getName());
            holder.setID(currentContactRequest.getID());
            holder.setPosition(position);
        }

        @Override
        public int getItemCount() {
            return contactRequests.size();
        }

    }

    //This class is the adapter that will hold the data for the contact list recycler view
    public class SettingsListAdapter extends RecyclerView.Adapter<SettingsListAdapter.ViewHolder>{

        //This array list contains the list of settings
        private ArrayList<Setting> settings;

        public SettingsListAdapter(ArrayList<Setting> settingsData) {
            settings = settingsData;
        }

        @NonNull
        @Override
        public SettingsListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            TextView v = (TextView) LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.setting_item, viewGroup, false);
            return new ViewHolder(v);
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            //The title of the setting
            public TextView title;

            //The instance of the setting
            public Setting setting;

            //The position of the setting in the array list
            public int position;

            public ViewHolder(TextView v) {
                super(v);
                title = v;
            }

            public void setOnClick(View.OnClickListener onClickIn){
                setting.setOnClickListener(onClickIn);
            }

            public void setPosition(int posIn){
                position = posIn;
            }

            public void setSetting(Setting settingIn){
                setting = settingIn;
                title.setOnClickListener(setting.getOnClickListener());
            }
        }

        @Override
        public void onBindViewHolder(@NonNull SettingsListAdapter.ViewHolder viewHolder, int i) {
            Setting currentSetting = settings.get(i);
            viewHolder.title.setText(currentSetting.getTitle());
            viewHolder.setPosition(i);
            viewHolder.setSetting(currentSetting);
        }

        @Override
        public int getItemCount() {
            return settings.size();
        }
    }

    public class CallChecker implements Runnable, ServerCaller {

        //The instance of the server call, used to make calls to the server
        private ServerCall serverCheck;

        //Whether or not the device is currently calling a contact
        private boolean calling;

        public CallChecker(){
            callDialog = null;
            calling = false;
        }

        @Override
        public void run() {

            //While the user is logged in and is not calling a contact, check for incoming calls
            while(loggedIn && !calling){

                Log.d("state check", state + "");

                //Check which state the app is in...
                switch(state){
                    //STATE_IDLE: Waiting for incoming call... or can send call ourselves...
                    //STATE_CALLING: Sent call to contact... waiting for their answer...
                    //STATE_RECEIVED_CALL: Received call from contact... prompted to answer or decline (ensure call is not canceled from the other end before answer)
                    //STATE_IN_CALL: One of the two ends accepted the call, launch video call activity (CallActivity), and stop looking for call requests
                    case STATE_IDLE:
                        //Make a call to the server to retrieve any incoming calls (0 or 1)
                        serverCheck = new ServerCall(this, userID);
                        serverCheck.execute(Integer.toString(ServerCall.SERVER_SESSION_REQUESTS_GET), Integer.toString(userID));
                        Log.d("sent server", "get requests idle");
                        break;
                    case STATE_CALLING:
                        //Make a call to the server to retrieve any outgoing calls (0 or 1)
                        serverCheck = new ServerCall(this, userID);
                        serverCheck.execute(Integer.toString(ServerCall.SERVER_SESSION_REQUESTS_GET), Integer.toString(currentSessionRequest.getUserID()));
                        Log.d("sent server", "get requests calling");
                        break;
                    case STATE_RECEIVED_CALL:
                        //Make a call to the server to retrieve any incoming calls (0 or 1)
                        serverCheck = new ServerCall(this, userID);
                        serverCheck.execute(Integer.toString(ServerCall.SERVER_SESSION_REQUESTS_GET), Integer.toString(userID));
                        Log.d("sent server", "get requests received call");
                        break;
                    case STATE_IN_CALL:
                        //Set calling boolean to true
                        calling = true;
                        break;
                }
                try {
                    //Only perform this check every 4 seconds (could be shorter, only set it at four seconds to lower power consumption)
                    Thread.sleep(4000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            //Temporary runOnUiThread() call
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //Toast.makeText(MainActivity.this, "CallChecker thread stopped", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void receiveServerCall(int type, boolean isSuccessful, String result) {

            Log.d("RECEIVE CALL", type + ", " + isSuccessful + ", " + result);

            //If response is successful
            if(isSuccessful){
                //If response is to retrieve session requests
                if(type == ServerCall.SERVER_SESSION_REQUESTS_GET){
                    try{

                        //Parse JSON object from result
                        JSONObject res = new JSONObject(result);

                        //Check state of device
                        switch(state){
                            //Device is idle, response indicates call has been found
                            case STATE_IDLE:
                                //Get ID of contact calling
                                int contactId = res.getInt("user_id");

                                //Get name of contact calling
                                String contactName = res.getString("name");

                                //Set incoming call to be current session request
                                currentSessionRequest = new SessionRequest(contactId, contactName);

                                //Launch call dialog
                                callDialog = new CallDialog();
                                Bundle args = new Bundle();
                                args.putString("name", contactName);
                                args.putInt("userId", userID);
                                args.putInt("contactId", contactId);
                                args.putBoolean("sending", false);
                                callDialog.setArguments(args);
                                callDialog.setCaller(MainActivity.this);
                                callDialog.show(getSupportFragmentManager(), "");

                                //Switch state
                                state = STATE_RECEIVED_CALL;
                                break;
                            case STATE_CALLING:
                                //We sent a call... and we check if we got a response
                                int decision = res.getInt("is_accepted");

                                //Check for the decision code (-1 means no response yet)

                                if(decision == 1){
                                    //Dismiss the call dialog (no longer calling)
                                    callDialog.dismiss();

                                    //Call server to add request to active sessions
                                    serverCheck = new ServerCall(MainActivity.this, userID);
                                    serverCheck.execute(Integer.toString(ServerCall.SERVER_SESSIONS_ADD), Integer.toString(userID), Integer.toString(currentSessionRequest.getUserID()));
                                    Log.d("sent server", "add session");

                                    state = STATE_IN_CALL;

                                    //Start call
                                    Intent intent = new Intent(MainActivity.this, CallActivity.class);
                                    intent.putExtra("userId", userID);
                                    intent.putExtra("contactId", currentSessionRequest.getUserID());
                                    startActivityForResult(intent, STATE_IN_CALL);

                                    //Reset current session request object
                                    currentSessionRequest = null;

                                    break;
                                }

                                break;
                            case STATE_IN_CALL:
                                //We have accepted the call
                                break;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            }
            //If response is not successful
            else{

                if(type == ServerCall.SERVER_SESSION_REQUESTS_GET && state == STATE_RECEIVED_CALL){
                    //Call request no longer exists
                    Log.d("CALL CHECKER", "Call is no longer existing");
                    //Dismiss dialog
                    callDialog.dismiss();

                    //Reset current session request object
                    currentSessionRequest = null;

                    state = STATE_IDLE;
                }else if(type == ServerCall.SERVER_SESSION_REQUESTS_GET && state == STATE_CALLING){
                    //Call request no longer exists
                    Log.d("CALL CHECKER", "Call is no longer existing");
                    //Dismiss dialog
                    if(callDialog != null){
                        callDialog.dismiss();
                    }

                    //Reset current session request object
                    currentSessionRequest = null;

                    state = STATE_IDLE;
                }
            }
        }
    }
}

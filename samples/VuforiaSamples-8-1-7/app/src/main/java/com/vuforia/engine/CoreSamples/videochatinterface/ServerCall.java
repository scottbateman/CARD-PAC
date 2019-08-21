package com.vuforia.engine.CoreSamples.videochatinterface;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;

/**
 * This class is used to make calls to the server, which sends response data based on the request
 * A request type is required to use this class (they can be found below, as final variables)
 *
 * @author Nathan Sonier
 */
public class ServerCall extends AsyncTask<String,Void,String[]> {

    //These final integers are used as request types
    public static final int SERVER_USER_CREATE = 0,
            SERVER_USER_LOGIN = 1,
            SERVER_USER_LOGOUT = 2,
            SERVER_CONTACTS_GET = 3,
            SERVER_CONTACTS_ADD = 4,
            SERVER_CONTACTS_REMOVE = 5,
            SERVER_CONTACT_REQUESTS_GET = 6,
            SERVER_CONTACT_REQUESTS_ADD = 7,
            SERVER_CONTACT_REQUESTS_REMOVE = 8,
            SERVER_CONTACT_REQUESTS_ACCEPT = 9,
            SERVER_SESSIONS_ADD = 10,
            SERVER_SESSIONS_REMOVE = 11,
            SERVER_SESSION_REQUESTS_GET = 12,
            SERVER_SESSION_REQUESTS_ADD = 13,
            SERVER_SESSION_REQUESTS_REMOVE = 14,
            SERVER_SESSION_REQUESTS_ACCEPT = 15,
            SERVER_SESSIONS_GET = 16,
            SERVER_USER_GET = 17,
            SERVER_TIMED_OUT = 18,
            SERVER_VUMARK_DATA_GET = 19,
            SERVER_VUMARK_DATA_UPDATE = 20,
            SERVER_VUMARK_DATA_REMOVE = 21;

    //These final booleans are used to indicate whether or not a request failed
    public static final boolean SERVER_CALL_FAIL = false, SERVER_CALL_SUCCESS = true;

    //This integer holds the logged in user's ID
    private int callerId;

    /*
        This instance of ServerCaller is the class that called (and initiated) this ServerCall instance
        Note: any class that wishes to use this ServerCall class must implement ServerCaller (only one method to override).
        This is for the responses to be handled (even if the method is left blank)
    */
    private ServerCaller caller;

    public ServerCall(ServerCaller callerIn, int idIn){
        caller = callerIn;
        callerId = idIn;
    }

    /**
     * This method takes in a string, and encodes it so that it is readable to the server (usually this step is done automatically in the browser)
     * Note: this implementation is very basic, and only replaces the spaces with %20. The regex can be replaced with any expression, if needed.
     *
     * @param in the string to encode
     * @return the encoded string
     */
    public String htmlEncode(String in){
        in = in.replaceAll(" ", "%20");
        return in;
    }

    //This method supports a variable number of parameters, but by default there is at least 2 (one for the request type, one for the user ID)
    @Override
    protected String[] doInBackground(String... params) {

        //This string will hold two items: the request type, and the response from the server
        String[] results = new String[2];

        //Retrieve the request type from the parameters (always the first parameter), and store it in the results array
        int callId = Integer.parseInt(params[0]);
        results[0] = Integer.toString(callId);

        //Set a default value for the second position in the array
        results[1] = "";

        try {

            //Create a URL variable
            URL url = null;

            //Initialize the server address (and port)
            String server = GlobalVariablesClass.getServerAddress() + ":9900";
//            String server = "http://hcidev.cs.unb.ca:9900";
            //Create a string variable to hold the query to send to the server
            String query = "";

            //Check the request type, and set query depending on type
            switch (callId) {
                case SERVER_USER_CREATE:
                    query = "/users/create?username=" + params[1] + "&name=" + params[2];
                    break;
                case SERVER_USER_LOGIN:
                    query = "/users/login?username=" + params[1];
                    break;
                case SERVER_USER_LOGOUT:
                    query = "/users/logout?username=" + params[1];
                    break;
                case SERVER_CONTACTS_GET:
                    query = "/contacts/user?userId=" + params[1];
                    break;
                case SERVER_CONTACTS_ADD:
                    query = "/contacts/add?userId=" + params[1] + "&contactId=" + params[2];
                    break;
                case SERVER_CONTACTS_REMOVE:
                    query = "/contacts/remove?userId=" + params[1] + "&contactId=" + params[2];
                    break;
                case SERVER_CONTACT_REQUESTS_GET:
                    query = "/contacts/requests/user?userId=" + params[1];
                    break;
                case SERVER_CONTACT_REQUESTS_ADD:
                    query = "/contacts/requests/add?userId=" + params[1] + "&username=" + params[2];
                    break;
                case SERVER_CONTACT_REQUESTS_REMOVE:
                    query = "/contacts/requests/remove?userId=" + params[1] + "&contactId=" + params[2];
                    break;
                case SERVER_CONTACT_REQUESTS_ACCEPT:
                    query = "/contacts/requests/accept?userId=" + params[1] + "&contactId=" + params[2];
                    break;
                case SERVER_SESSIONS_ADD:
                    query = "/sessions/add?userId=" + params[1] + "&contactId=" + params[2];
                    break;
                case SERVER_SESSIONS_REMOVE:
                    query = "/sessions/remove?userId=" + params[1] + "&contactId=" + params[2];
                    break;
                case SERVER_SESSION_REQUESTS_GET:
                    query = "/sessions/requests/user?userId=" + params[1];
                    break;
                case SERVER_SESSION_REQUESTS_ADD:
                    query = "/sessions/requests/add?userId=" + params[1] + "&contactId=" + params[2];
                    break;
                case SERVER_SESSION_REQUESTS_REMOVE:
                    query = "/sessions/requests/remove?userId=" + params[1] + "&contactId=" + params[2];
                    break;
                case SERVER_SESSION_REQUESTS_ACCEPT:
                    query = "/sessions/requests/accept?userId=" + params[1] + "&contactId=" + params[2];
                    break;
                case SERVER_SESSIONS_GET:
                    query = "/sessions/user?userId=" + params[1] + "&contactId=" + params[2];
                    break;
                case SERVER_USER_GET:
                    query = "/users/user?userId=" + params[1];
                    break;
                case SERVER_VUMARK_DATA_GET:
                    query = "/vumark/data/get?userId=" + params[1] + "&contactId=" + params[2];
                    break;
                case SERVER_VUMARK_DATA_UPDATE:
                    query = "/vumark/data/update?userId=" + params[1] + "&contactId=" + params[2] +
                            "&selectedCards=\"" + params[3] + "\"&discardCardId=" + params[4] +
                            "&restart_flag=" + params[5] + "&userCards=" + params[6] +
                            "&receiverCards=" + params[7];
                    Log.d("ServerCalling", query);
                    break;
                case SERVER_VUMARK_DATA_REMOVE:
                    query = "/vumark/data/remove?userId=" + params[1] + "&contactId=" + params[2];
                    break;
            }

            //Concatenate the server address with the query (after decoding it)
            String completeURL = server + htmlEncode(query);

            //Initialize the URL object using the above string
            url = new URL(completeURL);

            //Create an HTTP connection to the url, and set a timeout of 5 seconds
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(5000);

            //Initialize a buffered reader, which will read the stream from the server, and receive data
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            //Store data in the results array
            results[1] = bufferedReader.readLine();

            //Close the buffered reader
            bufferedReader.close();

            //End the connection
            connection.disconnect();

        } catch(ConnectException e){
            results[1] = "{type:'error', message:'Could not connect'}";
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch(SocketTimeoutException e){
            results[1] = "{type:'error', message:'Server error: please try again later'}";
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally{

        }

        return results;
    }

    //This method is executed after doInBackground is complete
    @Override
    protected void onPostExecute(String[] s) {
        //The array 's' is the results array in doInBackground

        //Parse the request/response type
        int callId = Integer.parseInt(s[0]);

        //Prepare a JSON object and array for parsing
        JSONObject inputObject = null;
        JSONArray inputArray = null;

        try{
            //Check data based on request/response type
            switch(callId){
                case SERVER_USER_CREATE:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_USER_CREATE, SERVER_CALL_SUCCESS, inputObject.getString("message"));
                    }else{
                        //IF ERROR
                    }
                    break;
                case SERVER_USER_LOGIN:
                    inputObject = new JSONObject(s[1]);
                    Log.d("ServerCall", inputObject.toString());
                    if(inputObject.getString("type").equals("success")){
                        JSONObject data = inputObject.getJSONObject("data");
                        caller.receiveServerCall(SERVER_USER_LOGIN, SERVER_CALL_SUCCESS, data.getString("userId"));
                    }else{
                        caller.receiveServerCall(SERVER_USER_LOGIN, SERVER_CALL_FAIL, inputObject.getString("message"));
                    }
                    break;
                case SERVER_USER_LOGOUT:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_USER_LOGOUT, SERVER_CALL_SUCCESS, inputObject.getString("message"));
                    }else{
                        //IF ERROR
                    }
                    break;
                case SERVER_CONTACTS_GET:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        inputArray = inputObject.getJSONArray("data");
                        caller.receiveServerCall(SERVER_CONTACTS_GET, SERVER_CALL_SUCCESS, inputArray.toString());
                    }else{
                        //IF ERROR
                    }
                    break;
                case SERVER_CONTACTS_ADD:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_CONTACTS_ADD, SERVER_CALL_SUCCESS, inputObject.getString("message"));
                    }else{
                        caller.receiveServerCall(SERVER_CONTACTS_ADD, SERVER_CALL_FAIL, inputObject.getString("message"));
                    }
                    break;
                case SERVER_CONTACTS_REMOVE:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_CONTACTS_REMOVE, SERVER_CALL_SUCCESS, inputObject.getString("message"));
                    }else{
                        //IF ERROR
                    }
                    break;
                case SERVER_CONTACT_REQUESTS_GET:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        inputArray = inputObject.getJSONArray("data");
                        caller.receiveServerCall(SERVER_CONTACT_REQUESTS_GET, SERVER_CALL_SUCCESS, inputArray.toString());
                    }else{
                        //IF ERROR
                    }
                    break;
                case SERVER_CONTACT_REQUESTS_ADD:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_CONTACT_REQUESTS_ADD, SERVER_CALL_SUCCESS, inputObject.getString("message"));
                    }else{
                        //IF ERROR
                    }
                    break;
                case SERVER_CONTACT_REQUESTS_REMOVE:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_CONTACT_REQUESTS_REMOVE, SERVER_CALL_SUCCESS, inputObject.getString("message"));
                    }else{

                    }
                    break;
                case SERVER_CONTACT_REQUESTS_ACCEPT:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_CONTACT_REQUESTS_ACCEPT, SERVER_CALL_SUCCESS, inputObject.getString("message"));
                    }else{
                        caller.receiveServerCall(SERVER_CONTACT_REQUESTS_ACCEPT, SERVER_CALL_FAIL, inputObject.getString("message"));
                    }
                    break;
                case SERVER_SESSIONS_ADD:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_SESSIONS_ADD, SERVER_CALL_SUCCESS, inputObject.getString("message"));
                    }else{
                        //IF ERROR
                    }
                    break;
                case SERVER_SESSIONS_REMOVE:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_SESSIONS_REMOVE, SERVER_CALL_SUCCESS, inputObject.getString("message"));
                    }else{
                        //IF ERROR
                    }
                    break;
                case SERVER_SESSION_REQUESTS_GET:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        Log.d("SERVER CALL", inputObject.getJSONObject("data").toString());
                        inputObject = inputObject.getJSONObject("data");
                        caller.receiveServerCall(SERVER_SESSION_REQUESTS_GET, SERVER_CALL_SUCCESS, inputObject.toString());
                    }else{
                        Log.d("SERVER CALL", inputObject.getString("message"));
                        caller.receiveServerCall(SERVER_SESSION_REQUESTS_GET, SERVER_CALL_FAIL, "{}");
                    }
                    break;
                case SERVER_SESSION_REQUESTS_ADD:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_SESSION_REQUESTS_ADD, SERVER_CALL_SUCCESS, inputObject.getString("message"));
                    }else{
                        caller.receiveServerCall(SERVER_SESSION_REQUESTS_ADD, SERVER_CALL_FAIL, inputObject.getString("message"));
                    }
                    break;
                case SERVER_SESSION_REQUESTS_REMOVE:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_CONTACT_REQUESTS_REMOVE, SERVER_CALL_SUCCESS, inputObject.getString("message"));
                    }else{
                        Log.d("SERVER CALL", "FAILED CALL");
                    }
                    break;
                case SERVER_SESSION_REQUESTS_ACCEPT:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_SESSION_REQUESTS_ACCEPT, SERVER_CALL_SUCCESS, inputObject.getString("message"));
                    }else{
                        caller.receiveServerCall(SERVER_SESSION_REQUESTS_ACCEPT, SERVER_CALL_FAIL, inputObject.getString("message"));
                    }
                    break;
                case SERVER_SESSIONS_GET:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_SESSIONS_GET, SERVER_CALL_SUCCESS, inputObject.getJSONObject("data").toString());
                    }else{
                        caller.receiveServerCall(SERVER_SESSIONS_GET, SERVER_CALL_FAIL, inputObject.getString("message"));
                    }
                    break;
                case SERVER_USER_GET:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_USER_GET, SERVER_CALL_SUCCESS, inputObject.getJSONObject("data").toString());
                    }else{
                        caller.receiveServerCall(SERVER_USER_GET, SERVER_CALL_FAIL, inputObject.getString("message"));
                    }
                    break;
                case SERVER_VUMARK_DATA_GET:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_VUMARK_DATA_GET,
                                                 SERVER_CALL_SUCCESS,
                                                 inputObject.getJSONObject("data").toString());
                    }else{
                        caller.receiveServerCall(SERVER_VUMARK_DATA_GET,
                                                 SERVER_CALL_FAIL,
                                                 inputObject.getString("message"));
                    }
                    break;
                case SERVER_VUMARK_DATA_UPDATE:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_VUMARK_DATA_UPDATE,
                                                 SERVER_CALL_SUCCESS,
                                                 inputObject.getString("message"));
                    }else{
                        caller.receiveServerCall(SERVER_VUMARK_DATA_UPDATE,
                                                 SERVER_CALL_FAIL,
                                                 inputObject.getString("message"));
                    }
                    break;
                case SERVER_VUMARK_DATA_REMOVE:
                    inputObject = new JSONObject(s[1]);
                    if(inputObject.getString("type").equals("success")){
                        caller.receiveServerCall(SERVER_VUMARK_DATA_REMOVE,
                                                 SERVER_CALL_SUCCESS,
                                                 inputObject.getString("message"));
                    }else{
                        caller.receiveServerCall(SERVER_VUMARK_DATA_REMOVE,
                                                 SERVER_CALL_FAIL,
                                                 inputObject.getString("message"));
                    }
            }
        }catch(JSONException e){

        }
    }
}
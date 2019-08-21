package com.vuforia.engine.CoreSamples.videochatinterface;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Calendar;

public class UDPConnection {

    //Reference to activity that instantiated and called this Class
    private CallActivity activity;

    //IP Address specified to connect to
    private String address;

    //Whether or not the client is hosting the connection
    private boolean isHosting;

    //Whether the current connection is open or closed
    private boolean isConnected;

    //Default port to transfer data over
    private final int DEFAULT_PORT = 9901;

    //Camera object that will continuously request frames from the camera
    public CameraSnapShot cameraSnapShot;

    //Bitmap to display on UI thread (ready to be displayed)
    private Bitmap frameToDisplay;

    private int frameToDisplayOrientation;

    //Data that was received from the camera, and that is ready to be sent over UDP
    private byte[] dataToSend;

    //Audio to send over UDP
    private byte[] audioToSend;

    //Length of byte array of audio to send (above)
    private int audioToSendLength;

    //Data received as a UDP packet, that contains frame data
    public byte[] dataReceived;

    //Audio received from a UDP packet
    public byte[] audioReceived;

    //Thread that the camera will run on, to minimize time between frames
    private Thread cameraThread;

    //Class that handles compressing the data and packing it into packets, then sending it through UDP
    private DataSender dataSender;

    //Class that handles receiving data from packets, unpacking and using them.
    private DataReceiver dataReceiver;

    //Thread that the DataSender instance will run on
    private Thread senderThread;

    //Thread that the DataReceiver instance will run on
    private Thread receiverThread;

    //UDP Socket that will "connect" to other host
    private DatagramSocket udpSocket;

    //UDP packets for outgoing data, as well as incoming data
    private DatagramPacket udpOut, udpIn;

    //The AudioRecorder instance that will record and play audio
    private AudioRecorder audioRecorder;


    //The scan thread, which is where the QRCodeThread above will be executed
    private Thread scanThread;

    //The thread where the AudioRecorder instance will be executed
    private Thread audioRecorderThread;

    //The logged in user's ID
    private int userId;

    //A Calendar object that will hold the time data of when the last packet was received
    private Calendar lastPacketReceived;

    //Whether or not the call is still going
    public boolean callEnded;

    //Whether or not the main view (the big view) is showing the streamed video or the local view (from camera)
    public boolean isViewingStream;

    //Constant size of packet headers (containing info about packet data)
    public final int HEADER_SIZE = ( 4 /* NUMBER OF INTEGERS */) * 4;

    //Header that describes the data as no data
    public final int HEADER_TYPE_NONE = -1;

    //Header that describes the data as a frame
    public final int HEADER_TYPE_FRAME = 1;

    //Header that describes the data as audio
    public final int HEADER_TYPE_AUDIO = 2;

    //Header that signifies the other user has ended the call
    public final int HEADER_TYPE_END = 3;

    TCPConnection tcpConnection;

    public UDPConnection(CallActivity activityIn, String addressIn, boolean isHostingIn, int userIdIn){
        activity = activityIn;
        address = addressIn;
        isHosting = isHostingIn;

        udpSocket = null;
        udpIn = null;
        udpOut = null;
        frameToDisplay = null;

        frameToDisplayOrientation = 0;
        audioToSendLength = 0;

        callEnded = false;
        isViewingStream = true;

        userId = userIdIn;

        lastPacketReceived = null;

        //Start connection (this is actually a UDP connection, the class TCPConnection has been modified)
        tcpConnection = new TCPConnection();
        Thread tcp = new Thread(tcpConnection);
        tcp.start();
    }

    //This method sets the connection status of this side of the UDP connection
    private void setConnected(boolean status){
        isConnected = status;
    }

    public void startCamera(){
        tcpConnection.startTcpCamera();
    }

    public void stopCamera(){
        tcpConnection.stopTcpCamera();
    }

    //This method is called when the camera has received a new frame, and is ready to send it
    public void receiveFromCamera(byte[] bytesIn){
        //Store the new byte data in a global variable
        dataToSend = bytesIn;

        //Decode the byte data and store the bitmap temporarily
        final Bitmap b = BitmapFactory.decodeByteArray(bytesIn, 0, bytesIn.length);

        //If main view (larger view) is the stream, send the byte data to the local view (smaller view)
        if(isViewingStream){
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.localView.renderOnView(b, cameraSnapShot.getTargetRotation());
                }
            });
        //If local view (smaller view) is the stream, send the byte data to the main view (larger view)
        }else{
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.streamView.renderOnView(b, cameraSnapShot.getTargetRotation());
                }
            });
        }
    }

    //This method receives an array of bytes containing audio data (recorded from the microphone), as well as the length (as it may not use up the entire array)
    public void receiveFromMicrophone(byte[] bytesIn, int lengthIn){
        audioToSend = bytesIn;
        audioToSendLength = lengthIn;
    }

    //This method switches the two views (main view and local view), so the user can see what the other sees in fullscreen
    public void switchView(){
        //If user has main view as the larger view, move it to smaller view and set orientation of that view
        if(isViewingStream){
            isViewingStream = false;
            activity.localView.setOrientation(cameraSnapShot.getTargetRotation());
        //If user has main view as the smaller view, move it to larger view and set orientation of that view
        }else{
            isViewingStream = true;
            activity.streamView.setOrientation(cameraSnapShot.getTargetRotation());
        }
    }

    //This method is called to display the current frame (received from a UDP packet) to the view that is supposed to display the stream
    public void displayCurrentData(final int orientationToDisplay){
        try {
            //Make a copy of the byte data received from the UDP packets (to truncate any excess array spaces)
            byte[] data = Arrays.copyOf(dataReceived, dataReceived.length);

            //Decode a bitmap from the data above
            frameToDisplay = BitmapFactory.decodeByteArray(data, 0, data.length);

            //If bitmap decoding was successful, display it
            if(frameToDisplay != null){
                //Create a matrix (to rotate the data)
                //Matrix matrix = new Matrix();

                //Rotate the matrix by 90 degrees
                //int rotation = cameraSnapShot.getTargetRotation();
                //if(rotation != -1){
                //    matrix.postRotate(rotation);
                //}

                //Create a bitmap from the original bitmap and the rotation matrix, to rotate the original bitmap
                //frameToDisplay = Bitmap.createBitmap(frameToDisplay, 0, 0, frameToDisplay.getWidth(), frameToDisplay.getHeight(), matrix, true);
                //frameToDisplay = Bitmap.createScaledBitmap(frameToDisplay, activity.streamView.getWidth(), activity.streamView.getHeight(), false);

                //Information about the bitmap decoded (for debug purposes)
//                Log.d("SIZE", "Image size is " + frameToDisplay.getWidth() + " by " + frameToDisplay.getHeight());

                //If user is viewing the stream in the main view, display this frame on it and request the QR code scanner to scan for codes
                if(isViewingStream){
                    //Create a new array that will hold the bitmap data, only this time in an integer array (instead of byte array)
                    int[] bitmapData = new int[frameToDisplay.getWidth() * frameToDisplay.getHeight()];

                    //Fill the integer array using Bitmap.getPixels(), which fills a buffer (int) with pixels from the bitmap
                    frameToDisplay.getPixels(bitmapData,0, frameToDisplay.getWidth(),0,0, frameToDisplay.getWidth(), frameToDisplay.getHeight());

                    //Send the data to the QR code scanner
                    //.currentFrame = bitmapData;

                    //Send the orientation data to the QR code scanner
                    //qrCodeThread.orientation = orientationToDisplay;

                    //Send the bitmap dimensions to the QR code scanner
                    //qrCodeThread.currentFrameSize = new Size(frameToDisplay.getWidth(), frameToDisplay.getHeight());

                    //Display the frame on the view (using the orientation)
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                        activity.streamView.renderOnView(frameToDisplay, orientationToDisplay);

                        //Reset data
                        if(activity.connection != null){
                            activity.connection.dataReceived = null;
                        }
                        }
                    });
                }else{
                    //Display the frame on the view (using the orientation)
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                        activity.localView.renderOnView(frameToDisplay, orientationToDisplay);

                        //Reset data
                        if(activity.connection != null){
                            activity.connection.dataReceived = null;
                        }
                        }
                    });
                }


                data = null;
            }
        }catch(Exception e){
            e.printStackTrace();
            dataReceived = null;
        }finally {
            dataReceived = null;
        }
    }

    public void listenCurrentData(){
        audioRecorder.audioReceived = audioReceived;
    }

    //This method is used to clear all variables, stop all threads, and stop the camera instance
    public void shutDown(){
        //Stop connection
        isConnected = false;

        //Camera instance (CameraSnapShot)
        if(cameraSnapShot != null){
            cameraSnapShot.stop();
        }

        //Camera thread that the camera instance ran on (Thread)
        if(cameraThread != null && !cameraThread.isInterrupted()){
            cameraThread.interrupt();
            cameraThread = null;
        }

        //Current frame being displayed (Bitmap)
        if(frameToDisplay != null && !frameToDisplay.isRecycled()){
            frameToDisplay.recycle();
            frameToDisplay = null;
        }

        //Data ready to be sent over UDP packets (byte[])
        if(dataToSend != null){
            dataToSend = null;
        }

        //Data that was received from UDP packets (byte[])
        if(dataReceived != null){
            dataReceived = null;
        }

        //Data sender instance handling the sending of data through UDP packets (DataSender)
        if(dataSender != null){
            dataSender = null;
        }

        //Data sender instance handling the receiving of data through UDP packets (DataReceiver)
        if(dataReceiver != null){
            dataReceiver = null;
        }

        //Sender thread that the data sender instance ran on
        if(senderThread != null && !senderThread.isInterrupted()){
            senderThread.interrupt();
            senderThread = null;
        }

        //Receiver thread that the data receiver instance ran on
        if(receiverThread != null && receiverThread.isInterrupted()){
            receiverThread.interrupt();
            receiverThread = null;
        }

        //UDP packet used to hold incoming packets
        if(udpIn != null){
            udpIn = null;
        }

        //UDP packet used to hold outgoing packets
        if(udpOut != null){
            udpOut = null;
        }

        //UDP socket used to send/receive packets
        if(udpSocket != null){
            udpSocket.close();
            udpSocket = null;
        }

        //Audio Recorder
        if(audioRecorder != null){
            audioRecorder.stop();
        }

        //Audio Recorder Thread
        if(audioRecorderThread != null && !audioRecorderThread.isInterrupted()){
            audioRecorderThread.interrupt();
            audioRecorderThread = null;
        }

        //QR Code Scanner Thread
        if(scanThread != null){
            scanThread.interrupt();
            scanThread = null;
        }
    }

    //This method will request the camera class (CameraSnapShot) to switch between the front facing camera and the back facing camera
    public void requestSwitchCamera(){
        cameraSnapShot.switchCamera();
    }

    //This method will request the AudioRecorder instance to mute the speakers, meaning the audio coming from the server won't be played (same as playing at 0 volume)
    public boolean requestMuteSpeakers(){
        if(audioRecorder != null){
            audioRecorder.muteStream();
            Toast.makeText(activity, "Speakers muted", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    //This method will request the AudioRecorder instance to mute the microphone, meaning the device won't record the audio from the microphone anymore
    public boolean requestMuteMic(){
        if(audioRecorder != null){
            audioRecorder.muteMic();
            Toast.makeText(activity, "Microphone muted", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    //This method will request the AudioRecorder instance to unmute the speakers, meaning the audio coming from the server will be played again
    public boolean requestUnmuteSpeakers(){
        if(audioRecorder != null){
            audioRecorder.unmuteStream();
            Toast.makeText(activity, "Speakers unmuted", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    //This method will request the AudioRecorder instance to unmute the microphone, meaning the device will start recording the audio from the microphone again
    public boolean requestUnmuteMic(){
        if(audioRecorder != null){
            audioRecorder.unmuteMic();
            Toast.makeText(activity, "Microphone unmuted", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    //Despite the name, this class will establish a UDP "connection".
    //It used to establish a TCP connection (as seen in the commented code), but everything has been moved to UDP.
    private class TCPConnection implements Runnable{
        @Override
        public void run() {
            /*
        }
            if(isHosting){
                try {
                    ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT);
                    serverSocket.setReuseAddress(true);
                    Socket connecting = serverSocket.accept();
                    //Transfer whatever information is necessary here
                    //...

                    setConnected(true);

                    //Close the TCP connection
                    connecting.close();
                    serverSocket.close();

                } catch (IOException e) {
                    //Could not connect to other client
                    e.printStackTrace();
                }
            }else{
                try {
                    Socket clientSocket = new Socket(address, DEFAULT_PORT);
                    //Transfer whatever information is necessary here
                    //...

                    setConnected(true);

                    //Close the TCP connection
                    clientSocket.close();

                } catch (IOException e) {
                    //Could not connect to other client
                    e.printStackTrace();
                }
            }
*/
            try {

                //Set the connection status to "connected"
                setConnected(true);

                //Initialize UDP socket
                udpSocket = new DatagramSocket();

                //Set the broadcast option on the UDP socket to false (off)
                udpSocket.setBroadcast(false);

                //Information about the socket (max send buffer size and max receive buffer size, for debugging)
//                Log.d("TSET", "SEND: " + udpSocket.getSendBufferSize() + ", RECEIVE: " + udpSocket.getReceiveBufferSize());

                //Set UDP socket timeout to 1 second (1000 milliseconds)
                udpSocket.setSoTimeout(1000);

                //Initialize the CameraSnapShot instance
                cameraSnapShot = new CameraSnapShot(activity, UDPConnection.this);

                //Create a new thread to place the CameraSnapShot instance on it
                cameraThread = new Thread(cameraSnapShot);

                //Start the camera thread
                cameraThread.start();

                //Assume both devices are hosting now (there is no difference in the process)
                isHosting = true;

                //Reset the data to send
                dataToSend = null;

                //Reset the data received
                dataReceived = null;;

                //Initialize the Data Sender instance
                dataSender = new DataSender();

                //Initialize the Data Receiver instance
                dataReceiver = new DataReceiver();

                //Initialize the thread that will hold the Data Sender instance
                senderThread = new Thread(dataSender);

                //Initialize the thread that will hold the Data Receiver instance
                receiverThread = new Thread(dataReceiver);

                //Start the sender thread
                senderThread.start();

                //Start the receiver thread
                receiverThread.start();

                //Log that startup is done
//                Log.d("TESTT", "Launched receiver");

                //Create AudioRecorder instance
                audioRecorder = new AudioRecorder(activity, UDPConnection.this);

                //Create thread to hold AudioRecorder instance
                audioRecorderThread = new Thread(audioRecorder);

                //Start the audio recorder thread
                audioRecorderThread.start();

                //Set default orientations for both of the camera views
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        activity.localView.setOrientation(cameraSnapShot.getTargetRotation());
                        activity.streamView.setOrientation(cameraSnapShot.getTargetRotation());
                    }
                });

            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        public void stopTcpCamera(){
            //Camera instance (CameraSnapShot)
            if(cameraSnapShot != null){
                cameraSnapShot.stop();
            }

            //Camera thread that the camera instance ran on (Thread)
            if(cameraThread != null && !cameraThread.isInterrupted()){
                cameraThread.interrupt();
                cameraThread = null;
            }
        }

        public void startTcpCamera(){
            //Initialize the CameraSnapShot instance
            cameraSnapShot = new CameraSnapShot(activity, UDPConnection.this);

            //Create a new thread to place the CameraSnapShot instance on it
            cameraThread = new Thread(cameraSnapShot);

            //Start the camera thread
            cameraThread.start();
        }
    }

    //This class is in charge of collecting all the data that is ready to send to the server, and send it in a series of packets
    //It implements the Runnable class, and should be run on a thread
    private class DataSender implements Runnable{

        //Sample packet object that is private to this class
        private DatagramPacket packet;

        public DataSender(){
            packet = null;
        }

        @Override
        public void run() {

            /*
                START of initial connection
                This section is used to send 10 (varies) packets, containing nothing by 4 bytes of data (that holds the user ID)
                This is done to let the server know that any information coming from this IP address and port must be associated with this user ID
                Note: 10 is a random number chosen. After the server receives one of the 10 packets, it ignores the rest. This is done to ensure (or maximize the chances of) the packet arriving (no packet loss)
            */

            //Create byte array that will hold the user ID
            byte[] bArr = new byte[4];

            //Assign a byte buffer to that array
            ByteBuffer buf = ByteBuffer.wrap(bArr);

            //Use the byte buffer to insert the user ID into the byte array
            buf.putInt(userId);

            //Send 10 copies of this array in a packet (for port identification on server)
            for(int i=0;i<10;i++){
                try {
                    DatagramPacket temp = new DatagramPacket(bArr, bArr.length, InetAddress.getByName(address), DEFAULT_PORT);
                    udpSocket.send(temp);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            /*
                END of initial connection
                By this point the server should know of this client, and should have their connection details stored in a session.
                If for some reason it failed to do so, or all the packets sent were lost, the connection won't appear on the device, but the call should end automatically (timeout)
             */

            //While the socket is connected, look for data to send
            while(isConnected){

                //If the "Call Ended" flag is true, finish the connection
                if(callEnded){
                    /*
                        START of closing connection
                        This block runs when the user presses on the "End Call" button (red).
                        The purpose of this block is to send 5 packets, containing empty data, with the type HEADER_TYPE_END.
                        When the other device will receive one of the 5 packets, it will terminate the call on their end as well.
                        Again, we send 5 packets to minimize the chances of packet loss breaking this functionality.
                     */

                    //Create a new byte array to hold the header
                    byte[] packetData = new byte[HEADER_SIZE];

                    //Add a byte buffer to insert data into the array
                    ByteBuffer b = ByteBuffer.wrap(packetData);

                    //Fill the data array with the end header type, and then put padding data after (1,0,0)
                    b.putInt(HEADER_TYPE_END);
                    b.putInt(1);
                    b.putInt(0);
                    b.putInt(0);

                    try {
                        //Create the packet using the data
                        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, InetAddress.getByName(address), DEFAULT_PORT);
                        //Resend the packet five times
                        for(int i=0;i<5;i++){
                            udpSocket.send(packet);
                        }
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    return;

                    /*
                        END of closing connection
                        This is to ensure the call on both device end at the same time (no delay)
                     */
                }

                //Check if there is any data to send (camera frames)
                if(dataToSend != null) {
                    try {
                        //Keep track of the orientation of the sensor
                        int dataOrientation = cameraSnapShot.getTargetRotation();

                        //Create a byte array output stream
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                        //Initialize bitmap options (to customize the decoding of our bitmap)
                        BitmapFactory.Options options = new BitmapFactory.Options();

                        //Compression steps
                        options.inSampleSize = 1;

                        //Decode the bitmap, compress it, and then store it into the byte array output stream
                        (BitmapFactory.decodeByteArray(dataToSend, 0, dataToSend.length, options)).compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);

                        //Retrieve data to send (compressed) from stream
                        dataToSend = byteArrayOutputStream.toByteArray();

                        /*
                            START of sending frame
                            Here frame data (bytes) is divided into parts, each of length 1400 or less.
                            This is done to prevent issues with the MTU (Maximum Transmission Unit) which might limit the packet size to 1500 bytes.
                         */

                        //Set the default packet size
                        int packetSize = 1400;

                        //Set the number of bytes left to put in packets
                        int remainingBytes = dataToSend.length;

                        //Calculate the number of packets required to fit all of the bytes
                        int numPackets = (int) (remainingBytes / packetSize) + 1;

                        //Calculate total size of all packets to be sent
                        int totalSize = numPackets * packetSize;

                        //Create a 2D byte array that will hold one packet per row, with each row containing the packet's content (plus the header data)
                        byte[][] splitPackets = new byte[numPackets][packetSize + HEADER_SIZE];

                        //Wrap a byte buffer to the frame data array
                        ByteBuffer b = ByteBuffer.wrap(dataToSend);

                        //Debug log
//                        Log.d("TSE", "Split packets into " + splitPackets.length + " pieces");

                        try{
                            //For each packet, write header data and any frame data that will fit
                            for (int i = 0; i < splitPackets.length; i++) {

                                //Byte buffer to write to the packet array
                                ByteBuffer temp = ByteBuffer.wrap(splitPackets[i]);

                                //Write header type to buffer (frame data in this case)
                                temp.putInt(HEADER_TYPE_FRAME);

                                //Set end flag in header (1 = end of frame, 0 = frame is incomplete)
                                if (i == splitPackets.length - 1) {
                                    temp.putInt(1);
                                } else {
                                    temp.putInt(0);
                                }

                                //Write the total size of the frame (so that the other device can know if the frame has no missing parts)
                                temp.putInt(totalSize);

                                //Write orientation of frame (so that the other device can rotate the frame accordingly)
                                temp.putInt(dataOrientation);

                                //Log.d("TSE", "buffer has " + b.remaining() + " bytes remaining");

                                //If there is at least 1400 bytes of data left, write 1400 bytes to the packet array
                                if (remainingBytes >= 1400) {
                                    b.get(splitPackets[i], HEADER_SIZE, 1400);
                                    remainingBytes -= 1400;
                                //If there is less than 1400 bytes of data left, write however many there is left, and set the remaining number of bytes to 0
                                } else {
                                    b.get(splitPackets[i], HEADER_SIZE, remainingBytes);
                                    remainingBytes = 0;
                                }
                            }
                        }catch(BufferUnderflowException e){
                            e.printStackTrace();
                        }

                        //byte[] currentDataToSend = dataToSend;

                        //byte[] dataWithHeader = new byte[currentDataToSend.length + HEADER_SIZE];
                        //ByteBuffer byteBuffer = ByteBuffer.wrap(dataWithHeader);

                        //int header = HEADER_TYPE_FRAME;

                        //byteBuffer.putInt(header);
                        //byteBuffer.put(currentDataToSend);

                        //For each packet data in the array, create a DatagramPacket instance and send it through the UDP socket
                        for (int i = 0; i < splitPackets.length; i++) {
                            //Create new packet to send
                            packet = new DatagramPacket(splitPackets[i], splitPackets[i].length, InetAddress.getByName(address), DEFAULT_PORT);
                            //Send packet
                            udpSocket.send(packet);
                        }

                        //Reset data to send
                        dataToSend = null;

                        /*
                            END of sending frame
                         */

                    } catch(ArrayIndexOutOfBoundsException e){
                      e.printStackTrace();
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                        //Reset data to send
                        dataToSend = null;

                    }
                }

                //Check if there is any audio data to send
                if(audioToSend != null){

//                    Log.d("UDP", "Sending audio data");
                    /*
                        START of sending audio data
                        Again, here data (bytes) is divided into parts, each of length 1400 or less.
                        This is done to prevent issues with the MTU (Maximum Transmission Unit) which might limit the packet size to 1500 bytes.
                     */

                    //Set header type (in this case it is audio)
                    int header = HEADER_TYPE_AUDIO;

                    //Set the default packet size
                    int packetSize = 1400;

                    //Set the number of bytes left to put in packets
                    int remainingBytes = audioToSendLength;

                    //Calculate the number of packets required to fit all of the bytes
                    int numPackets = (int)(remainingBytes / packetSize) + 1;

                    //Calculate total size of all packets to be sent
                    int totalSize = numPackets * packetSize;

                    //Create a 2D byte array that will hold one packet per row, with each row containing the packet's content (plus the header data)
                    byte[][] splitPackets = new byte[numPackets][packetSize + HEADER_SIZE];

                    //Wrap a byte buffer to the audio data array
                    ByteBuffer b = ByteBuffer.wrap(audioToSend);

//                    Log.d("TSE", "Split audio packets into " + splitPackets.length + " pieces");

                    //For each packet, write header data and any audio data that will fit
                    for(int i=0;i<splitPackets.length;i++){

                        //Byte buffer to write to the packet array
                        ByteBuffer temp = ByteBuffer.wrap(splitPackets[i]);

                        //Write header type to buffer (audio data in this case)
                        temp.putInt(header);

                        //Write end flag in header (1 = end of audio, 0 = more packets to come)
                        if(i == splitPackets.length - 1){
                            temp.putInt(1);
                        }else{
                            temp.putInt(0);
                        }

                        //Write the total size of the audio data (so that the other device can know if the data has no missing parts)
                        temp.putInt(totalSize);

                        //Write the orientation (0 for audio)
                        temp.putInt(0);

                        //If there is at least 1400 bytes of data left, write 1400 bytes to the packet array
                        if(remainingBytes >= 1400){
                            b.get(splitPackets[i], HEADER_SIZE, 1400);
                            remainingBytes -= 1400;
                        //If there is less than 1400 bytes of data left, write however many there is left, and set the remaining number of bytes to 0
                        }else{
                            b.get(splitPackets[i], HEADER_SIZE, remainingBytes);
                            remainingBytes = 0;
                        }
                    }

                    try {
                        //For each packet in the array, create a DatagramPacket instance and send it through the UDP socket
                        for(int i=0;i<splitPackets.length;i++){
                            //Create packet
                            packet = new DatagramPacket(splitPackets[i], splitPackets[i].length, InetAddress.getByName(address), DEFAULT_PORT);

                            //Send packet
                            udpSocket.send(packet);
                        }

                        //Reset data to send
                        audioToSend = null;

                        /*
                            END of sending audio data
                         */
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        //Reset data to send
                        audioToSend = null;
                        e.printStackTrace();
                    }


                }
                //Sleep the thread for 30 times per second, again to try to maintain a lower power consumption
                try {
                    Thread.sleep(1000/30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            }
        }

    //This class is in charge of collecting all the data that has been received by the server, and take appropriate action based on the data type (display frame, play audio, etc.)
    //It implements the Runnable class, and should be run on a thread
    private class DataReceiver implements Runnable{

        //Sample packet that is private to this class
        private DatagramPacket packet;

        //Calendar instance holding the current time at which a packet was received
        private Calendar currentPacketReceivedTime;

        public DataReceiver(){
            packet = null;
        }

        @Override
        public void run() {
            //While the socket is connected, look for data to receive
            while(isConnected){
                try {

                    //Check to make sure there is no data received, before receiving more
                    if(dataReceived == null){
                        //Create sample packet (to be filled with actual data)
                        packet = new DatagramPacket(new byte[2000], 2000);

                        //Create an array to hold any incoming data (up to a maximum of 50000 bytes per series of packets, or 49500 bytes if each packet follows a MTU of 1500)
                        byte[] completeData = new byte[50000];

                        //Number of bytes received
                        int bytesWritten = 0;

                        //The end flag of the header received
                        int end = 0;

                        //The header type of the header received
                        int headerIn = 0;

                        //The total size of the series of packets received (included in the header)
                        int totalBytes = 0;

                        //The orientation of the frame (or 0 if anything else, included in the header)
                        int orientation = 0;

                        //Number of packets received
                        int count = 0;

                        //While the packet with an end flag of "true" has not been received, keep receiving packets (and adding them to the total)
                        while(end != 1){

                            //Receive packet data
                            udpSocket.receive(packet);

                            //Debug log
                            //Log.d("packets", "packets received: " + ++count);

                            //Create a byte buffer to read the data in the packet
                            ByteBuffer packetReader = ByteBuffer.wrap(packet.getData());

                            //Store the header type
                            headerIn = packetReader.getInt();

                            //Store the end flag
                            end = packetReader.getInt();

                            //Store the total size of the series of packets
                            totalBytes = packetReader.getInt();

                            //Store the orientation
                            orientation = packetReader.getInt();

                            //If there is still space to read bytes from the packet
                            if(bytesWritten + 1400 < completeData.length){
                                packetReader.get(completeData, bytesWritten, 1400);
                                bytesWritten += 1400;
                            //Otherwise, set the header type to NONE, and break out of the loop
                            }else{
                                headerIn = HEADER_TYPE_NONE;
                                break;
                            }

                            //Debug log
                            //Log.d("TSE","Received packet - " + packet.getLength());
                        }

                        //Check if packet group is incomplete (packet loss or wrong order)
                        if(bytesWritten > 1400 && bytesWritten != totalBytes){
                            //Debug message
//                            Log.d("PACKETS", "Packet incomplete: " + bytesWritten + "/" + totalBytes + " bytes");

                            //Since the packet is incomplete, set its type to NONE
                            headerIn = HEADER_TYPE_NONE;

                            //Reset the data
                            completeData = null;
                        }

                        //Debug message
                        //Log.d("TSE", "Received Full Packet of " + bytesWritten + " bytes");

                        //ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData());

                        //Read header
                        //final int headerIn = byteBuffer.getInt();

                        //Store the time at which the last packet was received (to check for timeout)
                        lastPacketReceived = Calendar.getInstance();

                        //If this is the first packet received, simply store it
                        if(lastPacketReceived == null){
                            lastPacketReceived = Calendar.getInstance();
                        //If this is not the first packet received, check if the delta time is longer than 5 seconds. If so, end the call because of timeout
                        }else if(Calendar.getInstance().getTimeInMillis() - lastPacketReceived.getTimeInMillis() > 5000 /* 5 seconds */){
                            activity.endCall();
                        }

                        //Check if the header type is a frame
                        if(headerIn == HEADER_TYPE_FRAME){

                            //Create array to hold data received
                            //dataReceived = new byte[bytesWritten + 1];
                            dataReceived = Arrays.copyOfRange(completeData, 0, bytesWritten);

                            //Store data received
                            //byteBuffer.get(dataReceived, 0, dataReceived.length);

                            //Display data that was just received
                            displayCurrentData(orientation);

                        //Check if the header type is audio
                        }else if(headerIn == HEADER_TYPE_AUDIO){
                            //audioReceived = new byte[bytesWritten + 1];
                            audioReceived = Arrays.copyOfRange(completeData, 0, bytesWritten);
                            //byteBuffer.get(audioReceived, 0, audioReceived.length);
//                            Log.d("UDP", "listening to audio");
                            listenCurrentData();

                        //Check if the header type is end
                        }else if(headerIn == HEADER_TYPE_END){
                            //Tell the CallActivity to end this call
//                            Log.d("UDPConnection", "HeaderIn");
                            activity.endCall();
                        }
                    }
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (SocketTimeoutException e){
                    e.printStackTrace();

                    //If the socket timed out, record the time at which it timed out
                    if(lastPacketReceived == null){
                        lastPacketReceived = Calendar.getInstance();

                    //If it has been longer than 5 seconds since the last packet, end the call (timeout)
                    }else if(Calendar.getInstance().getTimeInMillis() - lastPacketReceived.getTimeInMillis() > 5000 /* 5 seconds */){
//                        Log.d("TSE", "" + (Calendar.getInstance().getTimeInMillis() - lastPacketReceived.getTimeInMillis()));
                        activity.endCall();
                    }
                }catch (IOException e) {
                    e.printStackTrace();
                }

                //Sleep thread for 30 times per second
                try {
                    Thread.sleep(1000/30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}


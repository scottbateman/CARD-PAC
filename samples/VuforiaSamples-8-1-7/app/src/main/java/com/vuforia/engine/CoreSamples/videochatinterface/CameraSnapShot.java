package com.vuforia.engine.CoreSamples.videochatinterface;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.util.ArrayList;
import java.util.List;

/*
    This class manages the camera connections and image capture

    @author Nathan Sonier
 */
public class CameraSnapShot implements Runnable{

    //Reference to the call activity that is calling this class
    private CallActivity activity;

    //Reference to the client object that handles this class
    private UDPConnection client;

    //This holds the CameraManager instance, which controls when the camera is used, and how it is used
    private CameraManager cameraManager;

    //This is the unique ID of the currently active camera
    private String currentCameraID;

    //ID of the camera on the back of the phone
    private String backCameraID;

    //ID of the camera on the front of the phone
    private String frontCameraID;

    //Whether the phone is currently using the front facing camera or not
    private boolean isFrontCamera;

    //This is the camera that is currently being used
    private CameraDevice cameraDevice;

    //Surface on which we will display the preview of the camera (feed), along with the TextureView
    private ImageReader previewReader;

    //Default image format to be used with the image reader
    private final int DEFAULT_IMAGE_FORMAT = ImageFormat.YUV_420_888;

    //The resolution (width, height) of the image reader images
    private int imageWidth, imageHeight;

    //This is used to open a session with a camera, and it enables us to use it
    private CameraCaptureSession cameraCaptureSession;

    //This builder will generate a capture request so that we can receive images from it
    private CaptureRequest.Builder captureRequestBuilder;

    //Background thread that will do some additional work for the camera
    private HandlerThread backgroundThread;

    //Background handler that will hold the work passed to the thread
    private Handler backgroundHandler;

    //This is the callback that is called once we open (enable) a camera to use it
    private CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.d("CAMERA", "STARTED CAMERA");

            //Store the newly opened camera
            cameraDevice = camera;

            //Begin using the active camera
            startCamera();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            //Close the camera once we are done with it
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            //In case of an error, close the camera to leave it usable for other apps
            cameraDevice.close();
        }

        @Override
        public void onClosed(CameraDevice camera) {
            super.onClosed(camera);
            if(backgroundThread != null){
                backgroundThread.interrupt();
                backgroundThread = null;
            }

            if(cameraCaptureSession != null){
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }

            if(previewReader != null){
                previewReader.close();
                previewReader = null;
            }

        }
    };

    //This is the callback called after we create a capture session with the camera
    private CameraCaptureSession.StateCallback captureStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            //Store the newly created session as the active session
            cameraCaptureSession = session;

            //Begin displaying the preview to the user
            startPreview();
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Log.e("CameraCaptureSession", "Configuration failed");
        }
    };

    //This texture listener will be called when its surface is available (ready), or its surface changes (or is destroyed)
    private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //Create a list of surfaces that will be used to create a capture session

            Log.d("CAMERA", "TEXTURE AVAILABLE");
            //Get the surface texture on the TextureView instance and set a buffer size


        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    //This image listener will be called when there is a new frame ready to be sent
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //Send image to the UDP Connection instance, to be processed and sent over UDP
            sendImage(reader.acquireLatestImage());
        }
    };

    /*
        This constructor initializes the instance variables, starts the background thread, and then retrieves the Camera Manager instance from the system services
        @param actIn the activity that called this class
        @param clientIn the reference to the UDP Connection instance, which handles all the processing and UDP sending/receiving
     */
    public CameraSnapShot(CallActivity actIn, UDPConnection clientIn){

        activity = actIn;
        client = clientIn;

        backCameraID = "";
        frontCameraID = "";
        currentCameraID = "";

        imageWidth = 0;
        imageHeight = 0;

        //Start the background thread (for some of the processing done in this class)
        startBackgroundThread();

        //Initialize the active camera manager by retrieving the system service (from the context)
        cameraManager = (CameraManager) activity.getApplicationContext().getSystemService(activity.getApplicationContext().CAMERA_SERVICE);

    }

    /*
        Find the optimal camera (the ID) to use first (based on settings), as well as a secondary camera ID
     */
    private void setupCameraID(){
        try {
            //Create a list and populate it with the IDs of every camera that is available to use on this device
            String[] idList = cameraManager.getCameraIdList();

            //Iterate through each camera, and stop on the first camera that is not front-facing
            for(String id : idList){
                //Select the current camera (using the id)
                CameraCharacteristics current = cameraManager.getCameraCharacteristics(id);
                //Get the direction it is facing
                int facing = current.get(CameraCharacteristics.LENS_FACING);

                //If it is a front facing camera, choose this one to enable
                if(facing == CameraCharacteristics.LENS_FACING_FRONT){
                    //Store the camera ID as the active camera
                    currentCameraID = id;
                    isFrontCamera = true;
                    frontCameraID = id;
                }else{
                    //Otherwise, set the back camera
                    backCameraID = id;
                }
            }

            //If currentCameraID is still empty (either the front camera was not found, or it does not exist), set it to be the back camera
            if(currentCameraID.equals("") && !backCameraID.equals("")){
                currentCameraID = backCameraID;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*
        Initialize CameraDevice to open the camera (with currently used ID)
     */
    private void startCamera(){

        //Initialize a new Image Reader instance, with an image resolution and type
        previewReader = ImageReader.newInstance(640, 480, DEFAULT_IMAGE_FORMAT, 2);

        //Set the on image available listener to the listener we created as an instance variable
        previewReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

        //Create a list to store the surfaces to pass to the camera
        List<Surface> surfaces = new ArrayList<Surface>();

        //Add the image reader surface to the list
        surfaces.add(previewReader.getSurface());

        //Add the local view's surface to the list (for the small preview screen)
        //surfaces.add(activity.streamView.getHolder().getSurface());

        try {
            //Attempt to create a capture session with the surface list
            cameraDevice.createCaptureSession(surfaces, captureStateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*
        Initialize a capture request so that we start receiving frames on the target surfaces
     */
    private void startPreview(){
        try{
            //Initialize the capture request builder to help us generate a capture request for our preview
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            //Specify the target surface to send the frames (image reader)
            captureRequestBuilder.addTarget(previewReader.getSurface());

            //Specify the target surface to send the frames (local preview)
            //captureRequestBuilder.addTarget(activity.streamView.getHolder().getSurface());

            //If the current active camera is not null, use our builder to set a new capture request
            if(cameraDevice != null){
                //Set the capture request builder
                captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                try{
                    //Create the capture session using a repeating request (so that we can receive images as the maximum rate)
                    cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                    Log.d("CAMERA", "STARTED SESSION");
                }catch(CameraAccessException e){
                    e.printStackTrace();
                }
            }
        }catch(Exception e){
            Log.e("startPreview", "Could not start preview: " + e.getMessage());
        }
    }

    /*
        Process frames (converting to a byte array) and send new frame (image) to the client
        @param img the image object containing the data to be converted
     */
    public void sendImage(Image img){

        if(img != null){
            //Log.d("SIZE", "The sent image is " + img.getWidth() + " by " + img.getHeight());

            //Convert original frame (YUV 420 888 format) to NV21 format
            byte[] bytes = ImageUtils.YUV420toNV21(img);

            //Log.d("IMAGEXY", "" + img.getWidth() + " x " + img.getHeight());

            //Convert the NV21 format image to JPEG
            bytes = ImageUtils.NV21toJPEG(bytes, img.getWidth(), img.getHeight(), 50);

            //Send the converted frame to client
            client.receiveFromCamera(bytes);

            //Close the image to free up space for the next image to be received
            img.close();
        }

    }

    @Override
    /*
        This method is called when the thread running this class will be started. It opens a camera using the camera manager and the current active camera ID (checks for permissions first)
     */
    public void run() {
        //Determine the ID of the camera that will be used
        setupCameraID();

        try {
            Log.d("CAMERA", "OPENING CAMERA");
            //If the app has permission to use the camera, begin setup to use it
            if(ActivityCompat.checkSelfPermission(activity.getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(currentCameraID, cameraStateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*
        This method starts the underlying background thread that will handle retrieving frames from the camera
     */
    public void startBackgroundThread(){
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    /*
        Clears data from camera and closes it
     */
    public void stop(){
        if(cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
        }
        cameraManager = null;
    }

    /*
        This method switches between the front and back camera
     */
    public void switchCamera(){
        //Close the camera before switching
        closeCamera();

        //If the front camera is being used, switch to back camera. Otherwise, switch to front camera
        if(isFrontCamera){
            isFrontCamera = false;
            currentCameraID = backCameraID;
            try {
                Log.d("CAMERA", "OPENING CAMERA");
                //If the app has permission to use the camera, begin setup to use it
                if(ActivityCompat.checkSelfPermission(activity.getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(currentCameraID, cameraStateCallback, backgroundHandler);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }else{
            isFrontCamera = true;
            currentCameraID = frontCameraID;
            try {
                Log.d("CAMERA", "OPENING CAMERA");
                //If the app has permission to use the camera, begin setup to use it
                if(ActivityCompat.checkSelfPermission(activity.getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(currentCameraID, cameraStateCallback, backgroundHandler);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /*
        This method closes the camera session, as well the currently active camera
     */
    private void closeCamera(){
        cameraDevice.close();
    }

    /*
        This method retrieves the camera sensor's orientation and returns the value (so that we can rotate the image by that amount)
     */
    public int getTargetRotation(){
        try {
            int cameraOrientation = cameraManager.getCameraCharacteristics(currentCameraID).get(CameraCharacteristics.SENSOR_ORIENTATION);
            int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            return cameraOrientation;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch(IllegalArgumentException e){
            e.printStackTrace();
        } catch(NullPointerException e){
            e.printStackTrace();
        }
        return -1;
    }
}

# FaceDetectionCameraX
Face detection view implementation using CameraX and Firebase ML Kit with image Capture functionality

# Steps to run the app
Create firebase project and create android app in the firebase project with package ``` com.facedetection ```

### Add a Firebase configuration file
Add the Firebase Android configuration file to your app:
Click Download google-services.json to obtain your Firebase Android config file (google-services.json).
Move your config file into the module (app-level) directory of your app.
 Refer:https://firebase.google.com/docs/android/setup

### Then Run the App
you can listen to FaceDetectionView for call backs

```sh
 interface FaceDetectionListener {
        fun onSuccess(results: List<FirebaseVisionFace>)
        fun onFailed(errorMessage:String)
        fun onImageCaptured(uri: Uri)
    }
```

FaceDetectionView in XML with configurable properties

```sh
<com.facedetection.camerax.FaceDetectionView
        android:id="@+id/faceDetectionView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:captureIcon="@drawable/camera_capture48"
        app:flipIcon="@drawable/switch_camera_48"
        app:strokeWidth="2dp"
        app:strokeColor="@color/colorAccent"
        >

    </com.facedetection.camerax.FaceDetectionView>
    
```


    

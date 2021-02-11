package com.facedetection

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_camera_x.*

class CameraXActivity : AppCompatActivity() {
    private val cameraFacingKey = "cameraFacing"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_x)
        /*Important Don't use 0 as default value because for front camera the value is 0*/
        startCameraSource(savedInstanceState?.getInt(cameraFacingKey,-1)?:-1)
    }


    private fun startCameraSource(cameraFacing:Int){

        //val path = this.getExternalFilesDir(null)
        val  path = this.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        Log.i("PATH",path?.absolutePath?:"")
        path?.let {
            faceDetectionView?.setStorageDir(path)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
            if(cameraFacing!=-1) faceDetectionView?.setUpCamera(cameraFacing) else faceDetectionView?.setUpCamera()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(cameraFacingKey,faceDetectionView?.getCameraFacing()?:-1)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        faceDetectionView?.stop()
        super.onDestroy()
    }

}

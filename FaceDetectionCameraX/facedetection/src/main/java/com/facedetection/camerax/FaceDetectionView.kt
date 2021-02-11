package com.facedetection.camerax

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Environment
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FaceDetectionView(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {

    private val TAG = "FaceDetectView:CameraX"
    private val RATIO_4_3_VALUE = 4.0 / 3.0
    private val RATIO_16_9_VALUE = 16.0 / 9.0
    private var viewFinder = PreviewView(context)
    private val overlay: GraphicOverlay = GraphicOverlay(context, attrs)
    private val flipImageView: ImageView = ImageView(context)
    private val captureImageView: ImageView = ImageView(context)
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    @ColorInt
    private val strokeColor:Int
    private val strokeWidth:Int
    private var outputFileDir:File?=null
    private var onFaceDetectionListener: FaceDetectionListener?=null
    private val displayManager by lazy {
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    private var listener = object : FaceDetectionListener {
        override fun onImageCaptured(uri: Uri) {
            onFaceDetectionListener?.onImageCaptured(uri)
        }
        override fun onSuccess(results: List<FirebaseVisionFace>) {
            onFaceDetectionListener?.onSuccess(results)
            setCaptureVisibility(results.isNotEmpty())
        }

        override fun onFailed(errorMessage: String) {
            onFaceDetectionListener?.onFailed(errorMessage)
            setCaptureVisibility(false)
        }
    }

    init {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.FaceDetectionView, 0, 0)
        strokeColor = a.getColor(R.styleable.FaceDetectionView_strokeColor, Color.WHITE)
        strokeWidth=  a.getDimension(R.styleable.FaceDetectionView_strokeWidth,context.resources.getDimension(R.dimen.default_stroke)).toInt()

        val tempLayoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END)
        tempLayoutParams.setMargins(0, context.resources.getDimensionPixelSize(R.dimen.margin_24), context.resources.getDimensionPixelSize(R.dimen.margin_24), 0)
        val flipIcon = a.getResourceId(R.styleable.FaceDetectionView_flipIcon,R.drawable.flip_camera_48dp)
        flipImageView.setImageResource(flipIcon)
        flipImageView.layoutParams = tempLayoutParams
        flipImageView.visibility = View.GONE

        flipImageView.setOnClickListener {
            flip()
        }
        val captureIcon = a.getResourceId(R.styleable.FaceDetectionView_captureIcon,R.drawable.camera_white_48dp)
        captureImageView.setImageResource(captureIcon)
        val captureLayoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        captureLayoutParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        captureLayoutParams.setMargins(0, 0, 0, context.resources.getDimensionPixelSize(R.dimen.margin_48))
        captureImageView.layoutParams = captureLayoutParams
        setCaptureVisibility(false)

        captureImageView.setOnClickListener {
            captureImage()
        }


        addView(viewFinder)
        addView(overlay)
        addView(flipImageView, tempLayoutParams)
        addView(captureImageView, captureLayoutParams)
        outputFileDir =  context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    }

    private fun setCaptureVisibility(visible: Boolean) {
        if (visible && imageCapture != null) {
            captureImageView.visibility = View.VISIBLE
        } else {
            captureImageView.visibility = View.GONE
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == this@FaceDetectionView.displayId) {
                Log.d(TAG, "Rotation changed: ${viewFinder.display.rotation}")
                imageCapture?.targetRotation = viewFinder.display.rotation
                imageAnalyzer?.targetRotation = viewFinder.display.rotation
            }
        }
    }

    private fun captureImage() {

        // Create output options object which contains file
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            File(
               outputFileDir,
                "detectedFace${System.currentTimeMillis()}" + ".jpg"
            )
        )
            .build()

        imageCapture?.takePicture(outputOptions, cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if(outputFileResults.savedUri==null){
                        onFaceDetectionListener?.onFailed("Image Saved Uri is null")
                    }else{
                        outputFileResults.savedUri?.let { uri-> onFaceDetectionListener?.onImageCaptured(uri) }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onFaceDetectionListener?.onFailed(exception.message?:"")
                }

            })
    }

    private fun flip() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindCameraUseCases()
    }

    fun stop() {
        displayManager.unregisterDisplayListener(displayListener)
        cameraExecutor.shutdown()
    }

    fun getCameraFacing():Int{
        return lensFacing
    }
    
    fun setStorageDir(file: File){
        if(file.isDirectory){
            outputFileDir =  file
        }else{
           throw IllegalArgumentException(file.absolutePath+"is not a dir")
        }
    }

    fun setUpCamera(cameraFacing:Int=CameraSelector.LENS_FACING_BACK,onFaceDetectionListener: FaceDetectionListener? = null) {
        displayManager.registerDisplayListener(displayListener, null)
        onFaceDetectionListener?.let {
            this.onFaceDetectionListener = it
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        viewFinder.post {
            displayId = viewFinder.display.displayId
            cameraProviderFuture.addListener(Runnable {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                if(cameraFacing == CameraSelector.LENS_FACING_BACK){
                    // Select lensFacing depending on the available cameras
                    lensFacing = when {
                        hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                        hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                        else -> throw IllegalStateException("Back and front camera are unavailable")
                    }
                }else if(cameraFacing == CameraSelector.LENS_FACING_FRONT){
                    // Select lensFacing depending on the available cameras
                    lensFacing = when {
                        hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                        hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                        else -> throw IllegalStateException("Back and front camera are unavailable")
                    }
                }



                if (hasBackCamera() && hasFrontCamera()) {
                    flipImageView.visibility = View.VISIBLE
                }

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")
        val screenSize = Size(metrics.widthPixels, metrics.heightPixels)
        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")
        val rotation = viewFinder.display.rotation
        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
            // We request aspect ratio but no resolution
            //.setTargetAspectRatio(screenAspectRatio)
            .setTargetResolution(screenSize)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build()

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetResolution(screenSize)
            // We request aspect ratio but no resolution to match preview config, but letting
            // CameraX optimize for whatever specific resolution best fits our use cases
            //.setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()

        // ImageAnalysis
        imageAnalyzer = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            //.setTargetAspectRatio(screenAspectRatio)
            .setTargetResolution(screenSize)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()
            // The analyzer can then be assigned to the instance
            .also {
                it.setAnalyzer(cameraExecutor, FaceAnalyzer(overlay,strokeColor,strokeWidth, listener))
            }

        val min = min(metrics.widthPixels, metrics.heightPixels)
        val max = max(metrics.widthPixels, metrics.heightPixels)
        if (isPortraitMode) {
            overlay.setImageSourceInfo(min, max, lensFacing == CameraSelector.LENS_FACING_FRONT)
        } else {
            overlay.setImageSourceInfo(max, min, lensFacing == CameraSelector.LENS_FACING_FRONT)
        }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                (context as Activity) as LifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalyzer
            )
            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(viewFinder.createSurfaceProvider())

        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private val isPortraitMode: Boolean
        get() {
            val orientation = context.resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                return false
            }
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                return true
            }

            Log.d(TAG, "isPortraitMode returning false by default")
            return false
        }

    interface FaceDetectionListener {
        fun onSuccess(results: List<FirebaseVisionFace>)
        fun onFailed(errorMessage:String)
        fun onImageCaptured(uri: Uri)
    }

}
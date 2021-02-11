package com.facedetection.camerax

import android.util.Log
import androidx.annotation.ColorInt
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions

class FaceAnalyzer(private var graphicOverlay: GraphicOverlay, @ColorInt private var strokeColor:Int, private var strokeWidth:Int,
                   private var  facedetectlistener: FaceDetectionView.FaceDetectionListener) : ImageAnalysis.Analyzer {


    private val faceDetectionOptions = FirebaseVisionFaceDetectorOptions.Builder()
        .build()

    private val faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(faceDetectionOptions)

    override fun analyze(image: ImageProxy) {
        graphicOverlay.clear()

        val metadata = FirebaseVisionImageMetadata.Builder()
            .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
            .setWidth(image.width)
            .setHeight(image.height)
            .setRotation(degreesToFirebaseRotation(image.imageInfo.rotationDegrees))
            .build()

        val fireBaseVisionImage =
            FirebaseVisionImage.fromByteBuffer(image.planes[0].buffer, metadata)

        faceDetector.detectInImage(fireBaseVisionImage)
            .addOnSuccessListener { results ->
                image.close()
                for (i in results.indices) {
                    val face = results[i]

                    face?.let {
                        val faceGraphic = FaceGraphic(graphicOverlay,strokeColor,strokeWidth,face)
                        graphicOverlay.add(faceGraphic)
                    }
                }
                    graphicOverlay.postInvalidate()
                facedetectlistener.onSuccess(results)
            }
            .addOnFailureListener { e ->
                image.close()
                facedetectlistener.onFailed(e.message?:"")
                Log.i("Face Detection Failed", e.message?:"")
            }
    }

    private fun degreesToFirebaseRotation(degrees: Int): Int = when (degrees) {
        0 -> FirebaseVisionImageMetadata.ROTATION_0
        90 -> FirebaseVisionImageMetadata.ROTATION_90
        180 -> FirebaseVisionImageMetadata.ROTATION_180
        270 -> FirebaseVisionImageMetadata.ROTATION_270
        else -> throw Exception("Rotation must be 0, 90, 180, or 270.")
    }

}
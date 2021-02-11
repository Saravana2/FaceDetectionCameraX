package com.facedetection.camerax

import android.graphics.*
import android.graphics.Paint.Style
import androidx.annotation.ColorInt
import com.google.firebase.ml.vision.face.FirebaseVisionFace

/**
 * Graphic instance for rendering face position, orientation, and landmarks within an associated
 * graphic overlay view.
 */
class FaceGraphic(overlay: GraphicOverlay, @ColorInt private var strokeColor:Int, private var strokeWid:Int, private var firebaseVisionFace: FirebaseVisionFace) :
    GraphicOverlay.Graphic(overlay) {

    /**
     * Draws the face annotations for position on the supplied canvas.
     */

    private val boxPaint = Paint().apply {
        color = strokeColor
        style = Style.STROKE
        strokeWidth = strokeWid.toFloat()
    }

    override fun draw(canvas: Canvas) {

        val x = translateX(firebaseVisionFace.boundingBox.centerX().toFloat())
        val y = translateY(firebaseVisionFace.boundingBox.centerY().toFloat())

        // Draws a bounding box around the face.
        val xOffset = scale(firebaseVisionFace.boundingBox.width() / 2.0f)
        val yOffset = scale(firebaseVisionFace.boundingBox.height() / 2.0f)
        val left = x - xOffset
        val top = y - yOffset
        val right = x + xOffset
        val bottom = y + yOffset
        canvas.drawRect(left, top, right, bottom, boxPaint)

    }
}

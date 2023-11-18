package com.example.camera2apiv1

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    lateinit var capReq: CaptureRequest.Builder
    lateinit var handler: Handler
    lateinit var handlerThread: HandlerThread
    lateinit var cameraManager:CameraManager
    lateinit var textureView: TextureView
    lateinit var cameraCaptureSession: CameraCaptureSession
    lateinit var cameraDevice: CameraDevice
    lateinit var captureRequest: CaptureRequest
    lateinit var imageReader : ImageReader

    private val ISO_VALUE = 1000
    private val SHUTTER_SPEED = 1000000000L / 1500


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permisions()



        textureView = findViewById(R.id.textureView)
        cameraManager=getSystemService(Context.CAMERA_SERVICE) as CameraManager
        handlerThread=HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler((handlerThread).looper)

        textureView.surfaceTextureListener= object:TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                open_camera()
                configureTransform(width, height)
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

            }
        }
        try {
            // Your image capture code
        } catch (e: Exception) {
            e.printStackTrace()
            // Handle the exception
        }
        imageReader= ImageReader.newInstance(1080,1920,ImageFormat.JPEG,2)
        imageReader.setOnImageAvailableListener(object : ImageReader.OnImageAvailableListener{
            override fun onImageAvailable(reader: ImageReader?) {
                var image = reader?.acquireLatestImage()
                var buffer = image!!.planes[0].buffer
                var bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                val picturesDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                var file = File(picturesDirectory, UUID.randomUUID().toString() + ".jpg");
                var opStream = FileOutputStream(file)
                opStream.write(bytes)
                opStream.close()
                image.close()


                Toast.makeText(this@MainActivity,"image Captured",Toast.LENGTH_LONG).show()
            }
        },handler)

        findViewById<Button>(R.id.capture).apply {
            setOnClickListener {
                try {
                    capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    capReq.addTarget(imageReader.surface)
                    cameraCaptureSession.capture(capReq.build(), null, null)

                    val filePath = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "img.jpeg").absolutePath
                    Log.d("CaptureButton", "Image will be saved at: $filePath")
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Handle the exception, e.g., show an error message
                }
            }
        }


    }

    override fun onDestroy(){
        super.onDestroy()
        cameraDevice.close()
        handler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()
    }
    @SuppressLint("MissingPermission")
    fun open_camera() {
        try {
            // Your camera initialization code
            cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    var surface = Surface(textureView.surfaceTexture)

                    // Configuración de la velocidad de obturación, ISO y exposición
                    capReq.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
                    capReq.set(CaptureRequest.SENSOR_SENSITIVITY, ISO_VALUE)
                    capReq.set(CaptureRequest.SENSOR_EXPOSURE_TIME, SHUTTER_SPEED)
                    capReq.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                    //capReq.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0) // Exposición a 0

                    capReq.addTarget(surface)
                    cameraDevice.createCaptureSession(
                        listOf(surface, imageReader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                cameraCaptureSession = session
                                cameraCaptureSession.setRepeatingRequest(capReq.build(), null, null)
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {

                            }
                        },
                        handler
                    )
                }

                override fun onClosed(camera: CameraDevice) {
                    super.onClosed(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {

                }

                override fun onError(camera: CameraDevice, error: Int) {

                }
            }, handler)
        } catch (e: Exception) {
            e.printStackTrace()
            // Handle the exception
        }

    }
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, imageReader.height.toFloat(), imageReader.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
        val scale = Math.max(
            viewHeight.toFloat() / imageReader.height,
            viewWidth.toFloat() / imageReader.width
        )
        matrix.postScale(scale, scale, centerX, centerY)
        textureView.setTransform(matrix)
    }
    fun get_permisions(){
        var permissionLst = mutableListOf<String>()
        if (checkSelfPermission(android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            permissionLst.add(android.Manifest.permission.CAMERA)
        }
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            permissionLst.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            permissionLst.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if(permissionLst.size>0){
            requestPermissions(permissionLst.toTypedArray(),101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        grantResults.forEach { it
            if(it!=PackageManager.PERMISSION_GRANTED){
                get_permisions()
            }
        }
    }
}
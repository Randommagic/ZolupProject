package org.tensorflow.codelabs.objectdetection

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.text
import kotlinx.android.synthetic.main.activity_spot.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import org.tensorflow.codelabs.objectdetection.ml.Resnet
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min

class SpotActivity : AppCompatActivity() {
    companion object {
        const val TAG = "TFLite - ODT"
        const val REQUEST_IMAGE_CAPTURE: Int = 1
        const val REQUEST_OPEN_GALLERY: Int = 2
        const val REQ_PERMISSION_CAMERA: Int = 1001
        const val REQ_PERMISSION_GALLERY: Int = 1002
    }
    private lateinit var bitmap: Bitmap
    private lateinit var currentPhotoPath: String
    private lateinit var maxResLabel: String
    private lateinit var maxYoloLabel: ArrayList<String>
    private lateinit var horizon1: Animation
    private lateinit var horizon2: Animation
    private lateinit var horizon3: Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spot)
        overridePendingTransition(0, 0)

        maxYoloLabel = arrayListOf("zero")

        horizon1 = AnimationUtils.loadAnimation(this,R.anim.horizon_enter1)
        horizon2 = AnimationUtils.loadAnimation(this,R.anim.horizon_enter2)
        horizon3 = AnimationUtils.loadAnimation(this,R.anim.horizon_enter3)

        text.startAnimation(horizon1)
        btn_capture.startAnimation(horizon2)
        btn_gallery.startAnimation(horizon3)

        // ?????????
        btn_capture.setOnClickListener(View.OnClickListener {
            try {
                if(cameraPermissionGranted()) {
                    dispatchTakePictureIntent()
                }
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, e.message.toString())
            }
        })
        // ?????????
        btn_gallery.setOnClickListener(View.OnClickListener {
            try {
                if(galleryPermissionGranted()) {
                    openGallery()
                }
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, e.message.toString())
            }
        })
    }
    // ???????????? ???????????? ??? ????????? ?????? ??????
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //?????????
        if (resultCode == Activity.RESULT_OK &&
            requestCode == REQUEST_IMAGE_CAPTURE
        ) {
            val resultIntent = Intent(this, SpotResultActivity::class.java)
            showLoadingDialogAndMakeLabel(getCapturedImage(),resultIntent)
        }
        //?????????
        if(requestCode == REQUEST_OPEN_GALLERY) {
            if(resultCode == RESULT_OK) {
                val currentImageUri = data?.data
                try{
                    currentImageUri?.let {
                        val bitmap = MediaStore.Images.Media.getBitmap(
                            this.contentResolver,
                            currentImageUri
                        )
                        val resultIntent = Intent(this, SpotResultActivity::class.java)
                        showLoadingDialogAndMakeLabel(bitmap,resultIntent)
                    }
                }catch(e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    //??????????????? ??????, ????????? ??????, ?????? ??????
    private fun showLoadingDialogAndMakeLabel(bitmap: Bitmap,resultIntent: Intent) {
        val dialog = LoadingDialog(this@SpotActivity)
        CoroutineScope(Main).launch{
            dialog.show()
            delay(100)
            setViewAndDetectYolo(bitmap)
            setViewAndDetectResnet(bitmap)
            delay(1)
            dialog.dismiss()
            resultIntent.putExtra("res", maxResLabel)
            resultIntent.putExtra("yolo", maxYoloLabel)
            startActivity(resultIntent)
            overridePendingTransition(0, 0)
            maxYoloLabel.clear()
            maxYoloLabel.add("zero")
        }
    }
    //-------------------------------------------------------------------------------------
    //                                         ?????????
    //-------------------------------------------------------------------------------------
    // Yolo ??????
    private fun setViewAndDetectYolo(bitmap: Bitmap) {
        val image = TensorImage.fromBitmap(bitmap)
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(5)
            .setScoreThreshold(0.5f)
            .build()

        val detector = ObjectDetector.createFromFileAndOptions(
            this,
            "model.tflite", // Yolo ??????
            options
        )

        // ?????? ?????? ??? ????????? ??????
        val results = detector.detect(image)
        // ????????????
        val resultToDisplay = results.map {
            // ?????????(first)???????????? ???????????? ????????????
            val category = it.categories.first()
            val text = category.label
            // boundingBox??? ??????
            DetectionResult(it.boundingBox, text)
        }
        resultToDisplay.forEach {
            maxYoloLabel.add(it.text)
        }
    }
    // resnet ??????
    private fun setViewAndDetectResnet(bitmap: Bitmap){
        val model = Resnet.newInstance(this)
        // input ??????
        val image = TensorImage.fromBitmap(bitmap)
        // ?????? ?????? ??? ????????? ??????
        val outputs = model.process(image)
        val probability = outputs.probabilityAsCategoryList
        // ?????? ?????? score??? ??? label
        maxResLabel = probability.maxByOrNull { it!!.score }?.label!!
        // ?????? ??????
        model.close()
    }
    //-------------------------------------------------------------------------------------
    //                                         ?????????
    //-------------------------------------------------------------------------------------
    // capture ?????? ?????? ??? ???????????? ?????????
    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // ????????? ?????? ??? ??????(??????) ??????
            takePictureIntent.resolveActivity(packageManager)?.also {
                // ?????? ?????? ??????
                val photoFile: File? = try {
                    createImageFile()
                } catch (e: IOException) {
                    Log.e(TAG, e.message.toString())
                    null
                }
                //?????? ?????? ?????? ???
                photoFile?.also {
                    // path??? ?????? ?????? ??????
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "org.tensorflow.codelabs.objectdetection.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    //onActivityResult??? ??????(???????????? ????????? Main???????????? ?????????)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }
    // ????????? ?????? ?????????????????? ?????? -> ????????? ?????? ????????? ???????????? ??????
    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // ????????????????????? ??????
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // ???????????? : ACTION_VIEW intents ?????? ?????? ??? ??????
            currentPhotoPath = absolutePath
        }
    }
    // ????????? ???????????? ?????? ????????? ?????? ??????
    private fun getCapturedImage(): Bitmap {
        // ???????????? ??????
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val targetW: Int = size.x
        val targetH: Int = size.y

        val bmOptions = BitmapFactory.Options().apply {
            // ???????????? ?????? ?????????
            inJustDecodeBounds = true

            BitmapFactory.decodeFile(currentPhotoPath, this)

            val photoW: Int = outWidth
            val photoH: Int = outHeight

            // ????????? ?????? ?????? ??????
            val scaleFactor: Int = max(1, min(photoW / targetW, photoH / targetH))

            // ??????????????? ???????????? ???????????? ?????????????????? ?????????
            inJustDecodeBounds = false
            inSampleSize = scaleFactor
            inMutable = true
        }
        val exifInterface = ExifInterface(currentPhotoPath)
        val orientation = exifInterface.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                rotateImage(bitmap, 90f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                rotateImage(bitmap, 180f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                rotateImage(bitmap, 270f)
            }
            else -> {
                bitmap
            }
        }
    }
    // ????????? ????????? ?????? ??? ????????? ??????
    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }
    //-------------------------------------------------------------------------------------
    //                                         ?????????
    //-------------------------------------------------------------------------------------
    // ???????????? ???
    private fun openGallery(){
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_OPEN_GALLERY)
    }
    //-------------------------------------------------------------------------------------
    //                                         ??????
    //-------------------------------------------------------------------------------------
    // ?????? ?????? ??????
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSION_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(this,"????????? ?????? ?????? ????????? ????????? ??? ????????????.", Toast.LENGTH_SHORT).show()
            }
        }
        if (requestCode == REQ_PERMISSION_GALLERY){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                openGallery()
            } else{
                Toast.makeText(this,"????????? ?????? ?????? ????????? ????????? ??? ????????????.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // ?????? ??????
    private fun cameraPermissionGranted(): Boolean {
        val preference = getPreferences(Context.MODE_PRIVATE)
        val isFirstCheck = preference.getBoolean("isFirstPermissionCheck", true)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                // ????????? ?????? ??? ???????????? ??????
                val snackBar = Snackbar.make(spot_layout, "????????? ???????????????", Snackbar.LENGTH_INDEFINITE)
                snackBar.setAction("????????????") {
                    ActivityCompat.requestPermissions(this,
                        arrayOf(
                            Manifest.permission.CAMERA
                        ), REQ_PERMISSION_CAMERA
                    )
                }
                snackBar.show()
            } else { //???????????????, Don't ask again??? ????????? ??????
                if (isFirstCheck) {
                    // ?????? ???????????? ????????? ??????
                    preference.edit().putBoolean("isFirstPermissionCheck", false).apply()
                    // ????????????
                    ActivityCompat.requestPermissions(this,
                        arrayOf(
                            Manifest.permission.CAMERA
                        ), REQ_PERMISSION_CAMERA
                    )
                } else {
                    // ???????????? ????????? ??????????????? ?????? ???????????? ????????? ????????? ??????
                    // requestPermission??? ???????????? ?????? ???????????? ?????? ????????? ??????????????? ??????
                    val snackBar = Snackbar.make(spot_layout, "????????? ??????????????? ????????? ???????????? ???????????????", Snackbar.LENGTH_INDEFINITE)
                    snackBar.setAction("??????") {
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    }
                    snackBar.show()
                }
            }
            return false
        } else return true
    }
    private fun galleryPermissionGranted(): Boolean {
        val preference = getPreferences(Context.MODE_PRIVATE)
        val isFirstCheck = preference.getBoolean("isFirstPermissionCheck", true)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // ????????? ?????? ??? ???????????? ??????
                val snackBar = Snackbar.make(spot_layout, "????????? ???????????????", Snackbar.LENGTH_INDEFINITE)
                snackBar.setAction("????????????") {
                    ActivityCompat.requestPermissions(this,
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ), REQ_PERMISSION_GALLERY
                    )
                }
                snackBar.show()
            } else {
                if (isFirstCheck) {
                    // ?????? ???????????? ????????? ??????
                    preference.edit().putBoolean("isFirstPermissionCheck", false).apply()
                    // ????????????
                    ActivityCompat.requestPermissions(this,
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ), REQ_PERMISSION_GALLERY
                    )
                } else {
                    // ???????????? ????????? ??????????????? ?????? ???????????? ????????? ????????? ??????
                    // requestPermission??? ???????????? ?????? ???????????? ?????? ????????? ??????????????? ?????????
                    val snackBar = Snackbar.make(spot_layout, "??????????????? ??????????????? ????????? ???????????? ???????????????", Snackbar.LENGTH_INDEFINITE)
                    snackBar.setAction("??????") {
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    }
                    snackBar.show()
                }
            }
            return false
        } else {
            return true
        }
    }
}
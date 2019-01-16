package com.example.kirich.picmerger

import android.R.attr.*
import android.app.Activity
import android.app.PendingIntent.getActivity
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.content.res.AssetFileDescriptor
import com.nbsp.materialfilepicker.ui.FilePickerActivity
import java.util.regex.Pattern
import com.nbsp.materialfilepicker.MaterialFilePicker
import android.graphics.Bitmap
import android.util.Log
import android.graphics.BitmapFactory
import android.graphics.Picture
import android.media.*
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.support.v4.content.ContextCompat
import org.jcodec.api.SequenceEncoder
import org.jcodec.api.android.AndroidSequenceEncoder
import org.jcodec.common.model.ColorSpace.RGB
import android.provider.MediaStore
import android.view.View
import android.widget.*
import com.googlecode.mp4parser.FileDataSourceImpl
import com.googlecode.mp4parser.authoring.Movie
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator
import com.googlecode.mp4parser.authoring.tracks.AACTrackImpl
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack
import com.googlecode.mp4parser.util.ByteBufferByteChannel
import kotlinx.android.synthetic.main.activity_main.*
import org.jcodec.containers.mp4.MP4Util
import java.io.*
import java.lang.Long.max
import java.lang.Long.min
import java.nio.ByteBuffer
import java.nio.channels.FileChannel


class MainActivity : AppCompatActivity() {
    private val pickImageReqCode = 1
    private val pickAudioReqCode = 2

    private var imagePathList: List<String>? = null
    private var bitmapList: MutableList<Bitmap?>? = null

    private fun loadImages() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), pickImageReqCode)
    }

    private fun copyFile(input: InputStream, out: OutputStream) {
        val buffer = ByteArray(1024)
        var read = input.read(buffer)
        while (read != -1) {
            out.write(buffer, 0, read)
            read = input.read(buffer)
        }
    }

    private fun merge() {
        Thread {
            hintTextView?.post{ hintTextView?.visibility = View.INVISIBLE }
            progressBar?.post { progressBar?.visibility = View.VISIBLE }
            val frameRate = 1
            var maxProgress: Int? = (bitmapList?.size as Int) * frameRate
            progressBar?.post { progressBar?.max = maxProgress as Int }
            progressBar?.post { progressBar?.progress = 0 }

            val file = File(applicationContext.filesDir, "example.mp4")
            val enc = AndroidSequenceEncoder.createSequenceEncoder(file, frameRate)
            val videoPath = file.absolutePath

            Log.d("kirich", "File created")
            for (i: Bitmap? in bitmapList!!) {
                for (j in 0..frameRate) {
                    if (i != null) {
                        progressBar?.post { progressBar?.progress = progressBar?.progress as Int + 1 }
                        enc.encodeImage(i)
                    }
                    else {
                        Log.d("kirich", "bitmap is null")
                    }
                }
            }
            enc.finish()
            progressBar?.post { progressBar?.visibility = View.INVISIBLE }
            hintTextView?.post { hintTextView?.text = "Done!" }
            hintTextView?.post { hintTextView?.visibility = View.VISIBLE }

            val outputFile = File(applicationContext.filesDir,"output.mp4")
            outputFile.createNewFile()
            val filePath = outputFile.absolutePath

            val inputStream = resources.openRawResource(R.raw.music)

            //val audioPath = File("android.resource://" + packageName + "/raw/music")
            var tempFile = File.createTempFile("audio", ".aac")
            copyFile(inputStream, FileOutputStream(tempFile))
            var audioFile = tempFile.absolutePath

            var audioTrack = AACTrackImpl(FileDataSourceImpl(audioFile))
            var size = min(audioTrack.samples.size.toLong() / 94, bitmapList!!.size.toLong())
            val aacTrackShort = CroppedTrack(audioTrack, 0, size * 94)

            var video = MovieCreator.build(videoPath)
            video.addTrack(aacTrackShort)

            var out = DefaultMp4Builder().build(video)
            var fos = FileOutputStream(filePath).channel
            out.writeContainer(fos)
            fos.close()

            videoView.post {
                videoView.visibility = View.VISIBLE
                videoView.setVideoPath(filePath)
                videoView.start()
            }

            hintTextView?.post {
                hintTextView.setText("!Done!")
            }

        }.start()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var controller = MediaController(this)
        videoView.setMediaController(controller)
        controller.setAnchorView(videoView)

        loadImagesBtn.setOnClickListener { loadImages() }
        mergeBtn.setOnClickListener { merge() }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == pickImageReqCode && resultCode == Activity.RESULT_OK) {
            imagePathList = ArrayList()
            bitmapList = ArrayList()

            if (data?.clipData != null) {
                val count = data.clipData.itemCount
                for (i in 0 until count) {
                    val imageUri = data.clipData.getItemAt(i).uri
                    val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
                    bitmapList?.add(bitmap)
                }
            }
        }
    }
}

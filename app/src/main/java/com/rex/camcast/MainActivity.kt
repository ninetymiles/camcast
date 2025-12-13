package com.rex.camcast

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioFormat
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.rex.camcast.data.rotation.RotationRepository
import com.rex.camcast.databinding.ActivityMainBinding
import com.rex.camcast.preference.PreferenceViewActivity
import com.rex.camcast.utils.PermissionsManager
import com.rex.camcast.utils.showDialog
import com.rex.camcast.utils.toast
import io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.MicrophoneSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.defaultCameraId
import io.github.thibaultbee.streampack.core.interfaces.setCameraId
import io.github.thibaultbee.streampack.core.interfaces.startStream
import io.github.thibaultbee.streampack.core.streamers.dual.DualStreamer
import io.github.thibaultbee.streampack.core.streamers.lifecycle.StreamerActivityLifeCycleObserver
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import io.github.thibaultbee.streampack.core.utils.extensions.isClosedException
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val streamerRequiredPermissions =
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    @SuppressLint("MissingPermission")
    private val permissionsManager = PermissionsManager(
        this,
        streamerRequiredPermissions,
        onAllGranted = { onPermissionsGranted() },
        onShowPermissionRationale = { permissions, onRequiredPermissionLastTime ->
            // Explain why we need permissions
            showDialog(
                title = "Permissions denied",
                message = "Explain why you need to grant $permissions permissions to stream",
                positiveButtonText = R.string.accept,
                onPositiveButtonClick = { onRequiredPermissionLastTime() },
                negativeButtonText = R.string.denied
            )
        },
        onDenied = {
            showDialog(
                "Permissions denied",
                "You need to grant all permissions to stream",
                positiveButtonText = 0,
                negativeButtonText = 0
            )
        })

    /**
     * The streamer is the central object of StreamPack.
     * It is responsible for the capture audio and video and the streaming process.
     *
     * If you need only 1 output (live only or record only), use [SingleStreamer].
     * If you need 2 outputs (live and record), use [DualStreamer].
     */
    private val streamer by lazy {
        // 1 output
        SingleStreamer(
            this, withAudio = true, withVideo = true
        )
        // 2 outputs: uncomment the line below
        /*
        DualStreamer(
            this,
            withAudio = true,
            withVideo = true
        )
        */
    }

    /**
     * Listen to lifecycle events. So we don't have to stop the streamer manually in `onPause` and release in `onDestroy
     */
    private val streamerLifeCycleObserver by lazy { StreamerActivityLifeCycleObserver(streamer) }

    /**
     * Listen to device rotation.
     */
    private val rotationRepository by lazy { RotationRepository.getInstance(applicationContext) }

    /**
     * A LiveData to observe the connection state.
     */
    private val isTryingConnectionLiveData = MutableLiveData<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindProperties()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        getMenuInflater().inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.getItemId()
        if (id == R.id.action_test) {
            return true
        } else if (id == R.id.action_settings) {
            val intent: Intent = Intent(this, PreferenceViewActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            startActivity(intent)
            return true
        } else if (id == R.id.action_exit) {
            finishAndRemoveTask()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun bindProperties() {
        binding.liveButton.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) {
                if (isChecked) {
                    /**
                     * Dispatch from main thread is forced to avoid making network call on main thread
                     * with coroutines.
                     */
                    lifecycleScope.launch {
                        try {
                            isTryingConnectionLiveData.postValue(true)
                            /**
                             * For SRT, use srt://my.server.url:9998?streamid=myStreamId&passphrase=myPassphrase
                             */
                            streamer.startStream("rtmp://192.168.88.62:1935/live/1")
                            //streamer.startStream("srt://192.168.88.62:8080/publish/live")
                        } catch (e: Exception) {
                            binding.liveButton.isChecked = false
                            Log.e(TAG, "Failed to connect", e)
                            toast("Connection failed: ${e.message}")
                        } finally {
                            isTryingConnectionLiveData.postValue(false)
                        }
                    }
                } else {
                    lifecycleScope.launch {
                        streamer.stopStream()
                    }
                }
            }
        }

        bindAndPrepareStreamer()
    }

    private fun bindAndPrepareStreamer() {
        // Register the lifecycle observer
        lifecycle.addObserver(streamerLifeCycleObserver)

        // Configure the streamer
        configureStreamer()

        // Listen to rotation
        lifecycleScope.launch {
            rotationRepository.rotationFlow.collect {
                streamer.setTargetRotation(it)
            }
        }

        // Lock and unlock orientation on isStreaming state.
        lifecycleScope.launch {
            streamer.isStreamingFlow.collect { isStreaming ->
                if (isStreaming) {
                    lockOrientation()
                } else {
                    unlockOrientation()
                }
                if (isStreaming) {
                    binding.liveButton.isChecked = true
                } else if (isTryingConnectionLiveData.value == true) {
                    binding.liveButton.isChecked = true
                } else {
                    binding.liveButton.isChecked = false
                }
            }
        }

        // General error handling
        lifecycleScope.launch {
            streamer.throwableFlow.filterNotNull().filter { !it.isClosedException }
                .collect { throwable ->
                    Log.e(TAG, "Error: ${throwable.message}", throwable)
                    toast("Error: ${throwable.message}")
                }
        }

        // Connection error handling
        lifecycleScope.launch {
            streamer.throwableFlow.filterNotNull().filter { it.isClosedException }
                .collect { throwable ->
                    Log.e(TAG, "Connection lost: ${throwable.message}", throwable)
                    toast("Connection lost: ${throwable.message}")
                }
        }
    }

    private fun lockOrientation() {
        /**
         * Lock orientation while stream is running to avoid stream interruption if
         * user turns the device.
         * For landscape only mode, set [requireActivity().requestedOrientation] to
         * [ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE].
         */
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }

    private fun unlockOrientation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onStart() {
        super.onStart()
        permissionsManager.requestPermissions()
    }

    @RequiresPermission(allOf = [Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO])
    private fun onPermissionsGranted() {
        setAVSource()
        setStreamerView()
    }

    @RequiresPermission(allOf = [Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO])
    private fun setAVSource() {
        // Set audio and video sources.
        lifecycleScope.launch {
            streamer.setAudioSource(MicrophoneSourceFactory())
            streamer.setCameraId(this@MainActivity.defaultCameraId)
        }
    }

    private fun setStreamerView() {
        binding.preview.streamer = streamer // Bind the streamer to the preview
        lifecycleScope.launch {
            binding.preview.startPreview()
        }
    }

    @SuppressLint("MissingPermission")
    private fun configureStreamer() {
        /**
         * To get the parameters supported by the device, the [SingleStreamer] have a
         * [SingleStreamer.getInfo] method.
         */

        /**
         * There are other parameters in the [VideoConfig] such as:
         * - bitrate
         * - profile
         * - level
         * - gopSize
         * They will be initialized with an appropriate default value.
         */
        val videoConfig = VideoConfig(
            //mimeType = MediaFormat.MIMETYPE_VIDEO_AVC, resolution = Size(1280, 720), fps = 25
            mimeType = MediaFormat.MIMETYPE_VIDEO_AVC, resolution = Size(1920, 1080), fps = 25
        )

        /**
         * There are other parameters in the [AudioConfig] such as:
         * - byteFormat
         * - enableEchoCanceler
         * - enableNoiseSuppressor
         * They will be initialized with an appropriate default value.
         */
        val audioConfig = AudioConfig(
            mimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate = 44100,
            channelConfig = AudioFormat.CHANNEL_IN_STEREO
        )

        lifecycleScope.launch {
            streamer.setConfig(audioConfig, videoConfig)
        }
    }

    private fun toast(message: String) {
        runOnUiThread { applicationContext.toast(message) }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
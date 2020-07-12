package com.pedro.usbtest

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import com.pedro.usbtest.streamlib.RtmpUSB
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.android.synthetic.main.activity_main.*
import net.ossrs.rtmp.ConnectCheckerRtmp


class MainActivity : Activity(), SurfaceHolder.Callback, ConnectCheckerRtmp {

    override fun onAuthSuccessRtmp() {
        Log.e("BYYD", "onAuthSuccessRtmp ")
    }

    override fun onNewBitrateRtmp(bitrate: Long) {

    }

    override fun onConnectionSuccessRtmp() {
        runOnUiThread {
            Toast.makeText(this, "Success", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionFailedRtmp(reason: String) {
        runOnUiThread {
            Toast.makeText(this, "Failed $reason", Toast.LENGTH_SHORT).show()
            rtmpUSB.stopStream(uvcCamera)
        }
    }

    override fun onAuthErrorRtmp() {
        Log.e("BYYD", "onAuthErrorRtmp")
    }

    override fun onDisconnectRtmp() {
        runOnUiThread {
            Toast.makeText(this, "Disconnect", Toast.LENGTH_SHORT).show()
        }
    }

    override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
    }

    override fun surfaceDestroyed(p0: SurfaceHolder?) {

    }

    override fun surfaceCreated(p0: SurfaceHolder?) {

    }

    private lateinit var usbMonitor: USBMonitor
    private var uvcCamera: UVCCamera? = null
    private var isUsbOpen = true
    private val width = UVCCamera.DEFAULT_PREVIEW_WIDTH
    private val height = UVCCamera.DEFAULT_PREVIEW_HEIGHT
    private var defished = false
    private lateinit var rtmpUSB: RtmpUSB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var permission = ActivityCompat.checkSelfPermission(this,  Manifest.permission.RECORD_AUDIO)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), 1);
        }

        rtmpUSB = RtmpUSB(openglview, this)
        usbMonitor = USBMonitor(this, onDeviceConnectListener)
        isUsbOpen = false
        usbMonitor.register()
        start_stop.setOnClickListener {
            if (uvcCamera != null) {
                if (!rtmpUSB.isStreaming) {

                    startStream(et_url.text.toString())
                    start_stop.text = "停止推流"
                } else {
                    rtmpUSB.stopStream(uvcCamera)
                    start_stop.text = "启动推流"
                }
            }
        }

        // 地址
        et_url.setText("rtmp://192.168.0.104:1935/live/demo")
    }

    private fun startStream(url: String) {
        // TODO 码率和帧率
        if (rtmpUSB.prepareVideo(
                width, height, 15, 2000 * 1024, false, 0,
                uvcCamera
            ) && rtmpUSB.prepareAudio()
        ) {
            rtmpUSB.startStream(uvcCamera, url)
        }
    }

    private val onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice?) {
            Log.e("BYYD", "发现设备")
            usbMonitor.requestPermission(device)
        }

        override fun onConnect(
            device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?,
            createNew: Boolean
        ) {
            val camera = UVCCamera()
            camera.open(ctrlBlock)
            Log.i("BYYD", "supportedSize: " + camera.supportedSize)
            try {
                camera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG)
            } catch (e: IllegalArgumentException) {
                try {
                    camera.setPreviewSize(width, height, UVCCamera.DEFAULT_PREVIEW_MODE)
                } catch (e1: IllegalArgumentException) {
                    camera.destroy()
                    return
                }
            }

            uvcCamera = camera
            rtmpUSB.startPreview(uvcCamera, width, height)
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            Log.e("BYYD", "设备断开")
            if (uvcCamera != null) {
                uvcCamera?.close()
                uvcCamera = null
                isUsbOpen = false
            }
        }

        override fun onDettach(device: UsbDevice?) {
            Log.e("BYYD", "设备拔出")
            if (uvcCamera != null) {
                uvcCamera?.close()
                uvcCamera = null
                isUsbOpen = false
            }
        }

        override fun onCancel(device: UsbDevice?) {

        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (rtmpUSB.isStreaming && uvcCamera != null) rtmpUSB.stopStream(uvcCamera)

        if (rtmpUSB.isOnPreview && uvcCamera != null) rtmpUSB.stopPreview(uvcCamera)

        if (isUsbOpen) {
            uvcCamera?.close()
            usbMonitor.unregister()
        }
    }
}

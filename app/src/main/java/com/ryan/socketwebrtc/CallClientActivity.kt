package com.ryan.socketwebrtc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnection.IceGatheringState
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnection.SignalingState
import org.webrtc.PeerConnectionFactory
import org.webrtc.PeerConnectionFactory.InitializationOptions
import org.webrtc.RtpReceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import java.net.URI
import java.net.URISyntaxException
import java.util.LinkedList

class CallClientActivity : Activity() {
    /**
     * ---------和信令服务相关-----------
     */
    private val address = "ws://10.49.49.141"//"ws://172.20.10.5"
    private val port = 8887

    //private boolean mIsServer = false;
    //    private SignalServer mServer;
    private var mClient: SignalClient? = null

    // 打印log
    private var mLogcatView: TextView? = null

    // Opengl es
    private val mRootEglBase by lazy { EglBase.create() }

    //    // 纹理渲染
    private var mSurfaceTextureHelper: SurfaceTextureHelper? = null
    private var mVideoTrack: VideoTrack? = null
    private var mAudioTrack: AudioTrack? = null

    // 视频采集
    private var mVideoCapturer: VideoCapturer? = null

    //用于数据传输
    private var mPeerConnection: PeerConnection? = null
    private var mPeerConnectionFactory: PeerConnectionFactory? = null
    private var display: Display? = null
    private val screenMetrics = DisplayMetrics()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        display = wm.defaultDisplay

        // 用户打印信息
        mLogcatView = findViewById(R.id.LogcatView)

        findViewById<Button>(R.id.btn_cast_start).setOnClickListener {
            startProjection()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: ")
    }

    // 注意这里退出时的销毁动作
    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        doLeave()
        //        mLocalSurfaceView.release();
//        mRemoteSurfaceView.release();
        mVideoCapturer!!.dispose()
        mSurfaceTextureHelper!!.dispose()
        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()
        mPeerConnectionFactory!!.dispose()

        mClient?.close()
    }

    private var mediaProjectionManager: MediaProjectionManager? = null
    private fun startProjection() {
        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager!!.createScreenCaptureIntent()
        startActivityForResult(captureIntent, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || requestCode != 1) {
            return
        }
        data ?: return
        initRTC(data)
        startCapture()
    }

    private fun initRTC(intent: Intent) {
        //        mRootEglBase = EglBase.create()

        // 用于展示本地和远端视频
//        mLocalSurfaceView = findViewById(R.id.LocalSurfaceView);
//        mRemoteSurfaceView = findViewById(R.id.RemoteSurfaceView);
//
//        mLocalSurfaceView.init(mRootEglBase.getEglBaseContext(), null);
//        mLocalSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
//        mLocalSurfaceView.setMirror(true);
//        mLocalSurfaceView.setEnableHardwareScaler(false /* enabled */);
//        mLocalSurfaceView.setZOrderMediaOverlay(true); // 注意这句，因为2个surfaceview是叠加的
//
//        mRemoteSurfaceView.init(mRootEglBase.getEglBaseContext(), null);
//        mRemoteSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
//        mRemoteSurfaceView.setMirror(true);
//        mRemoteSurfaceView.setEnableHardwareScaler(true /* enabled */);

        // 创建PC factory , PC就是从factory里面获取的
        mPeerConnectionFactory = createPeerConnectionFactory(this)

        // NOTE: this _must_ happen while PeerConnectionFactory is alive!
        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE)

        // 创建视频采集器
        mVideoCapturer = createVideoCapturer(intent)

//        mSurfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mRootEglBase.getEglBaseContext());
//        VideoSource videoSource = mPeerConnectionFactory.createVideoSource(false);
        mSurfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", mRootEglBase.eglBaseContext)
        val videoSource = mPeerConnectionFactory!!.createVideoSource(
            mVideoCapturer!!.isScreencast
        )
        mVideoCapturer!!.initialize(
            mSurfaceTextureHelper, applicationContext, videoSource.capturerObserver
        )
        mVideoTrack = mPeerConnectionFactory!!.createVideoTrack(VIDEO_TRACK_ID, videoSource)
//        mVideoTrack?.setEnabled(true)
        //        mVideoTrack.addSink(mLocalSurfaceView); // 设置渲染到本地surfaceview上

        //AudioSource 和 AudioTrack 与VideoSource和VideoTrack相似，只是不需要AudioCapturer 来获取麦克风，
        val audioSource = mPeerConnectionFactory!!.createAudioSource(MediaConstraints())
        mAudioTrack = mPeerConnectionFactory!!.createAudioTrack(AUDIO_TRACK_ID, audioSource)
//        mAudioTrack?.setEnabled(true)
        /** ---------开始启动信令服务-----------  */
//        mIsServer = getIntent().getBooleanExtra("server", false);

//        if (mIsServer) {
//            mServer = new SignalServer(port);
//            mServer.start();
//        } else {
        try {
            mClient = SignalClient(URI("$address:$port"))
            mClient!!.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
        //        }
    }

    private fun startCapture() {
        display?.getRealMetrics(screenMetrics)
        // 开始采集并本地显示
//        mVideoCapturer?.startCapture(
//            screenMetrics.widthPixels, screenMetrics.heightPixels, VIDEO_FPS
//        )
        mVideoCapturer?.startCapture(
            720, 1280, 24
        )
    }

    private fun stopCapture() {
        try {
            // 停止采集
            mVideoCapturer?.stopCapture()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }


    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription) {
            Logger.d("SdpObserver: onCreateSuccess !")
        }

        override fun onSetSuccess() {
            Logger.d("SdpObserver: onSetSuccess")
        }

        override fun onCreateFailure(msg: String) {
            Logger.d("SdpObserver onCreateFailure: $msg")
        }

        override fun onSetFailure(msg: String) {
            Logger.d("SdpObserver onSetFailure: $msg")
        }
    }

    private fun updateCallState(idle: Boolean) {
        Log.d(TAG, "updateCallState: $idle")
        //        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                if (idle) {
//                    mRemoteSurfaceView.setVisibility(View.GONE);
//                } else {
//                    mRemoteSurfaceView.setVisibility(View.VISIBLE);
//                }
//            }
//        });
    }

//    /**
//     * 有其他用户连进来，
//     */
//    fun doStartCall(conn: WebSocket) {
//        printInfoOnScreen("Start Call, Wait ...")
//        if (mPeerConnection == null) {
//            mPeerConnection = createPeerConnection()
//        }
//        val mediaConstraints = MediaConstraints()
//        mediaConstraints.mandatory.add(
//            MediaConstraints.KeyValuePair(
//                "OfferToReceiveAudio", "true"
//            )
//        ) // 接收远端音频
//        mediaConstraints.mandatory.add(
//            MediaConstraints.KeyValuePair(
//                "OfferToReceiveVideo", "true"
//            )
//        ) // 接收远端视频
//        mediaConstraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
//        mPeerConnection!!.createOffer(object : SimpleSdpObserver() {
//            override fun onCreateSuccess(sessionDescription: SessionDescription) {
//                Logger.d("Create local offer success: ${sessionDescription.description}".trimIndent())
//                mPeerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
//                val message = JSONObject()
//                try {
//                    message.put("type", "offer")
//                    message.put("sdp", sessionDescription.description)
//                    conn.send(message.toString())
//                } catch (e: JSONException) {
//                    e.printStackTrace()
//                }
//            }
//        }, mediaConstraints)
//    }

    fun doAnswerCall() {
        printInfoOnScreen("Answer Call, Wait ...")
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection()
        }
        val sdpMediaConstraints = MediaConstraints()
        Logger.d("Create answer ...")
        mPeerConnection!!.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Logger.d("Create answer success !")
                mPeerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "answer")
                    message.put("sdp", sessionDescription.description)
                    sendMessage(message.toString())
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, sdpMediaConstraints)
        updateCallState(false)
    }

    fun doLeave() {
        printInfoOnScreen("Leave room, Wait ...")
        printInfoOnScreen("Hangup Call, Wait ...")
        if (mPeerConnection == null) {
            return
        }
        mPeerConnection!!.close()
        mPeerConnection = null
        printInfoOnScreen("Hangup Done.")
        updateCallState(true)
    }

    fun createPeerConnection(): PeerConnection? {
        Logger.d("Create PeerConnection ...")
        val iceServers = LinkedList<IceServer>()

        // 设置ICE服务器
        val ice_server = IceServer.builder("turn:xxxx:3478").setPassword("xxx").setUsername("xxx")
            .createIceServer()
        iceServers.add(ice_server)
        val rtcConfig = RTCConfiguration(iceServers)
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED // 不要使用TCP
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE // max-bundle表示音视频都绑定到同一个传输通道
        rtcConfig.rtcpMuxPolicy =
            PeerConnection.RtcpMuxPolicy.REQUIRE // 只收集RTCP和RTP复用的ICE候选者，如果RTCP不能复用，就失败
        rtcConfig.continualGatheringPolicy =
            PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        //rtcConfig.iceCandidatePoolSize = 10;
//        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL

        // Use ECDSA encryption.
        //rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        // Enable DTLS for normal calls and disable for loopback calls.
//        rtcConfig.enableDtlsSrtp = true
        //rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        val connection = mPeerConnectionFactory?.createPeerConnection(
            rtcConfig, mPeerConnectionObserver
        ) // PC的observer
        if (connection == null) {
            Logger.d("Failed to createPeerConnection !")
            return null
        }
        val mediaStreamLabels = listOf("ARDAMS")
        connection.addTrack(mVideoTrack, mediaStreamLabels)
        connection.addTrack(mAudioTrack, mediaStreamLabels)

//        val stream = mPeerConnectionFactory?.createLocalMediaStream("102")
//        stream?.addTrack(mAudioTrack)
//        stream?.addTrack(mVideoTrack)
//        connection.addStream(stream)
        return connection
    }

    private fun createPeerConnectionFactory(context: Context?): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            InitializationOptions.builder(context).createInitializationOptions()
        )

        return PeerConnectionFactory.builder().setOptions(PeerConnectionFactory.Options())
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    mRootEglBase.eglBaseContext, ENABLE_INTEL_VP8_ENCODER, ENABLE_H264_HIGH_PROFILE
                )
            ).setVideoDecoderFactory(DefaultVideoDecoderFactory(mRootEglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    /*
     * Read more about Camera2 here
     * https://developer.android.com/reference/android/hardware/camera2/package-summary.html
     **/
    private fun createVideoCapturer(intent: Intent): VideoCapturer {
//        if (Camera2Enumerator.isSupported(this)) {
//            return createCameraCapturer(new Camera2Enumerator(this));
//        } else {
//            return createCameraCapturer(new Camera1Enumerator(true));
//        }
        return ScreenCapturerAndroid(intent, object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.e(TAG, "User has revoked media projection permissions")
            }
        })
    }

    //    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
    //        final String[] deviceNames = enumerator.getDeviceNames();
    //
    //        // First, try to find front facing camera
    //        Logger.d("Looking for front facing cameras.");
    //        for (String deviceName : deviceNames) {
    //            if (enumerator.isBackFacing(deviceName)) {
    //                Logger.d("Creating front facing camera capturer.");
    //                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
    //                if (videoCapturer != null) {
    //                    return videoCapturer;
    //                }
    //            }
    //        }
    //
    //        // Front facing camera not found, try something else
    //        Logger.d("Looking for other cameras.");
    //        for (String deviceName : deviceNames) {
    //            if (!enumerator.isBackFacing(deviceName)) {
    //                Logger.d("Creating other camera capturer.");
    //                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
    //                if (videoCapturer != null) {
    //                    return videoCapturer;
    //                }
    //            }
    //        }
    //        return null;
    //    }
    private val mPeerConnectionObserver: PeerConnection.Observer =
        object : PeerConnection.Observer {
            override fun onSignalingChange(signalingState: SignalingState) {
                Logger.d("onSignalingChange: $signalingState")
            }

            override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                Logger.d("onIceConnectionChange: $iceConnectionState")
            }

            override fun onIceConnectionReceivingChange(b: Boolean) {
                Logger.d("onIceConnectionChange: $b")
            }

            override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                Logger.d("onIceGatheringChange: $iceGatheringState")
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Logger.d("onIceCandidate: $iceCandidate")
                // 得到candidate，就发送给信令服务器
                try {
                    val message = JSONObject()
                    //message.put("userId", RTCWebRTCSignalClient.getInstance().getUserId());
                    message.put("type", "candidate")
                    message.put("label", iceCandidate.sdpMLineIndex)
                    message.put("id", iceCandidate.sdpMid)
                    message.put("candidate", iceCandidate.sdp)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                for (i in iceCandidates.indices) {
                    Logger.d("onIceCandidatesRemoved: " + iceCandidates[i])
                }
                mPeerConnection!!.removeIceCandidates(iceCandidates)
            }

            override fun onAddStream(mediaStream: MediaStream) {
                Logger.d("onAddStream: " + mediaStream.videoTracks.size)
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Logger.d("onRemoveStream")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Logger.d("onDataChannel")
            }

            override fun onRenegotiationNeeded() {
                Logger.d("onRenegotiationNeeded")
            }

            // 收到了媒体流
            override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
//            MediaStreamTrack track = rtpReceiver.track();
//            if (track instanceof VideoTrack) {
//                Logger.d("onAddVideoTrack");
//                VideoTrack remoteVideoTrack = (VideoTrack) track;
//                remoteVideoTrack.setEnabled(true);
//                remoteVideoTrack.addSink(mRemoteSurfaceView);
//            }
            }
        }

    private fun sendMessage(message: JSONObject) {
//        if (mIsServer) {
//            mServer.broadcast(message.toString());
//        } else {
        mClient!!.send(message.toString())
        //        }
    }

    private fun sendMessage(message: String) {
//        if (mIsServer) {
//            mServer.broadcast(message);
//        } else {
        mClient!!.send(message)
        //        }
    }

//    internal inner class SignalServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {
//        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
//            Logger.d("=== SignalServer onOpen()")
//            printInfoOnScreen("onOpen有客户端连接上...调用start call")
//            //调用call， 进行媒体协商
//            doStartCall(conn)
//        }
//
//        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
//            Logger.d("=== SignalServer onClose() reason=$reason, remote=$remote")
//            printInfoOnScreen("onClose客户端断开...调用doLeave，reason=$reason")
//            doLeave()
//        }
//
//        override fun onMessage(conn: WebSocket, message: String) {
//            Logger.d("=== SignalServer onMessage() message=$message")
//            try {
//                val jsonMessage = JSONObject(message)
//                val type = jsonMessage.getString("type")
//                if (type == "offer") {
//                    onRemoteOfferReceived(jsonMessage)
//                } else if (type == "answer") {
//                    onRemoteAnswerReceived(jsonMessage)
//                } else if (type == "candidate") {
//                    onRemoteCandidateReceived(jsonMessage)
//                } else {
//                    Logger.e("the type is invalid: $type")
//                }
//            } catch (e: JSONException) {
//                e.printStackTrace()
//            }
//        }
//
//        override fun onError(conn: WebSocket, ex: Exception) {
//            ex.printStackTrace()
//            Logger.e("=== SignalServer onMessage() ex=" + ex.message)
//        }
//
//        override fun onStart() {
//            Logger.d("=== SignalServer onStart()")
//            connectionLostTimeout = 0
//            connectionLostTimeout = 100
//            printInfoOnScreen("onStart服务端建立成功...创建PC")
//            //这里应该创建PeerConnection
//            if (mPeerConnection == null) {
//                mPeerConnection = createPeerConnection()
//            }
//        }
//    }

    internal inner class SignalClient(serverUri: URI?) : WebSocketClient(serverUri) {
        override fun onOpen(handshakedata: ServerHandshake) {
            Logger.d("=== SignalClient onOpen()")
            printInfoOnScreen("连接服务端成功...创建PC")
            //这里应该创建PeerConnection
            if (mPeerConnection == null) {
                mPeerConnection = createPeerConnection()
            }
        }

        override fun onMessage(message: String) {
            Logger.d("=== SignalClient onMessage(): message=$message")
            try {
                val jsonMessage = JSONObject(message)
                val type = jsonMessage.getString("type")
                if (type == "offer") {
                    onRemoteOfferReceived(jsonMessage)
                } else if (type == "answer") {
                    onRemoteAnswerReceived(jsonMessage)
                } else if (type == "candidate") {
                    onRemoteCandidateReceived(jsonMessage)
                } else {
                    Logger.e("the type is invalid: $type")
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            Logger.d("=== SignalClient onClose(): reason=$reason, remote=$remote")
            printInfoOnScreen("和服务端断开...调用doLeave")
            doLeave()
        }

        override fun onError(ex: Exception) {
            ex.printStackTrace()
            Logger.d("=== SignalClient onMessage() ex=" + ex.message)
        }
    }

    // 接听方，收到offer
    private fun onRemoteOfferReceived(message: JSONObject) {
        printInfoOnScreen("Receive Remote Call ...")
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection()
        }
        try {
            val description = message.getString("sdp")
            mPeerConnection!!.setRemoteDescription(
                SimpleSdpObserver(), SessionDescription(SessionDescription.Type.OFFER, description)
            )
            printInfoOnScreen("收到offer...调用doAnswerCall")
            doAnswerCall()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    // 发送方，收到answer
    private fun onRemoteAnswerReceived(message: JSONObject) {
        printInfoOnScreen("Receive Remote Answer ...")
        try {
            val description = message.getString("sdp")
            mPeerConnection!!.setRemoteDescription(
                SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, description)
            )
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        printInfoOnScreen("收到answer.....")
        updateCallState(false)
    }

    // 收到对端发过来的candidate
    private fun onRemoteCandidateReceived(message: JSONObject) {
        printInfoOnScreen("Receive Remote Candidate ...")
        try {
            // candidate 候选者描述信息
            // sdpMid 与候选者相关的媒体流的识别标签
            // sdpMLineIndex 在SDP中m=的索引值
            // usernameFragment 包括了远端的唯一识别
            val remoteIceCandidate = IceCandidate(
                message.getString("id"), message.getInt("label"), message.getString("candidate")
            )
            printInfoOnScreen("收到Candidate.....")
            mPeerConnection!!.addIceCandidate(remoteIceCandidate)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun onRemoteHangup() {
        printInfoOnScreen("Receive Remote Hangup Event ...")
        doLeave()
    }

    private fun printInfoOnScreen(msg: String) {
        Logger.d(msg)
        runOnUiThread {
            val output = """
                ${mLogcatView!!.text}
                $msg
                """.trimIndent()
            mLogcatView!!.text = output
        }
    }

    companion object {
        private const val TAG = "CallClientActivity"

        /**
         * ---------和webrtc相关-----------
         */
        // 视频信息
        private const val ENABLE_INTEL_VP8_ENCODER = true
        private const val ENABLE_H264_HIGH_PROFILE = true
        private const val VIDEO_RESOLUTION_WIDTH = 1280
        private const val VIDEO_RESOLUTION_HEIGHT = 720
        private const val VIDEO_FPS = 30

        //    private SurfaceViewRenderer mLocalSurfaceView;
        //    private SurfaceViewRenderer mRemoteSurfaceView;
        // 音视频数据
        const val VIDEO_TRACK_ID = "1" //"ARDAMSv0";
        const val AUDIO_TRACK_ID = "2" //"ARDAMSa0";
    }
}
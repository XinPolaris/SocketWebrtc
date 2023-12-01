package com.ryan.socketwebrtc

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.ryan.socketwebrtc.databinding.ActivityServerBinding
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.FiksVideoEncoderFactory
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
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.io.IOException
import java.net.InetSocketAddress
import java.util.LinkedList
import kotlin.system.exitProcess

class CallServerActivity : Activity() {
    private lateinit var binding: ActivityServerBinding

    /**
     * ---------和信令服务相关-----------
     */
    private val port = 8887

    private var mServer: SignalServer? = null

    /**
     * ---------和webrtc相关-----------
     */
    // 打印log
    private var mLogcatView: TextView? = null

    // Opengl es
    private val mRootEglBase by lazy { EglBase.create() }

    private lateinit var mRemoteSurfaceView: SurfaceViewRenderer

    //用于数据传输
    private var mPeerConnection: PeerConnection? = null
    private var mPeerConnectionFactory: PeerConnectionFactory? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        binding.btnBack.setOnClickListener {
            exitProcess(0)
        }
        // 用户打印信息
        mLogcatView = binding.LogcatView

        // 用于展示本地和远端视频
        mRemoteSurfaceView = binding.RemoteSurfaceView

        mRemoteSurfaceView.init(mRootEglBase.eglBaseContext, null)
        mRemoteSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_BALANCED)

        // 创建PC factory , PC就是从factory里面获取的
        mPeerConnectionFactory = createPeerConnectionFactory(this)

        // NOTE: this _must_ happen while PeerConnectionFactory is alive!
        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE)

        /** ---------开始启动信令服务-----------  */
        mServer = SignalServer(port)
        mServer!!.start()
    }

    // 注意这里退出时的销毁动作
    override fun onDestroy() {
        super.onDestroy()
        doLeave()
        mRemoteSurfaceView.release()

        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()
        mPeerConnectionFactory!!.dispose()
        try {
            mServer!!.stop()
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
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
        runOnUiThread {
            if (idle) {
                mRemoteSurfaceView.visibility = View.GONE
            } else {
                mRemoteSurfaceView.visibility = View.VISIBLE
            }
        }
    }

    /**
     * 有其他用户连进来，
     */
    fun doStartCall(conn: WebSocket?) {
        printInfoOnScreen("Start Call, Wait ...")
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection()
        }
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveAudio", "true"
            )
        ) // 接收远端音频
        mediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", "true"
            )
        ) // 接收远端视频
        mediaConstraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        mPeerConnection!!.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Logger.d(
                    """
    Create local offer success: 
    ${sessionDescription.description}
    """.trimIndent()
                )
                mPeerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "offer")
                    message.put("sdp", sessionDescription.description)
                    conn?.send(message.toString())
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, mediaConstraints)
    }

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
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL

        // Use ECDSA encryption.
        //rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        // Enable DTLS for normal calls and disable for loopback calls.
        rtcConfig.enableDtlsSrtp = true
        //rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        val connection = mPeerConnectionFactory!!.createPeerConnection(
            rtcConfig, mPeerConnectionObserver
        ) // PC的observer
        if (connection == null) {
            Logger.d("Failed to createPeerConnection !")
            return null
        }
        return connection
    }

    private fun createPeerConnectionFactory(context: Context?): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )

        return PeerConnectionFactory.builder().setOptions(PeerConnectionFactory.Options())
            .setVideoEncoderFactory(
                FiksVideoEncoderFactory(
                    mRootEglBase.eglBaseContext,
                    ENABLE_INTEL_VP8_ENCODER,
                    ENABLE_H264_HIGH_PROFILE
                )
            ).setVideoDecoderFactory(DefaultVideoDecoderFactory(mRootEglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

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
                val track = rtpReceiver.track()
                if (track is VideoTrack) {
                    Logger.d("onAddVideoTrack")
                    val remoteVideoTrack = track
                    remoteVideoTrack.setEnabled(true)
                    remoteVideoTrack.addSink(mRemoteSurfaceView)
                }
            }
        }

    private fun sendMessage(message: JSONObject) {
        mServer!!.broadcast(message.toString())
    }

    private fun sendMessage(message: String) {
        mServer!!.broadcast(message)
    }

    internal inner class SignalServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {
        override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
            Logger.d("=== SignalServer onOpen()")
            printInfoOnScreen("onOpen有客户端连接上...调用start call")
            //调用call， 进行媒体协商
            doStartCall(conn)
        }

        override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
            Logger.d("=== SignalServer onClose() reason=$reason, remote=$remote")
            printInfoOnScreen("onClose客户端断开...调用doLeave，reason=$reason")
            doLeave()
        }

        override fun onMessage(conn: WebSocket?, message: String?) {
            Logger.d("=== SignalServer onMessage() message=$message")
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

        override fun onError(conn: WebSocket?, ex: Exception?) {
            ex?.printStackTrace()
            Logger.e("=== SignalServer onMessage() ex=" + ex?.message)
        }

        override fun onStart() {
            Logger.d("=== SignalServer onStart()")
            connectionLostTimeout = 0
            connectionLostTimeout = 100
            printInfoOnScreen("onStart服务端建立成功...创建PC")
            //这里应该创建PeerConnection
            if (mPeerConnection == null) {
                mPeerConnection = createPeerConnection()
            }
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
        private const val ENABLE_INTEL_VP8_ENCODER = false
        private const val ENABLE_H264_HIGH_PROFILE = true
    }
}
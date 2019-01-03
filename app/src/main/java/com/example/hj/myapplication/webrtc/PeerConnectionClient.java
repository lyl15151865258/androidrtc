package com.example.hj.myapplication.webrtc;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.example.hj.myapplication.Bean.RoomConnectionParameters;
import com.example.hj.myapplication.MainActivity;
import com.example.hj.myapplication.MyApplication;
import com.example.hj.myapplication.constants.Constant;

import org.webrtc.AudioTrack;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.voiceengine.WebRtcAudioManager;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by hj on 2017/4/20.
 */
public class PeerConnectionClient {
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
    private static final String TAG = "PCRTCClient";
    private PeerConnectionEvents events;
    private static final PeerConnectionClient instance = new PeerConnectionClient();
    private PeerConnectionFactory.Options options = null;
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private SessionDescription localSdp; // either offer or answer SDP
    private MediaStream mediaStream;
    private VideoCapturerAndroid videoCapturer;
    // 如果要呈现和发送视频，则renderVideo设置为true。
    private boolean renderVideo;
    private VideoTrack localVideoTrack;
    private VideoTrack remoteVideoTrack;
    private AudioTrack localAudioTrack;
    // 如果应该发送音频，则enableAudio设置为true。
    private boolean enableAudio;
    private boolean isError;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private MediaConstraints pcConstraints;
    private MediaConstraints videoConstraints;
    private MediaConstraints audioConstraints;
    private MediaConstraints sdpMediaConstraints;
    private int numberOfCameras;
    private VideoSource videoSource;
    private boolean isInitiator;
    private boolean videoSourceStopped;
    private MainActivity mainActivity;
    private boolean videoCallEnabled;
    private String videoCodec = "VP8";
    private String audioCodec = "OPUS";
    private boolean useOpenSLES = true;
    private static final String AUDIO_CODEC_ISAC = "ISAC";
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String VIDEO_CODEC_H264 = "H264";
    private static final String AUDIO_CODEC_OPUS = "opus";
    private static final String VIDEO_CODEC_VP8 = "VP8";
    private boolean preferIsac;
    private int videoStartBitrate = 0;
    private int audioStartBitrate = 0;
    private boolean noAudioProcessing = false;
    // 包裹文件描述符
    private ParcelFileDescriptor aecDumpFileDescriptor;
    private boolean aecDump = false;
    private static final String VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate";
    private static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";
    private String preferredVideoCodec = VIDEO_CODEC_VP9;
    private final static List<PeerConnection.IceServer> iceServers = Constant.iceServers;
    //根据服务端的指令来选择渲染远程mediastream
    private RoomConnectionParameters roomConnectionParameters;
    private MediaStream audioMediaStream;

    /**
     * 获取PeerConnectionClient的实例
     *
     * @return
     */
    public static PeerConnectionClient getInstance() {
        return instance;
    }

    /**
     * 设置PeerConnectionFactory的选项
     *
     * @param options
     */
    public void setPeerConnectionFactoryOptions(PeerConnectionFactory.Options options) {
        this.options = options;
    }

    // 创建对等连接工厂 将变量重置为初始状态
    public void createPeerConnectionFactory(
            RoomConnectionParameters roomConnectionParameters,
            final Context context,
            final PeerConnectionEvents events) {
        mainActivity = (MainActivity) context;
        this.roomConnectionParameters = roomConnectionParameters;
        this.events = events;
        videoCallEnabled = true;
        // 将变量重置为初始状态。
        factory = null;
        peerConnection = null;
        isError = false;
        videoSourceStopped = false;
        mediaStream = null;
        videoCapturer = null;
        renderVideo = true;
        localVideoTrack = null;
        remoteVideoTrack = null;
        enableAudio = true;
        localAudioTrack = null;
        videoStartBitrate = Integer.parseInt(String.valueOf(1000));
        audioStartBitrate = Integer.parseInt(String.valueOf(32));
        // 初始化现场试验
        PeerConnectionFactory.initializeFieldTrials("");
        isError = false;
        // 检查首选视频编解码器
        preferredVideoCodec = VIDEO_CODEC_VP8;
        if (videoCallEnabled && videoCodec != null) {
            if (videoCodec.equals(VIDEO_CODEC_VP9)) {
                preferredVideoCodec = VIDEO_CODEC_VP9;
            } else if (videoCodec.equals(VIDEO_CODEC_H264)) {
                preferredVideoCodec = VIDEO_CODEC_H264;
            }
        }
        // 检查是否在默认情况下使用ISAC
        preferIsac = false;
        if (audioCodec != null
                && audioCodec.equals(AUDIO_CODEC_ISAC)) {
            preferIsac = true;
        }
        // 启用/禁用OpenSL ES播放
        if (!useOpenSLES) {
            // 即使设备支持，也禁用OpenSL ES音频
            Log.d(TAG, "Disable OpenSL ES audio even if device supports it");
            WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
        } else {
            // 如果设备支持，则允许OpenSL ES音频
            Log.d(TAG, "Allow OpenSL ES audio if device supports it");
            WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(false);
        }
        // ---------1.首先需要初始化PeerConnectionFactory -------------//
        if (!PeerConnectionFactory.initializeAndroidGlobals(context, true, true,
                true)) {
            events.onPeerConnectionError("Failed to initializeAndroidGlobals");
        }
        if (options != null) {
            // 工厂网络忽略掩码选项
            Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
        }
        Log.d(TAG, "Pereferred video codec: " + preferredVideoCodec);
        // ---------2.获得PeerConnectionFactory对象 -------------//
        factory = new PeerConnectionFactory(); //options
        Log.d(TAG, "Peer connection factory created.");
    }

    /**
     * 创建对点连接通道的实例
     *
     * @param renderEGLContext 视频渲染器上下文
     * @param localRender      本地渲染
     * @param remoteRender     远程渲染
     * @return
     */
    public PeerConnection createPeerConnection(
            final EglBase.Context renderEGLContext,
            VideoRenderer.Callbacks localRender,
            VideoRenderer.Callbacks remoteRender,
            String ids) {
        //localSdp = null;
        this.localRender = localRender;
        this.remoteRender = remoteRender;
        if (!sdps.containsKey(ids)) {
            SDPObserver sdpObserver = new SDPObserver(ids);
            sdps.put(ids, sdpObserver);
        }
        //创建对点连接约束
        createMediaConstraintsInternal();
        // 创建对等连接内部
        return createPeerConnectionInternal(renderEGLContext, ids);
    }

    public HashMap<String, SDPObserver> sdps = new HashMap<>();
    public HashMap<String, PCObserver> pcs = new HashMap<>();

    /**
     * 创建对等连接约束实例
     */
    public void createMediaConstraintsInternal() {
        // 创建对等连接约束
        pcConstraints = new MediaConstraints();
        // 检查设备上是否有相机，如果没有，则禁用视频通话
        // 通过CameraEnumerationAndroid类获取摄像头设备基本信息
        numberOfCameras = CameraEnumerationAndroid.getDeviceCount();
        if (numberOfCameras == 0) {
            Log.w(TAG, "No camera on device. Switch to audio only call.");
        }
        // 创建视频约束
        videoConstraints = new MediaConstraints();
        // 创建音频约束
        audioConstraints = new MediaConstraints();

        if (noAudioProcessing) {
            // 禁用音频处理
            Log.d(TAG, "Disabling audio processing");
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                    AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                    AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                    AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                    AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false"));
        }
        // 创建SDP约束
        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveAudio", "true"));
        if (videoCallEnabled) {
            sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                    "OfferToReceiveVideo", "true"));
        } else {
            sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                    "OfferToReceiveVideo", "false"));
        }
    }

    private EglBase.Context renderEGLContext;

    /**
     * 创建对等连接内部实例 媒体流的获取
     *
     * @param renderEGLContext 渲染器上下文
     * @param clientId         客户端ID
     */
    private PeerConnection createPeerConnectionInternal(EglBase.Context renderEGLContext, String clientId) {
        this.renderEGLContext = renderEGLContext;
        if (factory == null || isError) {
            Log.e(TAG, "Peerconnection factory is not created");
            return null;
        }
        Log.d(TAG, "Create peer connection.");
        if (videoConstraints != null) {
            Log.d(TAG, "VideoConstraints: " + videoConstraints.toString());
        }

        if (videoCallEnabled) {
            Log.d(TAG, "EGLContext: " + renderEGLContext);
            // 设置视频Hw加速选项
            factory.setVideoHwAccelerationOptions(renderEGLContext, renderEGLContext);
        }
        pcConstraints = new MediaConstraints();
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("RtpDataChannels", "false"));
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        // TCP候选者仅在连接到支持ICE-TCP的服务器时才有用
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // 使用ECDSA加密。
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        // --------------PeerConnection实例 ------------
        if (!pcs.containsKey(clientId)) {
            PCObserver pcObserver = new PCObserver(clientId);
            Log.d(TAG, "pcObserver---" + pcObserver);
            pcs.put(clientId, pcObserver);
        }
        Log.d(TAG, "pcObserver---" + pcs.get(clientId));
        peerConnection = factory.createPeerConnection(
                rtcConfig, pcConstraints, pcs.get(clientId));
        // 是否是发起者
        isInitiator = false;
        // ---------- 创建了peerConnection完成 --------------
        Log.d(TAG, "Peer connection created.");
        return peerConnection;
    }


    /**
     * 初始化摄像流信息
     */
    public void initCamreInfo() {
        // 获取媒体流
        mediaStream = factory.createLocalMediaStream("ARDAMS");
        // 获取摄像头名称
        String cameraDeviceName = CameraEnumerationAndroid.getDeviceName(0);
        // 获取前置摄像头
        String frontCameraDeviceName = CameraEnumerationAndroid.getNameOfFrontFacingDevice();
        if (numberOfCameras > 1 && frontCameraDeviceName != null) {
            cameraDeviceName = frontCameraDeviceName;
        }
        //创建包含摄像流信息的VideoCapturerAndroid实例
        videoCapturer = VideoCapturerAndroid.create(cameraDeviceName, null, renderEGLContext);

        Log.d(TAG, "Opening camera: " + cameraDeviceName);
        //mainActivity.startRecord();
        if (videoCapturer == null) {
            reportError("Failed to open camera");
            return;
        }
    }

    /**
     * 添加本地媒体流
     *
     * @param clientID 客户端ID
     */
    public void isAddStream(String clientID) {

        initCamreInfo();
        //添加视频媒体流
        mediaStream.addTrack(createVideoTrack(videoCapturer));
        // 添加音频媒体流
        mediaStream.addTrack(createAudioTrack());
        // mediaStream添加到PeerConnection
        MyApplication.peers.get(clientID).addStream(mediaStream);
        Log.d(TAG, "添加流成功------");
    }

    /**
     * 添加本地音频流
     *
     * @param clientID 客户端ID
     */
    public void isAddAudio(String clientID) {
        Log.d(TAG, "isAddStream------" + CameraEnumerationAndroid.getDeviceCount());
        // 获取媒体流
        audioMediaStream = factory.createLocalMediaStream("ARDAMS");
        // 获取摄像头名称
        String cameraDeviceName = CameraEnumerationAndroid.getDeviceName(0);
        // 获取前置摄像头
        String frontCameraDeviceName = CameraEnumerationAndroid.getNameOfFrontFacingDevice();
        if (numberOfCameras > 1 && frontCameraDeviceName != null) {
            cameraDeviceName = frontCameraDeviceName;
        }
        //创建包含摄像流信息的VideoCapturerAndroid实例
        videoCapturer = VideoCapturerAndroid.create(cameraDeviceName, null, renderEGLContext);

        Log.d(TAG, "Opening camera: " + cameraDeviceName);
        //mainActivity.startRecord();
        if (videoCapturer == null) {
            reportError("Failed to open camera");
            return;
        }
        //添加视频媒体流
        //mediaStream.addTrack(createVideoTrack(videoCapturer));
        // 添加音频媒体流
        audioMediaStream.addTrack(createAudioTrack());
        // mediaStream添加到PeerConnection
        MyApplication.peers.get(clientID).addStream(audioMediaStream);
        Log.d(TAG, "添加流成功------");
    }

    /**
     * 渲染本地流
     */
    public void renderLocal() {
        Log.d("renderLocal", "renderLocal");
        // 获取摄像头名称
        String cameraDeviceName = CameraEnumerationAndroid.getDeviceName(0);
        // 获取前置摄像头
        String frontCameraDeviceName =
                CameraEnumerationAndroid.getNameOfFrontFacingDevice();
        if (numberOfCameras > 1 && frontCameraDeviceName != null) {
            cameraDeviceName = frontCameraDeviceName;
        }
        Log.d(TAG, "Opening camera: " + cameraDeviceName);
        // videoCapturer 包含摄像流信息的VideoCapturerAndroid实例
        videoCapturer = VideoCapturerAndroid.create(cameraDeviceName, null, renderEGLContext);
        if (videoCapturer == null) {
            return;
        }
        createVideoTrack(videoCapturer);
        createAudioTrack();
    }

    /**
     * 获取音频轨道
     * AudioTrack是简单的添加VideoSource到MediaStream对象的一个封装。
     *
     * @return AudioTrack
     */
    private AudioTrack createAudioTrack() {
        localAudioTrack = factory.createAudioTrack(
                AUDIO_TRACK_ID,
                factory.createAudioSource(audioConstraints));
        localAudioTrack.setEnabled(enableAudio);
        return localAudioTrack;
    }

    /**
     * 创建视频轨道
     *
     * @param capturer 包含摄像流信息的VideoCapturerAndroid实例
     * @return
     */
    private VideoTrack createVideoTrack(VideoCapturer capturer) {
        //获取视频资源
        videoSource = factory.createVideoSource(capturer, videoConstraints);
        //一旦我们这样做，我们可以创建我们的VideoTrack
        //注意，VIDEO_TRACK_ID可以是任何唯一的
        //识别您的应用
        //获取视频轨道
        localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(renderVideo);
        if (roomConnectionParameters.adminid.equals(roomConnectionParameters.userid)) {
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainActivity.showViewRender("1");
                }
            });
            localVideoTrack.addRenderer(new VideoRenderer(remoteRender));
        } else {
            Log.e("showViewRender----", "localRender+2");
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainActivity.showViewRender("2");
                }
            });
            localVideoTrack.addRenderer(new VideoRenderer(localRender));
        }

        return localVideoTrack;
    }

    /**
     * 关闭对点连接
     */
    public void closeP2P() {
        // 关闭对点连接通道
        Log.d(TAG, "Closing peer connection.");
        if (MyApplication.peers.size() > 0) {
            for (int i = 0; i < MyApplication.peers.size(); i++) {
                PeerConnection peerConnection = MyApplication.peers.get(i);
                if (peerConnection != null) {
                    // 处理
                    peerConnection.dispose();
                    peerConnection = null;
                }
            }
        }
        // 关闭视频资源
        Log.d(TAG, "Closing video source.");
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        // 关闭对点连接工厂
        Log.d(TAG, "Closing peer connection factory.");
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        options = null;
        Log.d(TAG, "Closing peer connection done.");
        events.onPeerConnectionClosed();
    }

    /**
     * 指令类型
     */
    public String eventType = "";

    /**
     * 创建呼叫，发起者caller
     */
    public void createOffer(String eventType, String cliend_ID) {
        Log.d(TAG, "createOffer---" + eventType + "---" + cliend_ID);
        this.eventType = eventType;
        if ("_offer_agree_video".equals(eventType)) {
            setRenderCount(renderCount + 1);
        }
        if ("_offer_agree_video_private".equals(eventType)) {
            setRenderCount(renderCount + 1);
        }
        if (MyApplication.peers.get(cliend_ID) != null && !isError) {
            Log.d(TAG, "PC Create OFFER");
            // 是否是发起者
            isInitiator = true;
            peerConnection.createOffer(sdps.get(cliend_ID), sdpMediaConstraints);
        }
    }

    /**
     * 创建应答，接收者 callee
     */
    public void createAnswer(String target_ID) {
        isInitiator = false;
        Log.d(TAG, "createAnswer ---success");
        if (MyApplication.peers.get(target_ID) != null && !isError) {
            Log.d(TAG, "PC create ANSWER");
            isInitiator = false;
            MyApplication.peers.get(target_ID).createAnswer(sdps.get(target_ID), sdpMediaConstraints);
        }
    }

    /**
     * 添加远程IceCandidate
     *
     * @param candidate 远程icecandidate
     * @param sender_ID 发送者的客户端ID
     */
    public void addRemoteIceCandidate(final IceCandidate candidate, String sender_ID) {
        MyApplication.peers.get(sender_ID).addIceCandidate(candidate);//没有的话，对方视频无法渲染
        Log.d("addRemoteIceCandidate", "添加" + sender_ID + "发来的候选信息,连接状态为：" + MyApplication.peers.get(sender_ID).iceConnectionState());
    }

    /**
     * 移除远程IceCandidate
     *
     * @param candidates
     */
    public void removeRemoteIceCandidates(final IceCandidate[] candidates) {
        if (peerConnection == null || isError) {
            return;
        }
        //如果有任何问题，排队的远程候选人排出，以正确的顺序处理。
        //peerConnection.removeIceCandidates(candidates);

    }

    /**
     * 报告错误
     *
     * @param errorMessage
     */
    private void reportError(final String errorMessage) {
        Log.e(TAG, "Peerconnection error: " + errorMessage);
        if (!isError) {
            events.onPeerConnectionError(errorMessage);
            isError = true;
        }
    }

    /**
     * 设置音频启用
     *
     * @param enable
     */
    public void setAudioEnabled(final boolean enable) {
        enableAudio = enable;
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(enableAudio);
        }
    }

    /**
     * 设置视频启用
     *
     * @param enable
     */
    public void setVideoEnabled(final boolean enable) {
        renderVideo = enable;
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(renderVideo);
        }
        if (remoteVideoTrack != null) {
            remoteVideoTrack.setEnabled(renderVideo);
        }
    }

    /**
     * 设置开始比特率
     *
     * @param codec
     * @param isVideoCodec
     * @param sdpDescription
     * @param bitrateKbps
     * @return
     */
    private static String setStartBitrate(String codec, boolean isVideoCodec, String sdpDescription, int bitrateKbps) {
        String[] lines = sdpDescription.split("\r\n");
        // rtp map行索引
        int rtpmapLineIndex = -1;
        // sdp格式已更新
        boolean sdpFormatUpdated = false;
        // 编解码器Rtp Map
        String codecRtpMap = null;
        // 以格式搜索编解码器rtpmap
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                rtpmapLineIndex = i;
                break;
            }
        }
        if (codecRtpMap == null) {
            Log.w(TAG, "No rtpmap for " + codec + " codec");
            return sdpDescription;
        }
        Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex]);
        // 检查此编解码器的远程SDP中是否存在a = fmtp字符串，并使用新的比特率参数进行更新。
        regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$";
        codecPattern = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                Log.d(TAG, "Found " + codec + " " + lines[i]);
                if (isVideoCodec) {
                    // 视频编解码器参数开始双击
                    lines[i] += "; " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    // 双击音频编解码器参数
                    lines[i] += "; " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
                }
                Log.d(TAG, "Update remote SDP line: " + lines[i]);
                sdpFormatUpdated = true;
                break;
            }
        }

        StringBuilder newSdpDescription = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            newSdpDescription.append(lines[i]).append("\r\n");
            // Append new a=fmtp line if no such line exist for a codec.
            // 如果编解码器不存在这样的行，则附加新的a = fmtp行。
            if (!sdpFormatUpdated && i == rtpmapLineIndex) {
                String bitrateSet;
                if (isVideoCodec) {
                    bitrateSet = "a=fmtp:" + codecRtpMap + " " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps;
                } else {
                    bitrateSet = "a=fmtp:" + codecRtpMap + " " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
                }
                Log.d(TAG, "Add remote SDP line: " + bitrateSet);
                newSdpDescription.append(bitrateSet).append("\r\n");
            }
        }
        return newSdpDescription.toString();
    }

    public void sendCandidates(String target_ID) {
        List<IceCandidate> iIceCandidates = candidateMaps.get(target_ID);
        for (int i = 0; i < iIceCandidates.size(); i++) {
            events.onIceCandidate(iIceCandidates.get(i), target_ID);
        }
    }

    /**
     * 设置远程描述
     *
     * @param sdp
     */
    public void setRemoteDescription(final SessionDescription sdp, String target_ID) {
        Log.d(TAG, "开始设置远程SDP====");
        if (MyApplication.peers.get(target_ID) == null || isError) {
            Log.d("hj----", "peerConnection is null");
            return;
        }
        String sdpDescription = sdp.description;
        if (preferIsac) {
            sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
        }
        if (videoCallEnabled) {
            sdpDescription = preferCodec(sdpDescription, preferredVideoCodec, false);
        }
        if (videoCallEnabled && videoStartBitrate > 0) {
            sdpDescription = setStartBitrate(VIDEO_CODEC_VP8, true,
                    sdpDescription, videoStartBitrate);
            sdpDescription = setStartBitrate(VIDEO_CODEC_VP9, true,
                    sdpDescription, videoStartBitrate);
            sdpDescription = setStartBitrate(VIDEO_CODEC_H264, true,
                    sdpDescription, videoStartBitrate);
        }
        // 音频开始比特率
        if (audioStartBitrate > 0) {
            sdpDescription = setStartBitrate(AUDIO_CODEC_OPUS, false, sdpDescription, audioStartBitrate);
        }
        Log.d(TAG, "Set remote SDP.");
        SessionDescription sdpRemote = new SessionDescription(sdp.type, sdpDescription);
        MyApplication.peers.get(target_ID).setRemoteDescription(sdps.get(target_ID), sdpRemote);
    }

    /**
     * 开始视频资源
     * VideoSource允许方法开启、停止设备捕获视频。
     */
    public void startVideoSource() {
        if (videoSource != null && videoSourceStopped) {
            Log.d(TAG, "Restart video source.");
            videoSource.restart();
            videoSourceStopped = false;
        }
    }

    /**
     * 终止视频资源
     */
    public void stopVideoSource() {
        if (videoSource != null && !videoSourceStopped) {
            Log.d(TAG, "Stop video source.");
            videoSource.stop();
            videoSourceStopped = true;
        }

    }

    private List<IceCandidate> candidates;

    /**
     * 创建IceCandidate集合，用于存储可用的IceCandidate
     *
     * @param clientID 客户端ID
     */
    public void setIceCandidates(String clientID) {
        Log.d(TAG, "开始设置IceCandidates----");
        if (candidateMaps.size() > 0) {
            candidates = candidateMaps.get(clientID);
        }
        if (candidates != null) { //对象已存在
            // 创建候选信息集合
            candidates.removeAll(candidates);
            candidates = new ArrayList<IceCandidate>();
            candidateMaps.put(clientID, candidates);
        } else { //对象不存在
            // 创建候选信息集合
            candidates = new ArrayList<IceCandidate>();
            candidateMaps.put(clientID, candidates);
        }
    }

    /**
     * 房间里的已开始视频数量
     */
    private int renderCount = 0;

    public int getRenderCount() {
        return renderCount;
    }

    public void setRenderCount(int renderCount) {
        Log.d(TAG, "更新渲染位置" + renderCount);
        this.renderCount = renderCount;
    }

    public void addStream(String target_ID) {
        if (!roomConnectionParameters.userid.equals(roomConnectionParameters.adminid)) {

            mainActivity.showViewRender("1");
        }
        MyApplication.peers.get(target_ID).addStream(mediaStream);
    }

    public SessionDescription sessionDescription;
    public String target_ID;
    public List<String> peers = new ArrayList<>();

    public void setTestM(SessionDescription sessionDescription, String target_ID) {
        this.sessionDescription = sessionDescription;
        this.target_ID = target_ID;
    }

    public void setTestM(SessionDescription sessionDescription, String target_ID, List<String> peers) {
        Log.d(TAG, "peers------" + peers);
        this.sessionDescription = sessionDescription;
        this.target_ID = target_ID;
        this.peers = peers;
        Log.d("peers", peers.toString());
    }

    // 标记，是否同意其他用户的公聊请求
    public boolean isVideo = false;

    public void setIsVideo(boolean isVideo) {
        this.isVideo = isVideo;
    }

    public boolean getIsVideo() {
        return isVideo;
    }

    /**
     * 公聊的用户量
     */
    public int publicCounts = 0;

    /**
     * 公聊添加上麦的数量，更新视频渲染的位置
     */
    public void setAddPublicCounts() {
        publicCounts++;
        setRenderCount(renderCount + 1);
    }

    /**
     * 公聊移除上麦的数量,更新视频渲染的位置
     */
    public void setRemovePublicCounts() {
        publicCounts--;
        setRenderCount(renderCount - 1);
    }

    /**
     * 向公聊用户发送answer
     */
    public void send() {
        if (sessionDescription == null) {
            setIsVideo(true);
        } else {
            Log.d(TAG, "target_ID---" + target_ID);
            Log.d(TAG, "sessionDescription---" + sessionDescription);
            // 创建连接对象
            mainActivity.resetPeerconnection(target_ID);
            renderLocal();
            // 创建ICE网络候选集合
            setIceCandidates(target_ID);
            mainActivity.setIinitiator();
            mainActivity.onRemoteDescription(sessionDescription, target_ID);
        }
    }

    public HashMap<String, List<IceCandidate>> candidateMaps = new HashMap<>();

    /**
     * 实施细节：观察ICE和流变化并作出相应的反应。
     * 这个接口提供了一种监测PeerConnection事件的方法，例如收到MediaStream时，
     * 或者发现iceCandidates 时，或者需要重新建立通讯时。这个接口必须被实现，
     * 以便你可以有效处理收到的事件，例如当对方变为可见时，向他们发送信号iceCandidates。
     */
    public class PCObserver implements PeerConnection.Observer {
        public String clientID;

        public PCObserver(String clientID) {
            this.clientID = clientID;
        }

        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            if (candidate != null) {
                if (isInitiator) {
                    if (!("_offer_video".equals(eventType)) && !("_offer_agree_video".equals(eventType)) && !("_offer_agree_video_private".equals(eventType))) {
                        Log.d(isInitiator + "onIceCandidate", "onIceCandidate---监听到，然后发送" + isInitiator);
                        // 回调发送candidate
                        events.onIceCandidate(candidate, clientID);
                    }
                } else {
                    Log.d(isInitiator + "onIceCandidate", "onIceCandidate---监听到，然后发送" + isInitiator);
                    events.onIceCandidate(candidate, clientID);
                }
                candidateMaps.get(clientID).add(candidate);
            }
        }

        @Override
        public void onSignalingChange(
                PeerConnection.SignalingState newState) {
            Log.d(TAG, "SignalingState: " + newState);
        }

        //监听连接状态改变事件
        @Override
        public void onIceConnectionChange(
                final PeerConnection.IceConnectionState newState) {
            Log.d("onIceConnectionChange", clientID + "onIceConnectionChange: " + newState);
            Log.d("onIceConnectionChange", clientID + "peer端当前连接状态切换为：" + newState);
            if (PeerConnection.IceConnectionState.FAILED == newState || PeerConnection.IceConnectionState.DISCONNECTED == newState) {
                if (isInitiator) {
                    List<IceCandidate> candidates = candidateMaps.get(clientID);
                    for (int i = 0; i < candidates.size(); i++) {
                        events.onIceCandidate(candidates.get(i), clientID);
                        Log.d(TAG, "peer端发送candidate---" + i);
                    }
                }
                Log.d(TAG, "本地请求的peer端连接失败,重新发送候选信息,连接状态切换为：" + newState);
            } else if (PeerConnection.IceConnectionState.CONNECTED == newState) {
                //exitSender();//退出转发
                //sendLocalStream(client_ID);//转发音视频流
            }
        }

        @Override
        public void onIceGatheringChange(
                PeerConnection.IceGatheringState newState) {
            Log.d(TAG, "IceGatheringState: " + newState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
            Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
        }

        @Override
        public void onAddStream(final MediaStream stream) {
            Log.d(TAG, "onAddStream========");
            if (MyApplication.peers.get(clientID) == null || isError) {
                return;
            }
            if (stream.audioTracks.size() > 1 || stream.videoTracks.size() > 1) {
                // 奇怪的流
                Log.d(TAG, "Weird-looking stream");
                reportError("Weird-looking stream: " + stream);
                return;
            }
            if (stream.videoTracks.size() == 1) {
                Log.d(TAG, "收到对方answer调用，渲染用户音屏");
                remoteVideoTrack = stream.videoTracks.get(0);
                remoteVideoTrack.setEnabled(renderVideo);

                if (renderCount > 1) {
                    mainActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mainActivity, "已达到渲染上限！！", Toast.LENGTH_SHORT).show();
                        }
                    });

                }
                if (roomConnectionParameters.adminid.equals(roomConnectionParameters.userid)) {
                    mainActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mainActivity.showViewRender("2");
                        }
                    });
                    remoteVideoTrack.addRenderer(new VideoRenderer(localRender));
                } else {
                    mainActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mainActivity.showViewRender("1");
                        }
                    });
                    remoteVideoTrack.addRenderer(new VideoRenderer(remoteRender));
                }


            }
        }

        @Override
        public void onRemoveStream(final MediaStream stream) {
            remoteVideoTrack = null;
        }

        @Override
        public void onDataChannel(final DataChannel dc) {
        }

        @Override
        public void onRenegotiationNeeded() {

        }
    }

    /**
     * 使用编解码器
     *
     * @param sdpDescription sdp描述
     * @param codec          编解码
     * @param isAudio
     * @return
     */
    private static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
        String[] lines = sdpDescription.split("\r\n");
        int mLineIndex = -1;
        String codecRtpMap = null;
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);
        String mediaDescription = "m=video ";
        if (isAudio) {
            mediaDescription = "m=audio ";
        }
        for (int i = 0; (i < lines.length) && (mLineIndex == -1 || codecRtpMap == null); i++) {
            if (lines[i].startsWith(mediaDescription)) {
                mLineIndex = i;
                continue;
            }
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                continue;
            }
        }
        if (mLineIndex == -1) {
            Log.w(TAG, "No " + mediaDescription + " line, so can't prefer " + codec);
            return sdpDescription;
        }
        if (codecRtpMap == null) {
            Log.w(TAG, "No rtpmap for " + codec);
            return sdpDescription;
        }
        Log.d(TAG, "Found " + codec + " rtpmap " + codecRtpMap + ", prefer at " + lines[mLineIndex]);
        String[] origMLineParts = lines[mLineIndex].split(" ");
        if (origMLineParts.length > 3) {
            StringBuilder newMLine = new StringBuilder();
            int origPartIndex = 0;
            // Format is: m=<media> <port> <proto> <fmt> ...
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(origMLineParts[origPartIndex++]).append(" ");
            newMLine.append(codecRtpMap);
            for (; origPartIndex < origMLineParts.length; origPartIndex++) {
                if (!origMLineParts[origPartIndex].equals(codecRtpMap)) {
                    newMLine.append(" ").append(origMLineParts[origPartIndex]);
                }
            }
            lines[mLineIndex] = newMLine.toString();
            Log.d(TAG, "Change media description: " + lines[mLineIndex]);
        } else {
            // SDP媒体描述格式错误
            Log.e(TAG, "Wrong SDP media description format: " + lines[mLineIndex]);
        }
        StringBuilder newSdpDescription = new StringBuilder();
        for (String line : lines) {
            newSdpDescription.append(line).append("\r\n");
        }
        return newSdpDescription.toString();
    }

    /**
     * 实施细节：处理呼叫创建/信令和answer设置，以及一旦设置了answer SDP，则添加远程ICE候选。
     */
    private class SDPObserver implements SdpObserver {
        public String clientID;

        public SDPObserver(String clientID) {
            this.clientID = clientID;
        }

        @Override
        public void onCreateSuccess(final SessionDescription origSdp) {
            Log.d("localSdp", "localSdp---" + localSdp);
            if (localSdp != null) {
                Log.d(TAG, "localSdp is not null");
            }
            Log.d(TAG, "创建" + origSdp.type.canonicalForm() + "成功" + clientID);
            String sdpDescription = origSdp.description;
            sdpDescription = preferCodec(sdpDescription, AUDIO_CODEC_ISAC, true);
            // 首选视频编解码器
            //preferredVideoCodec = VIDEO_CODEC_H264;
            sdpDescription = preferCodec(sdpDescription, preferredVideoCodec, false);
            final SessionDescription sdp = new SessionDescription(
                    origSdp.type, sdpDescription);
            localSdp = sdp;
            // 设置本地SDP
            MyApplication.peers.get(clientID).setLocalDescription(sdps.get(clientID), localSdp);
            Log.d("setSDP", clientID + "Set local SDP from " + localSdp.type + "成功");
        }

        @Override
        public void onSetSuccess() {
            Log.d(TAG, "onSetSuccess" + clientID);
            if (MyApplication.peers.get(clientID) == null || isError) {
                return;
            }
            // 是发起者
            if (isInitiator) {
                // 为了提供对等连接，我们首先创建呼叫caller并设置本地SDP，然后在收到远程SDP的应答之后。
                if (MyApplication.peers.get(clientID).getRemoteDescription() == null) {
                    // 我们刚刚设置了本地SDP等时间到了发送。
                    Log.d(TAG, isInitiator + "Local SDP set succesfully" + clientID);
                    events.onLocalDescription(localSdp, eventType, clientID);
                } else {
                    // 我们刚刚设置了远程描述，所以排出远程并发送本地的ICE网络候选。
                    Log.d(TAG, "Remote SDP set succesfully");
                    for (int i = 0; i < candidates.size(); i++) {
                        events.onIceCandidate(candidates.get(i), clientID);
                    }
                }
            } else {
                // 对于应答对点连接，我们设置远程SDP，然后创建应答并设置本地SDP。
                if (MyApplication.peers.get(clientID).getLocalDescription() != null) {
                    // 我们刚刚设置了本地的SDP，以便发送它，排出远程并发送本地的ICE候选人。
                    Log.d(TAG, isInitiator + "Local SDP set succesfully" + clientID);
                    events.onLocalDescription(localSdp, eventType, clientID);
                } else {
                    // 我们刚刚设置远程SDP - 什么也不做，现在的答案将很快创建。
                    Log.d(TAG, clientID + "什么也不做 Remote SDP set succesfully");
                }
            }
        }

        @Override
        public void onCreateFailure(final String error) {
            reportError("createSDP error: " + error);
        }

        @Override
        public void onSetFailure(final String error) {
            Log.d("setSDP", clientID + "设置SDP失败====");
            reportError("setSDP error: " + error);
            mainActivity.offline();
        }
    }

    /**
     * Peer connection events.
     * 对等连接事件
     */
    public interface PeerConnectionEvents {
        /**
         * 本地SDP被创建并设置后，回调触发。
         */
        public void onLocalDescription(final SessionDescription sdp, String offerType, String client_ID);

        /**
         * 一旦本地的Ice候选人被生成，就回调。
         */
        public void onIceCandidate(final IceCandidate candidate, String cliend_ID);

        /**
         * 一旦对等连接关闭，回调触发。
         */
        public void onPeerConnectionClosed();


        /**
         * 一旦发生对等连接错误，就回调
         */
        public void onPeerConnectionError(final String description);
    }
}

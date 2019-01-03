package com.example.hj.myapplication.constants;

import org.webrtc.PeerConnection;

import java.util.Arrays;
import java.util.List;

/**
 * Created by hj on 2017/4/24.
 */
public class Constant {
    /**
     * stun和turn服务器
     */
    public static List<PeerConnection.IceServer> iceServers = Arrays.asList(
            new PeerConnection.IceServer("stun:turn.jiashizhan.com"),
            new PeerConnection.IceServer("turn:turn.jiashizhan.com", "zhimakai", "zhimakai888"),
            new PeerConnection.IceServer("stun:webrtcweb.com:7788", "muazkh", "muazkh"),
            new PeerConnection.IceServer("turn:webrtcweb.com:7788", "muazkh", "muazkh"),
            new PeerConnection.IceServer("turns:webrtcweb.com:7788", "muazkh", "muazkh"),
            new PeerConnection.IceServer("turn:webrtcweb.com:8877", "muazkh", "muazkh"),
            new PeerConnection.IceServer("turns:webrtcweb.com:8877", "muazkh", "muazkh"),
            new PeerConnection.IceServer("stun:webrtcweb.com:4455", "muazkh", "muazkh"),
            new PeerConnection.IceServer("turn:webrtcweb.com:4455", "muazkh", "muazkh"),
            new PeerConnection.IceServer("turn:webrtcweb.com:3344", "muazkh", "muazkh"),
            new PeerConnection.IceServer("turn:webrtcweb.com:4433", "muazkh", "muazkh"),
            new PeerConnection.IceServer("turn:webrtcweb.com:5544?transport=tcp", "muazkh", "muazkh")
    );
}

package com.example.hj.myapplication.webrtc;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.example.hj.myapplication.Bean.RoomConnectionParameters;
import com.example.hj.myapplication.MainActivity;
import com.example.hj.myapplication.MyApplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketHandler;
import de.tavendo.autobahn.WebSocketOptions;


/**
 * webSocket的协议，用于和房间服务器进行通信
 */
public class WsSocketutils {
    private static final String TAG = "WsSocketutils";
    private WebSocketConnection mWebSocketConnection;
    private Timer mTimer;
    private TimerTask mTimerTask;
    private WebSocketOptions mWebSocketOptions;
    private long nowdate;
    private String serverUri;
    private MainActivity mainActivity;
    /**
     * 指令类型（区别服务的消息）
     */
    private String eventType;
    private PeerConnectionClient peerConnection;
    private String userid;
    private String username;
    private String hasAdmin;
    private String adminid;
    /**
     * 其他用户客户端ID
     */
    private JSONArray client_IDs = new JSONArray();
    /**
     * 其他用户客户端名称
     */
    private JSONArray client_NAMEs = new JSONArray();
    /**
     * 麦序列表
     */
    private ArrayList<HashMap<String, String>> micLists = new ArrayList<>();
    /**
     * 用户列表
     */
    private ArrayList<HashMap<String, String>> userLists = new ArrayList<>();
    /**
     * 聊天列表
     */
    private HashMap<String, HashMap<String, String>> chatLists = new HashMap<>();
    /**
     * 请求类型
     */
    private String offerType = " ";
    /**
     * 记录公聊用户量
     */
    private List<String> publicCount = new ArrayList<>();
    private boolean isOnceAginAdd = false;
    private String testOfferType = " ";
    private String audioOfferType = "";
    private String testAudioOfferType = "";

    /**
     * @param roomConnectionParameters 连接房间参数
     */
    public WsSocketutils(RoomConnectionParameters roomConnectionParameters) {
        mWebSocketConnection = new WebSocketConnection();
        mTimer = new Timer();
        String roomid = roomConnectionParameters.roomid;
        userid = roomConnectionParameters.userid;
        String password = roomConnectionParameters.password;
        username = roomConnectionParameters.username;
        adminid = roomConnectionParameters.adminid;
        String roomname = roomConnectionParameters.roomname;
        String roomIp = roomConnectionParameters.roomip;
        String state = "conn";
        if (userid.equals(adminid)) {
            hasAdmin = "1";
        } else {
            hasAdmin = "0";
        }
        // wss:/.jiashizhan.com:8443/Conference/websocket/2/2/1/1/2/2/conn
        this.serverUri = "ws://" + roomIp + ":8080/Conference/websocket/" +
                roomid + "/" + userid + "/" + password + "/" + username + "/" + adminid + "/" +
                roomname + "/" + state;
        mWebSocketOptions = new WebSocketOptions();
        setwebsocketoptions();
        //心跳包，不停地发送消息给服务器0
        mTimerTask = new TimerTask() {
            @Override
            public void run() {
                mWebSocketConnection.sendTextMessage("_offer_connect");
                Log.i(TAG, "连接中。。。。。");
            }
        };
    }

    /**
     * 连接服务器端的代码
     *
     * @param context              上下文
     * @param peerConnectionClient 对点连接客户端
     */
    public void connect(Context context, final PeerConnectionClient peerConnectionClient) {
        mainActivity = (MainActivity) context;
        peerConnection = peerConnectionClient;
        try {
            mWebSocketConnection.connect(serverUri, new WebSocketHandler() {
                        @Override
                        public void onOpen() {
                            // 连接成功，服务端返回pong包
                            Log.d(TAG, "Status: 连接成功--Connected to " + serverUri);
                            // 开启心跳包
                            sendHB();
                        }

                        @TargetApi(Build.VERSION_CODES.KITKAT)
                        @Override
                        public void onTextMessage(String payload) {
                            Log.d(TAG, "Get Server Data======: " + payload);
                            nowdate = System.currentTimeMillis();
                            try {
                                JSONObject jsonObject = new JSONObject(payload);
                                eventType = (String) jsonObject.get("event");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            if (eventType.equals("_pong")) {
                                // hasAdmin 1代表房主 0代表用户
                                String pingMessage = "{\"event\":\"_ping\",\"sender_ID\":\"" + userid + "\",\"target_ID\":\"服务器\",\"hasAdmin\":\"" + hasAdmin + "\",data\":{\"message\":" + userid + "--ping}}";
                                sendMessage(pingMessage);
                            } else if (eventType.equals("_login")) {
                                //{"event":"_login","sender_ID":"2","target_ID":"1","sender_NAME":"2","data":{"message":"用户上线"}}
                                // 房主已经开始视频了，才对刚刚上线的用户发送offer
                                // sender_ID == 房主ID
                                String sender_ID = "";
                                try {
                                    JSONObject json = new JSONObject(payload);
                                    sender_ID = (String) json.get("sender_ID");
                                    String sender_NAME = (String) json.get("sender_NAME");
                                    // 房主
                                    if (adminid.equals(sender_ID)) {
                                        // 更新麦序列表
                                        HashMap<String, String> packs = new HashMap<>();
                                        packs.put(sender_ID, sender_NAME);
                                        micLists.add(packs);
                                    } else {
                                        // 更新用户列表
                                        HashMap<String, String> packs = new HashMap<>();
                                        packs.put(sender_ID, sender_NAME);
                                        userLists.add(packs);
                                    }
                                    Log.d(TAG, "userLists---" + userLists.toString());

                                    mainActivity.setData(micLists, userLists);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                if ("1".equals(hasAdmin)) { //房主
                                    if (mainActivity.getIsStartVideo()) {
                                        Log.d(TAG, "向登录进来的用户发送offer");
                                        // 创建连接对象
                                        createOfferPeer("_offer_start_media", sender_ID);
                                    }
                                } else {
                                    // 用户正在公聊中,用户加入音视频聊天---公聊
                                    if ("_offer_video".equals(offerType)) {
                                        Log.d(TAG, "其他用户上线，发送请求--......offerType" + offerType);
                                        if (adminid != sender_ID) {
                                            createOfferPeer(offerType, sender_ID);
                                        }
                                    }
                                }

                            } else
                                // 用户/房主 操作
                                if (eventType.equals("_waitOffer")) {
                                    Log.d(TAG, payload + "----");
                                    //{"event":"_waitOffer","sender_ID":"服务器","target_ID":"2","sender_NAME":"2","room_NAME":"2","data":{"message":"请等待其他用户登录"}}
                                    //服务器发送的消息，第一个加入房间的，等待其他人请求
                                    //Log.d(TAG, "服务端_waitOffer信息====" + message.getEvent() + message.getSender_ID() + message.getTarget_ID() + message.getData());
                                    // 如果是房主 则默认更新列表
                                    if ("1".equals(hasAdmin)) {
                                        HashMap<String, String> packs = new HashMap<>();
                                        packs.put(userid, username);
                                        micLists.add(packs);
                                    } else { // 如果是用户 则添加到用户列表集合中
                                        HashMap<String, String> packs = new HashMap<>();
                                        packs.put(userid, username);
                                        userLists.add(packs);
                                    }
                                    mainActivity.setData(micLists, userLists);
                                } else if ("_logout".equals(eventType)) {
                                    // {"event":"_logout","sender_ID":"2","target_ID":"所有连接对象","sender_NAME":"2","data":{"message":"用户下线"}}
                                    try {
                                        JSONObject json = new JSONObject(payload);
                                        String clienID = (String) json.get("sender_ID");

                                        PeerConnection peer = MyApplication.peers.get(clienID);
                                        if (peer != null) {//处理连接
                                            peer.close();
                                            peer = null;
                                        }
                                        // 房主下线
                                        if (clienID.equals(adminid)) {
                                            Log.d(TAG, "房主退出，隐藏视图");
                                            // 隐藏
                                            mainActivity.hideViewRender("1");
                                            mainActivity.hideViewRender("2");
                                        }
                                        //clienID = clienID + "_公";
                                        Log.d("micLists---", micLists.toString());
                                        // 处于麦序列表中
                                        if (isContain(micLists, clienID)) {
                                            // 用户
                                            if (!adminid.equals(clienID)) {
                                                int count = peerConnection.getRenderCount();
                                                if (count == 1) {
                                                    //隐藏第一个view
                                                    Log.d(TAG, "当前渲染在" + count);
                                                    mainActivity.hideViewRender("2");
                                                }
                                                if (mainActivity.getAgreeVideoCount() > 0) {
                                                    peerConnection.setRenderCount(count - 1);
                                                }
                                            } else { //房主
                                                mainActivity.hideViewRender("1");
                                            }
                                        }
                                        Log.e("isContain", isContain(micLists, clienID) + "");
                                        if (isContain(micLists, clienID)) {
                                            removeElement(micLists, clienID);
                                            Log.d("micLists---2", micLists.toString());//micLists---: [{2=2}, {1_私=1}]
                                        } else {
                                            removeElement(userLists, clienID);
                                        }
                                        Log.d("micLists---1", clienID.split("_")[0] + "");
                                        Log.d("micLists---", micLists.toString());
                                        mainActivity.setData(micLists, userLists);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                } else
                                    // 下线
                                    if ("_offline".equals(eventType)) {
                                        //{"event":"_offline","sender_ID":"1","target_ID":"server","media_TYPE":"公开","data":{"message":"正常退出会议室"}}
                                        try {
                                            JSONObject json = new JSONObject(payload);
                                            String clienID = (String) json.get("sender_ID");

                                            PeerConnection peer = MyApplication.peers.get(clienID);
                                            if (peer != null) {//处理连接
                                                peer.close();
                                                peer = null;
                                            }
                                            // 房主下线
                                            if (clienID.equals(adminid)) {
                                                Log.d(TAG, "房主退出，隐藏视图");
                                                // 隐藏
                                                mainActivity.hideViewRender("1");
                                                mainActivity.hideViewRender("2");
                                            }
                                            //clienID = clienID + "_公";
                                            Log.d("micLists---", micLists.toString());
                                            // 处于麦序列表中
                                            if (isContain(micLists, clienID)) {
                                                // 用户
                                                if (!adminid.equals(clienID)) {
                                                    int count = peerConnection.getRenderCount();
                                                    if (count == 1) {
                                                        //隐藏第一个view
                                                        Log.d(TAG, "当前渲染在" + count);
                                                        mainActivity.hideViewRender("2");
                                                    }
                                                    if (mainActivity.getAgreeVideoCount() > 0) {
                                                        peerConnection.setRenderCount(count - 1);
                                                    }
                                                } else { //房主
                                                    mainActivity.hideViewRender("1");
                                                }
                                            }
                                            Log.e("isContain", isContain(micLists, clienID) + "");
                                            if (isContain(micLists, clienID)) {
                                                removeElement(micLists, clienID);
                                                Log.d("micLists---2", micLists.toString());//micLists---: [{2=2}, {1_私=1}]
                                            } else {
                                                removeElement(userLists, clienID);
                                            }
                                            Log.d("micLists---1", clienID.split("_")[0] + "");
                                            Log.d("micLists---", micLists.toString());
                                            mainActivity.setData(micLists, userLists);
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                    } else
                                        // 用户/房主 操作
                                        if (eventType.equals("_canOffer")) {
                                            //服务端把该房间中的其他成员的录入信息，发送给你
                                            // 自己
                                            try {
                                                JSONObject json = new JSONObject(payload);
                                                JSONObject data = (JSONObject) json.get("data");
                                                client_IDs = (JSONArray) data.get("client_IDs");
                                                client_NAMEs = (JSONArray) data.get("client_NAMEs");
                                                JSONArray room_VMICs = (JSONArray) data.get("room_VMIC"); // 公聊
                                                JSONArray room_PVMICs = (JSONArray) data.get("room_PMIC");// 私聊
                                                for (int i = 0; i < client_IDs.length(); i++) {
                                                    String clientID = (String) client_IDs.get(i);
                                                    Log.d(TAG, "clientID--" + clientID);
                                                    String clientName = (String) client_NAMEs.get(i);
                                                    if (adminid.equals(clientID)) {
                                                        // 是房主
                                                        Log.d(TAG, "是房主" + clientName);
                                                        HashMap<String, String> packs = new HashMap<>();
                                                        packs.put(clientID, clientName);
                                                        micLists.add(packs);
                                                    } else {
                                                        Log.d(TAG, "是用户");
                                                        HashMap<String, String> packs = new HashMap<>();
                                                        packs.put(clientID, clientName);
                                                        userLists.add(packs);
                                                    }
                                                }
                                                for (int i = 0; i < room_VMICs.length(); i++) { // 麦序用户（包含私聊上麦人数）
                                                    String VMIC_ID = (String) room_VMICs.get(i);
                                                    if (!isContain(micLists, VMIC_ID)) {
                                                        String VMIC_Name = " ";
                                                        for (int j = 0; j < userLists.size(); j++) {
                                                            HashMap<String, String> keyVaule = userLists.get(j);
                                                            for (String key : keyVaule.keySet()) {
                                                                if (VMIC_ID.equals(key)) { //包含
                                                                    VMIC_Name = keyVaule.get(VMIC_ID);
                                                                    break;
                                                                }
                                                            }
                                                        }
                                                        HashMap<String, String> packs = new HashMap<>();
                                                        packs.put(VMIC_ID + "_公", VMIC_Name);
                                                        micLists.add(packs);
                                                        removeElement(userLists, VMIC_ID);
                                                    }
                                                }
                                                for (int i = 0; i < room_PVMICs.length(); i++) { // 私聊上麦用户
                                                    String PVMIC_ID = (String) room_VMICs.get(i);
                                                    if (isContain(micLists, PVMIC_ID + "_公")) {
                                                        String PVMIC_Name = "";
                                                        for (int j = 0; j < micLists.size(); j++) {
                                                            HashMap<String, String> keyVaule = micLists.get(j);
                                                            for (String key : keyVaule.keySet()) {
                                                                if (PVMIC_ID.equals(key.split("_")[0])) { //包含
                                                                    PVMIC_Name = keyVaule.get(key);
                                                                    break;
                                                                }
                                                            }
                                                        }
                                                        removeElement(micLists, PVMIC_ID + "_公");
                                                        HashMap<String, String> packs = new HashMap<>();
                                                        packs.put(PVMIC_ID + "_私", PVMIC_Name);
                                                        micLists.add(packs);
                                                        removeElement(userLists, PVMIC_ID);
                                                    }
                                                }

                                                if ("1".equals(hasAdmin)) {
                                                    HashMap<String, String> packs = new HashMap<>();
                                                    packs.put(userid, username);
                                                    micLists.add(packs);
                                                } else {
                                                    HashMap<String, String> packs = new HashMap<>();
                                                    packs.put(userid, username);
                                                    userLists.add(packs);
                                                }
                                                Log.d(TAG, "micLists==" + micLists.toString());
                                                Log.d(TAG, "userLists==" + userLists.toString());
                                                // 房主
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                            //拿到对方的信息,设置给主页面
                                            mainActivity.setData(micLists, userLists);
                                            //发送offer
                                            /**
                                             "event": command,"sender_ID": thisClient_ID,   我的ID"target_ID": client_ID,"data": {"sdp": desc}
                                             */
                                        } else
                                            // 下麦 用户操作
                                            if ("_mic_video_down".equals(eventType)) {
                                                Log.d("当前offerType：", offerType);
                                                //{"event":"_mic_video_down","sender_ID":"1","target_ID":"所有人","sender_NAME":"1","data":{"message":"申请视频下麦"}}
                                                try {
                                                    JSONObject json = new JSONObject(payload);
                                                    String clientID = (String) json.get("sender_ID");
                                                    if (offerType.equals("_offer_video") || testOfferType.equals("_me_offer_video")) {
                                                        if (MyApplication.peers.containsKey(clientID)) {
                                                            PeerConnection currentPeer = MyApplication.peers.get(clientID);
                                                            currentPeer.close();
                                                            currentPeer = null;
                                                            MyApplication.peers.remove(clientID);
                                                        }
                                                    }

                                                    if (mainActivity.getInvites().contains(clientID + "_私") && "1".equals(hasAdmin)) { //私聊下麦
                                                        if (MyApplication.peers.containsKey(clientID)) {
                                                            PeerConnection currentPeer = MyApplication.peers.get(clientID);
                                                            currentPeer.close();
                                                            currentPeer = null;
                                                            MyApplication.peers.remove(clientID);
                                                            //隐藏第一个view
                                                            mainActivity.hideViewRender("2");
                                                            //mainActivity.hideViewRender("1");

                                                            Log.e("peers", "重新发送_offer_start_media请求" + isOnceAginAdd);
                                                            // 创建连接对象
                                                            mainActivity.resetPeerconnection(clientID);

                                                            if (mainActivity.getIsStartVideo()) {
                                                                peerConnection.addStream(clientID);
                                                            } else {
                                                                peerConnection.isAddStream(clientID);
                                                                isOnceAginAdd = true;
                                                            }
                                                            peerConnectionClient.setIceCandidates(clientID);
                                                            mainActivity.setIinitiator("1");
                                                            peerConnectionClient.createOffer("_offer_start_media", clientID);
                                                        }
                                                        Log.e("私聊下麦", mainActivity.getAgreeVideoCount() + "===");
                                                        mainActivity.setAgreeVideoCount(mainActivity.getAgreeVideoCount() - 1);
                                                        mainActivity.getInvites().remove(clientID + "_私");
                                                        Log.e("私聊下麦", mainActivity.getAgreeVideoCount() + "");
                                                    }
                                                    //用户下线，清除麦序列表
                                                    if (isContain(micLists, clientID)) {
                                                        removeElement(micLists, clientID);
                                                        Log.d("micLists---22", micLists.toString());
                                                    } else {
                                                        removeElement(userLists, clientID);
                                                    }
                                                    mainActivity.setData(micLists, userLists);


                                                    // 处于麦序列表中
                                                    if (!adminid.equals(clientID)) {
                                                        int count = WsSocketutils.this.peerConnection.getRenderCount();
                                                        //mainActivity.setAgreeVideoCount(count);
                                                        Log.d(TAG, "当前渲染的位置：" + count);
                                                        if (count > 0) {
                                                            if (count == 1) {
                                                                //隐藏第一个view
                                                                mainActivity.hideViewRender("2");
                                                            }
                                                            WsSocketutils.this.peerConnection.setRenderCount(count - 1);
                                                        }
                                                    }
                                                    if (testOfferType.equals("_me_offer_video")) {
                                                        peerConnection.renderLocal();
                                                    }
                                                    String clientName = (String) json.get("sender_NAME");
                                                    Log.d(TAG, "当前下线--" + clientID + "_公");
                                                    // 更新到麦序列表
                                                    if (isContain(micLists, clientID + "_公")) {
                                                        Log.d(TAG, "包含--" + clientID + "_公");
                                                        removeElement(micLists, clientID + "_公");
                                                    }
                                                    if (isContain(micLists, clientID + "_私")) {
                                                        removeElement(micLists, clientID + "_私");
                                                    }
                                                    HashMap<String, String> packs = new HashMap<>();
                                                    packs.put(clientID, clientName);
                                                    userLists.add(packs);
                                                    mainActivity.setData(micLists, userLists);
                                                } catch (JSONException e) {
                                                    e.printStackTrace();
                                                }
                                            } else
                                                // 房主 操作
                                                if (eventType.equals("_offer_start_media")) {
                                                    try {
                                                        JSONObject jsonData = new JSONObject(payload);
                                                        // 解析sdp
                                                        JSONObject remoteSdpData = (JSONObject) jsonData.get("data");
                                                        JSONObject sdp = (JSONObject) remoteSdpData.get("sdp");
                                                        // 目标ID，响应offer，设置远程sdp，发送answer，建立P2P
                                                        String sender_ID = (String) jsonData.get("sender_ID");
                                                        // 房主开启了音视频聊天
                                                        // 创建连接对象
                                                        mainActivity.resetPeerconnection(sender_ID);
                                                        // 创建ICE网络候选集合
                                                        peerConnection.setIceCandidates(sender_ID);
                                                        String remoteSdp = (String) sdp.get("sdp");
                                                        String type = (String) sdp.get("type");
                                                        SessionDescription.Type typee = Enum.valueOf(SessionDescription.Type.class, type.toUpperCase());
                                                        SessionDescription sessionDescription = new SessionDescription(typee, remoteSdp);
                                                        mainActivity.setIinitiator();
                                                        mainActivity.onRemoteDescription(sessionDescription, sender_ID);
                                                    } catch (JSONException e) {
                                                        e.printStackTrace();
                                                    }
                                                } else
                                                    // 用户操作
                                                    if (eventType.equals("_mic_video_on")) {
                                                        // 视频上麦
                                                        // 私聊/公聊 -->同意上麦
                                                        //{"eventType":"_mic_video_on","sender_ID":"1","target_ID":"所有人","sender_NAME":"1","media_TYPE":"公开","data":{"message":"申请视频上麦"}}
                                                        try {
                                                            JSONObject json = new JSONObject(payload);
                                                            String clientID = (String) json.get("sender_ID");
                                                            String clientName = (String) json.get("sender_NAME");
                                                            String media_TYPE = (String) json.get("media_TYPE");
                                                            if ("公开".equals(media_TYPE)) {// 公聊
                                                                HashMap<String, String> packs = new HashMap<>();
                                                                packs.put(clientID + "_公", clientName);
                                                                micLists.add(packs);
                                                                removeElement(userLists, clientID);
                                                            } else {// 私聊
                                                                HashMap<String, String> packs = new HashMap<>();
                                                                packs.put(clientID + "_私", clientName);
                                                                micLists.add(packs);
                                                                removeElement(userLists, clientID);
                                                            }
                                                            Log.d(TAG, "micLists===" + micLists.toString());
                                                            // 更新到麦序列表
                                                            mainActivity.setData(micLists, userLists);
                                                        } catch (JSONException e) {
                                                            e.printStackTrace();
                                                        }
                                                    } else
                                                        // 用户/房主 操作
                                                        if (eventType.equals("_ice_candidate")) {
                                                            // 添加网络候选信息
                                                            try {
                                                                JSONObject jsonData = new JSONObject(payload);
                                                                Log.d(TAG, "_ice_candidate===" + payload);
                                                                String sender_ID = (String) jsonData.get("sender_ID");
                                                                // 解析iceCandidate信息
                                                                JSONObject remoteSdpData = (JSONObject) jsonData.get("data");
                                                                JSONObject candidate = (JSONObject) remoteSdpData.get("candidate");
                                                                String candidateSdp = (String) candidate.get("candidate");
                                                                String sdpMid = (String) candidate.get("sdpMid");
                                                                int sdpMLineIndex = (int) candidate.get("sdpMLineIndex");
                                                                IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidateSdp);
                                                                // 添加ICE网络候选
                                                                peerConnection.addRemoteIceCandidate(iceCandidate, sender_ID);
                                                            } catch (JSONException e) {
                                                                e.printStackTrace();
                                                            }

                                                        } else
                                                            // 用户/房主 操作
                                                            if (eventType.equals("_answer")) {
                                                                // 响应offer，建立P2P
                                                                Log.d(TAG, "_answer===" + payload);
                                                                try {
                                                                    JSONObject jsonData = new JSONObject(payload);
                                                                    // 解析sdp
                                                                    JSONObject remoteSdpData = (JSONObject) jsonData.get("data");
                                                                    String target_ID = (String) jsonData.get("sender_ID");
                                                                    JSONObject sdp = (JSONObject) remoteSdpData.get("sdp");
                                                                    String remoteSdp = (String) sdp.get("sdp");
                                                                    String type = (String) sdp.get("type");
                                                                    SessionDescription.Type typee = Enum.valueOf(SessionDescription.Type.class, type.toUpperCase());
                                                                    SessionDescription sessionDescription = new SessionDescription(typee, remoteSdp);
                                                                    peerConnectionClient.setRemoteDescription(sessionDescription, target_ID);
                                                                    //发送ice
                                                                    peerConnectionClient.sendCandidates(target_ID);
                                                                } catch (JSONException e) {
                                                                    e.printStackTrace();
                                                                }

                                                            } else if (eventType.equals("_textChat_message")) {
                                                                //{"event":"_textChat_message","sender_ID":"2","target_ID":"所有人","sender_NAME":"2","data":{"message":"<label style='color:blue;'>@3:</label><p>sfjskf</p><p><br/></p>"}}
                                                                //<label style='color:blue;'>@3:</label><p>sfjskf</p><p><br/></p>
                                                                // <p>sfjskf<br/></p>
                                                                try {
                                                                    JSONObject json = new JSONObject(payload);
                                                                    JSONObject data = (JSONObject) json.get("data");
                                                                    String sendName = (String) json.get("sender_NAME");
                                                                    String senderID = (String) json.get("sender_ID");
                                                                    String targetID = (String) json.get("target_ID");
                                                                    String message = (String) data.get("message");
                                                                    HashMap<String, String> map = new HashMap();
                                                                    if ("所有人".equals(targetID)) {// 普通公聊/@公聊
                                                                        map.put(sendName, message);
                                                                        chatLists.clear();
                                                                        chatLists.put(senderID, map);
                                                                    } else { //私聊
                                                                        if (userid.equals(targetID)) {//对方私聊自己
                                                                            map.put(sendName, message);
                                                                            chatLists.clear();
                                                                            chatLists.put(senderID, map);
                                                                        }
                                                                    }
                                                                    mainActivity.setData(chatLists);
                                                                } catch (JSONException e) {
                                                                    e.printStackTrace();
                                                                }
                                                            } else
                                                                // 房主同意了用户音视频------私聊
                                                                if (eventType.equals("_offer_agree_video_private")) {
                                                                    mainActivity.showViewRender("2");
                                                                    Log.e("agree_video_private", "房主同意私聊了！");
                                                                    offerType = "_offer_agree_video_private";
                                                                    try {
                                                                        JSONObject jsonData = new JSONObject(payload);
                                                                        // 解析sdp
                                                                        JSONObject remoteSdpData = (JSONObject) jsonData.get("data");
                                                                        JSONObject sdp = (JSONObject) remoteSdpData.get("sdp");
                                                                        String target_ID = (String) jsonData.get("sender_ID");
                                                                        // 目标ID，响应offer，设置远程sdp，发送answer，建立P2P

                                                                        if (MyApplication.peers.containsKey(target_ID)) {
                                                                            PeerConnection currentPeer = MyApplication.peers.get(target_ID);
                                                                            currentPeer.close();
                                                                            currentPeer = null;
                                                                            MyApplication.peers.remove(target_ID);
                                                                            //隐藏第一个view
                                                                        }

                                                                        // 创建连接对象
                                                                        mainActivity.resetPeerconnection(target_ID);
                                                                        Log.e("isOnceAginAdd", isOnceAginAdd + "");
                                                                        if (isOnceAginAdd) {
                                                                            peerConnection.addStream(target_ID);
                                                                        } else {
                                                                            peerConnection.isAddStream(target_ID);
                                                                            isOnceAginAdd = true;
                                                                        }

                                                                        peerConnection.setIceCandidates(target_ID);
                                                                        String remoteSdp = (String) sdp.get("sdp");
                                                                        String type = (String) sdp.get("type");
                                                                        SessionDescription.Type typee = Enum.valueOf(SessionDescription.Type.class, type.toUpperCase());
                                                                        SessionDescription sessionDescription = new SessionDescription(typee, remoteSdp);
                                                                        mainActivity.setIinitiator();
                                                                        mainActivity.onRemoteDescription(sessionDescription, target_ID);
                                                                    } catch (JSONException e) {
                                                                        e.printStackTrace();
                                                                    }
                                                                } else if (eventType.equals("_repeatLogin")) {
                                                                    Toast.makeText(mainActivity, "当前用户已登录，无法进入!!", Toast.LENGTH_SHORT).show();
                                                                } else if (eventType.equals("_forbidOnMic")) {
                                                                    // {"event":"_forbidOnMic","sender_ID":"2","target_ID":1,"data":{"message":"您已被房主禁止文字发言和音视频上麦!"}}
                                                                    // 执行下麦操作downVideoMic()
                                                                    mainActivity.downVideoMic();
                                                                    //更新UI
                                                                    try {
                                                                        JSONObject json = new JSONObject(payload);
                                                                        String target_ID = (String) json.get("target_ID");
                                                                        //用户下线，清除麦序列表
                                                                        if (isContain(micLists, target_ID)) {
                                                                            removeElement(micLists, target_ID);
                                                                            Log.d("micLists---22", micLists.toString());
                                                                            Log.e("userLists--22:", userLists.toString());
                                                                        } else {
                                                                            removeElement(userLists, target_ID);
                                                                        }
                                                                        //mainActivity.setData(micLists, userLists);
                                                                        if (offerType.equals("_offer_agree_video_private")) { // 处于私聊中
// 处于麦序列
                                                                            if (!adminid.equals(target_ID)) {
                                                                                int count = WsSocketutils.this.peerConnection.getRenderCount();
                                                                                //mainActivity.setAgreeVideoCount(count);
                                                                                Log.d(TAG, "当前渲染的位置：" + count);
                                                                                if (count > 0) {
                                                                                    if (count == 1) {
                                                                                        //隐藏第一个view
                                                                                        mainActivity.hideViewRender("2");
                                                                                    }
                                                                                    WsSocketutils.this.peerConnection.setRenderCount(count - 1);
                                                                                }
                                                                            }
                                                                        }

                                                                        if (testOfferType.equals("_me_offer_video")) {
                                                                            peerConnection.renderLocal();
                                                                        }

                                                                        if (isContain(micLists, target_ID + "_私")) {
                                                                            removeElement(micLists, target_ID + "_私");
                                                                        }
                                                                        HashMap<String, String> packs = new HashMap<>();
                                                                        packs.put(target_ID, username);
                                                                        userLists.add(packs);
                                                                        Log.e("userLists:", userLists.toString());
                                                                        mainActivity.setData(micLists, userLists);
                                                                    } catch (Exception e) {
                                                                        e.printStackTrace();
                                                                    }

                                                                } else if (eventType.equals("_offer_agree_audio_private")) {
                                                                    try {
                                                                        JSONObject jsonData = new JSONObject(payload);
                                                                        // 解析sdp
                                                                        JSONObject remoteSdpData = (JSONObject) jsonData.get("data");
                                                                        JSONObject sdp = (JSONObject) remoteSdpData.get("sdp");
                                                                        // 目标ID，响应offer，设置远程sdp，发送answer，建立P2P
                                                                        String target_ID = (String) jsonData.get("sender_ID");
                                                                        // 创建连接对象
                                                                        mainActivity.resetPeerconnection(target_ID);
                                                                        peerConnection.isAddAudio(target_ID);
                                                                        peerConnection.setIceCandidates(target_ID);
                                                                        String remoteSdp = (String) sdp.get("sdp");
                                                                        String type = (String) sdp.get("type");
                                                                        SessionDescription.Type typee = Enum.valueOf(SessionDescription.Type.class, type.toUpperCase());
                                                                        SessionDescription sessionDescription = new SessionDescription(typee, remoteSdp);
                                                                        mainActivity.onRemoteDescription(sessionDescription, target_ID);
                                                                    } catch (JSONException e) {
                                                                        e.printStackTrace();
                                                                    }

                                                                    // 音频公聊上麦
                                                                } else if (eventType.equals("_mic_audio_on")) {
                                                                    //{"event":"_mic_audio_on","sender_ID":"1","target_ID":"所有人","sender_NAME":"1","media_TYPE":"公开/私聊","data":{"message":"申请音频上麦"}}
                                                                    try {
                                                                        JSONObject json = new JSONObject(payload);
                                                                        String clientID = (String) json.get("sender_ID");
                                                                        String clientName = (String) json.get("sender_NAME");
                                                                        String media_TYPE = (String) json.get("media_TYPE");
                                                                        if ("公开".equals(media_TYPE)) {// 音频公聊
                                                                            HashMap<String, String> packs = new HashMap<>();
                                                                            packs.put(clientID, clientName);
                                                                            micLists.add(packs);
                                                                            removeElement(userLists, clientID);
                                                                        } else {// 音频私聊
                                                                            HashMap<String, String> packs = new HashMap<>();
                                                                            packs.put(clientID, clientName);
                                                                            micLists.add(packs);
                                                                            removeElement(userLists, clientID);
                                                                        }
                                                                        Log.d(TAG, "micLists===" + micLists.toString());
                                                                        Log.d(TAG, "userLists===" + micLists.toString());
                                                                        // 更新到麦序列表
                                                                        mainActivity.setData(micLists, userLists);
                                                                    } catch (JSONException e) {
                                                                        e.printStackTrace();
                                                                    }
                                                                    // 音频下麦操作
                                                                } else if (eventType.equals("_mic_audio_down")) {

                                                                    //{"event":"_mic_audio_down","sender_ID":"1","target_ID":"所有人","sender_NAME":"1","data":{"message":"申请音频下麦"}}
                                                                    try {
                                                                        JSONObject json = new JSONObject(payload);
                                                                        String clientID = (String) json.get("sender_ID");
                                                                        if (audioOfferType.equals("_offer_audio") || testAudioOfferType.equals("_me_offer_audio")) {
                                                                            if (MyApplication.peers.containsKey(clientID)) {
                                                                                PeerConnection currentPeer = MyApplication.peers.get(clientID);
                                                                                currentPeer.close();
                                                                                currentPeer = null;
                                                                                MyApplication.peers.remove(clientID);
                                                                            }
                                                                        }
                                                                        if (mainActivity.getMicOnDescriptions().containsKey(clientID)) {
                                                                            if (mainActivity.getMicOnDescriptions().get(clientID).equals("已私聊")) {
                                                                                if (MyApplication.peers.containsKey(clientID)) {
                                                                                    PeerConnection currentPeer = MyApplication.peers.get(clientID);
                                                                                    currentPeer.close();
                                                                                    currentPeer = null;
                                                                                    MyApplication.peers.remove(clientID);
                                                                                }
                                                                            }
                                                                        }
                                                                        String clientName = (String) json.get("sender_NAME");
                                                                        // 更新到麦序列表
                                                                        if (isContain(micLists, clientID)) {
                                                                            Log.d(TAG, "包含--" + clientID);
                                                                            removeElement(micLists, clientID);
                                                                        }

                                                                        HashMap<String, String> packs = new HashMap<>();
                                                                        packs.put(clientID, clientName);
                                                                        userLists.add(packs);
                                                                        Log.d(TAG, "xia micLists===" + micLists.toString());
                                                                        Log.d(TAG, "xia userLists===" + micLists.toString());
                                                                        mainActivity.setData(micLists, userLists);
                                                                    } catch (JSONException e) {
                                                                        e.printStackTrace();
                                                                    }

                                                                    // 音频私聊操作
                                                                }
                        }

                        @Override
                        public void onClose(int code, String reason) {
                            Log.d(TAG, "Connection lost." + reason);
                        }
                    }
                    , mWebSocketOptions);
        } catch (WebSocketException e) {
            Log.d(TAG, e.toString());
        }
    }

    private void removeElement(ArrayList<HashMap<String, String>> lists, String clienID) {
        for (int j = 0; j < lists.size(); j++) {
            HashMap<String, String> innerkeyVaule = lists.get(j);
            for (String innerkey : innerkeyVaule.keySet()) {
                innerkey = innerkey.split("_")[0];
                if (clienID.equals(innerkey)) {
                    lists.remove(j);
                    break;
                }
            }
        }
    }

    /**
     * 创建对点连接对象并对sender_ID发送offer
     *
     * @param eventType offer的event类型
     * @param sender_ID 发送者的用户ID
     */
    private void createOfferPeer(String eventType, String sender_ID) {
        //创建连接对象
        mainActivity.resetPeerconnection(sender_ID);
        // 添加流
        peerConnection.addStream(sender_ID);
        // 创建ice网络候选集合
        peerConnection.setIceCandidates(sender_ID);
        // 初始化,置为发送者
        mainActivity.setIinitiator("1");
        // 发送offer
        peerConnection.createOffer(eventType, sender_ID);
    }

    /**
     * 开启心跳包，每一秒发送一次消息，如果返回lost再重连
     */
    public void sendHB() {
        mTimer.schedule(mTimerTask, 250000, 250000);
        //每次发送心跳包，服务器接收到响应就会返回一个值，如果超过25s还没有收到响应，那么就判定是断网。
        if ((System.currentTimeMillis() - nowdate) > 25000 && nowdate != 0) {
            mWebSocketConnection.disconnect();
        }
        mTimer.cancel();
        connect(mainActivity, peerConnection);
        Log.i(TAG, "" + System.currentTimeMillis() + "nowdate:" + nowdate + mWebSocketConnection.isConnected());
        return;
    }

    public boolean isContain(ArrayList<HashMap<String, String>> lists, String testID) {
        boolean isContain = false;
        for (int i = 0; i < lists.size(); i++) {
            HashMap<String, String> keyVaule = lists.get(i);
            for (String key : keyVaule.keySet()) {
                key = key.split("_")[0];
                if (testID.equals(key)) { //包含
                    isContain = true;
                    return isContain;
                } else {
                    isContain = false;
                }
            }
        }
        return isContain;
    }


    //向服务器发送信息调用
    public void sendMessage(String sendID, String senderName, final String data, String message) {
        HashMap<String, String> map = new HashMap<>();
        map.put(senderName, message);
        chatLists.clear();
        chatLists.put(sendID, map);
        mainActivity.setData(chatLists);
        mWebSocketConnection.sendTextMessage(data);
    }

    //向服务器发送信息调用
    public void sendMessage(final String data) {
        mWebSocketConnection.sendTextMessage(data);
    }

    /**
     * 调整链接是否超时的时间限制
     */
    public void setwebsocketoptions() {
        mWebSocketOptions.setSocketConnectTimeout(30000);
        mWebSocketOptions.setSocketReceiveTimeout(10000);
    }

    public long getCurrentTime() {
        return System.currentTimeMillis();
    }

    // 退出时关闭socket
    public void close() {
        mWebSocketConnection.disconnect();
    }

}
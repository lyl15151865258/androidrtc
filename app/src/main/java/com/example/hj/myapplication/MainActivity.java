package com.example.hj.myapplication;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.hj.myapplication.Bean.RoomConnectionParameters;
import com.example.hj.myapplication.webrtc.UnhandledExceptionHandler;
import com.example.hj.myapplication.webrtc.WsSocketutils;
import com.example.hj.myapplication.webrtc.PeerConnectionClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import butterknife.ButterKnife;
import butterknife.InjectView;


public class MainActivity extends Activity implements PeerConnectionClient.PeerConnectionEvents {//, SurfaceHolder.Callback
    // 开始视频
    @InjectView(R.id.bt_start_video)
    Button start_video;
    // 连接房间
    @InjectView(R.id.bt_connect)
    Button start_connect;
    // 麦序列表
    @InjectView(R.id.tv_mic_list)
    TextView tvMicList;
    // 用户列表
    @InjectView(R.id.tv_user_list)
    TextView tvUserList;
    // 文字聊天列表
    @InjectView(R.id.tv_chat_list)
    TextView tvChatList;
    // 列表展示
    @InjectView(R.id.fl_message_list)
    ListView flMessageList;
    // 消息标编辑框
    @InjectView(R.id.et_chat_message)
    EditText etChatMessage;
    // 发送
    @InjectView(R.id.bt_send)
    Button btSend;
    // 选择用户列表
    @InjectView(R.id.tv_choice)
    TextView tvChoice;
    // 选择是否私聊
    @InjectView(R.id.cb_is_private)
    CheckBox cbIsPrivate;
    // 房主视频
    @InjectView(R.id.sv_master)
    SurfaceViewRenderer sfAdminRender;
    // 自己视频
    @InjectView(R.id.sv_self)
    SurfaceViewRenderer sfLocalRender;
    private WsSocketutils wsSocketutils;
    private long callStartedTimeMs = 0;
    private boolean initiator = true;
    private String TAG = "MainActivity";
    private boolean iceConnected;
    private PeerConnectionClient peerConnectionClient;
    private RoomConnectionParameters roomConnectionParameters;
    private boolean isError;
    private boolean activityRunning;
    private boolean commandLineRun;
    private EglBase rootEglBase;
    private boolean loopback = false;
    private boolean micEnabled = true;
    private String thisUserId;
    private String thisUserName;
    private String offerSignalling;
    private Boolean isStartVideo = false;
    /**
     * 客户端ID集合
     */
    public List<String> clientIDs = new ArrayList<>();
    /**
     * 是否是管理员
     */
    private String hasAdmin;
    /**
     * 麦序列表集合
     */
    public ArrayList<HashMap<String, String>> micLists = new ArrayList<>();
    /**
     * 用户列表
     */
    public ArrayList<HashMap<String, String>> userLists = new ArrayList<>();
    /**
     * 聊天信息列表
     */
    private List<HashMap<String, String>> chatLists = new ArrayList<>();
    /**
     * 聊天用户的id集合
     */
    private List<String> chatIDs = new ArrayList<>();
    /**
     * 邀请列表集合
     */
    public List<String> invites = new ArrayList<>();
    private MyAdapter myAdapter;
    // 默认选中麦序列表
    private String clickStatus = "mic";
    /**
     * 客户端ID的集合
     */
    private List<String> clientNames;
    private ArrayAdapter adapter;
    /**
     * 0 代表 未连接状态 1 代表 连接状态
     */
    private int CONNECT_STATUS = 0;
    /**
     * 同意视频公聊的用户量
     */
    private int agreeVideoCount = 0;
    /**
     * 用于渲染界面的辅助类
     */
    private static final SparseIntArray orientations = new SparseIntArray();//手机旋转对应的调整角度
    /**
     * 是否退出
     */
    private static Boolean isExit = false;
    /**
     * 聊天用户列表的数组集合
     */
    private String[] chatUsers;
    /**
     * 记录目标用户
     */
    private String sendTargetName = "用户列表";
    /**
     * 记录当前的视频请求类型
     */
    private String currentVideoType;
    /**
     * 是否可以上麦
     */
    private Boolean isCanOnMic = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));
        setWindow();
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);
        initViews();


        WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        int width = wm.getDefaultDisplay().getWidth();
        int height = wm.getDefaultDisplay().getHeight();

        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) sfLocalRender.getLayoutParams();
        layoutParams.width = (int) (width * 0.4);
        layoutParams.height = (int) (height * 0.25);
        sfLocalRender.setLayoutParams(layoutParams);
        // --- 获取连接房间参数
        Intent intent = getIntent();
        roomConnectionParameters = (RoomConnectionParameters) intent.getSerializableExtra("roomConnectionParameters");
        if (roomConnectionParameters.userid.equals(roomConnectionParameters.adminid)) {
            hasAdmin = "1";
        } else {
            hasAdmin = "0";
        }


        thisUserId = roomConnectionParameters.userid;
        thisUserName = roomConnectionParameters.username;
        if (roomConnectionParameters != null) {
            wsSocketutils = new WsSocketutils(roomConnectionParameters);
        } else {
            Toast.makeText(this, "failure", Toast.LENGTH_SHORT).show();
        }
        // 创建视频渲染器
        rootEglBase = EglBase.create();
        sfLocalRender.init(rootEglBase.getEglBaseContext(), null);
        sfAdminRender.init(rootEglBase.getEglBaseContext(), null);
        sfLocalRender.setZOrderMediaOverlay(true);
        sfAdminRender.setZOrderMediaOverlay(true);
        // 创建连接客户端和连接参数
        peerConnectionClient = PeerConnectionClient.getInstance();
        iceConnected = false;
        commandLineRun = false;
        peerConnectionClient.createPeerConnectionFactory(roomConnectionParameters, MainActivity.this, MainActivity.this);
        // 连接房间服务器
        start_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectToRoom();
            }
        });
        // 发送聊天信息(普通消息和私聊消息)
        btSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = etChatMessage.getText().toString().trim();//hello
                String newTargetName = tvChoice.getText().toString();
                //@私聊
                if (cbIsPrivate.isChecked()) {//私聊
                    // 私聊消息
                    // <label style='color:blue;'>@3:</label><p>sfjskf</p><p><br/></p>
                    //"<label style='color:red;'>悄悄对_"+($('#textUserList option:selected').html()) +"_说:</label>"+ html
                    if (!newTargetName.equals("用户列表")) {
                        String targetID = "";

                        for (int i = 0; i < micLists.size(); i++) {
                            HashMap<String, String> keyVaule = micLists.get(i);
                            for (String key : keyVaule.keySet()) {
                                String clientName = keyVaule.get(key);
                                if (clientName.equals(newTargetName)) {
                                    targetID = key;
                                    break;
                                }
                            }
                        }
                        for (int i = 0; i < userLists.size(); i++) {
                            HashMap<String, String> keyVaule = userLists.get(i);
                            for (String key : keyVaule.keySet()) {
                                String clientName = keyVaule.get(key);
                                if (clientName.equals(newTargetName)) {
                                    targetID = key;
                                    break;
                                }
                            }
                        }

                        String selfPrivateMessage = "悄悄对_" + newTargetName + "_说:" + message;
                        String sendPrivateMessage = "<label style='color:red;'>悄悄对_" + newTargetName + "_说:</label>" + message;
                        String chatPrivateMessage = "{\"event\":\"_textChat_message\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\"" + targetID + "\",\"sender_NAME\":\"" + thisUserName + "\",\"data\":{\"message\":\"" + sendPrivateMessage + "\"}}";
                        wsSocketutils.sendMessage(thisUserId, newTargetName, chatPrivateMessage, selfPrivateMessage);
                    } else {
                        Toast.makeText(MainActivity.this, "请选择您要私聊的对象！", Toast.LENGTH_SHORT).show();
                    }

                } else { //普通公聊，@公聊
                    String selfMessage = message;
                    String sendMessage;
                    //普通消息
                    // <p>sfjskf<br/></p>
                    if (!newTargetName.equals("用户列表")) {
                        selfMessage = "@" + newTargetName + ":" + message;
                        message = "<label style='color:blue;'>@" + newTargetName + ":</label>" + message;
                        String chatMessage = "{\"event\":\"_textChat_message\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\"所有人\",\"sender_NAME\":\"" + thisUserName + "\",\"data\":{\"message\":\"" + message + "\"}}";
                        wsSocketutils.sendMessage(thisUserId, newTargetName, chatMessage, selfMessage);
                    } else {
                        String chatMessage = "{\"event\":\"_textChat_message\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\"所有人\",\"sender_NAME\":\"" + thisUserName + "\",\"data\":{\"message\":\"" + message + "\"}}";
                        wsSocketutils.sendMessage(thisUserId, thisUserName, chatMessage, selfMessage);
                    }
                }
                etChatMessage.setText("");
            }
        });
        // 默认选中麦序列表，更新状态
        tvMicList.setTextColor(Color.WHITE);
        // 更新麦序列表
        tvMicList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if ("mic".equals(clickStatus)) {
                    // 不点击
                    tvMicList.setClickable(false);
                } else {
                    clickStatus = "mic";
                    Log.d(TAG, "tvMicList===");
                    tvMicList.setTextColor(Color.WHITE);
                    tvUserList.setTextColor(Color.BLACK);
                    tvUserList.setClickable(true);
                    tvChatList.setTextColor(Color.BLACK);
                    tvChatList.setClickable(true);
                    updateList(micLists);
                }
            }
        });
        // 更新用户列表
        tvUserList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if ("user".equals(clickStatus)) {
                    // 不点击
                    tvUserList.setClickable(false);
                } else {
                    clickStatus = "user";
                    tvUserList.setTextColor(Color.WHITE);
                    tvMicList.setTextColor(Color.BLACK);
                    tvMicList.setClickable(true);
                    tvChatList.setTextColor(Color.BLACK);
                    tvChatList.setClickable(true);
                    Log.d(TAG, "tvUserList===");
                    updateList(userLists);
                }
            }
        });
        // 更新聊天列表
        tvChatList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if ("chat".equals(clickStatus)) {
                    // 不点击
                    tvChatList.setClickable(false);
                } else {
                    clickStatus = "chat";
                    Log.d(TAG, "tvChatList===");
                    tvChatList.setTextColor(Color.WHITE);
                    tvMicList.setTextColor(Color.BLACK);
                    tvMicList.setClickable(true);
                    tvUserList.setTextColor(Color.BLACK);
                    tvUserList.setClickable(true);
                    Log.d(TAG, chatLists.toString());
                    updateChatList(chatLists);
                }
            }
        });

    }

    public List<String> getInvites() {
        Log.e("getInvites", invites.toString());
        return invites;
    }

    // 更新聊天列表
    private void updateChatList(List<HashMap<String, String>> chatLists) {
        if (adapter == null) {
            adapter = new MyArrayAdapter(this, 0, chatLists);
        }
        flMessageList.setAdapter(adapter);
        if (chatLists.size() > 1) {
            flMessageList.setSelection(chatLists.size() - 1);
        }
    }

    // 设置聊天信息
    public void setData(HashMap<String, HashMap<String, String>> chatList) {
        // 封装信息
        for (String key : chatList.keySet()) {
            chatIDs.add(key);
            HashMap<String, String> chat = chatList.get(key);
            chatLists.add(chat);
        }
        if ("chat".equals(clickStatus)) {
            updateChatList(chatLists);
        }
    }

    public String getCurrentTime() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        Date curDate = new Date(System.currentTimeMillis());//获取当前时间
        return formatter.format(curDate);
    }

    public String getCurrentVideoType() {
        return currentVideoType;
    }

    /**
     * 隐藏控件，移除渲染
     */
    public void hideViewRender(String index) {
        Log.d(TAG, "隐藏" + index);
        if ("1".equals(index)) {
            sfAdminRender.setVisibility(View.INVISIBLE);
        }
        if ("2".equals(index)) {
            sfLocalRender.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * 显示控件，添加渲染效果
     */
    public void showViewRender(String index) {
        if ("1".equals(index)) {
            sfAdminRender.setVisibility(View.VISIBLE);
        }
        if ("2".equals(index)) {
            sfLocalRender.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 同意私聊上麦
     */
    public void agreePrivateVideo(String clientID) {
        Log.d(TAG, "agree_private_video");
        // 创建连接对象
        createPeerToPeer(clientID, "_offer_agree_video_private");
        initiator = true;
    }

    /**
     * 创建peer对象,创建offer/answer
     */
    public void createPeerToPeer(String clientID, String eventType) {
        resetPeerconnection(clientID);
        if (isStartVideo) {
            peerConnectionClient.addStream(clientID);
        } else {
            peerConnectionClient.isAddStream(clientID);
            isStartVideo = true;
        }
        peerConnectionClient.setIceCandidates(clientID);
        peerConnectionClient.createOffer(eventType, clientID);
    }

    /**
     * 房主开启视频，发送_offer_start_media，请求建立连接
     */
    public void requestP2P() {
        Log.d(TAG, "requestP2P");
        if (CONNECT_STATUS != 1) {
            Toast.makeText(MainActivity.this, "请先登录房间服务器！！", Toast.LENGTH_SHORT).show();
            return;
        }
        if (peers.size() == 0) {
            Log.d(TAG, "本地渲染---");
            resetPeerconnection(thisUserId);
            peerConnectionClient.isAddStream(thisUserId);
            //peerConnectionClient.renderLocal();
        } else {
            String cliend_ID;
            //开始连接视频聊天,向其他用户发送请求
            for (int i = 0; i < peers.size(); i++) {
                cliend_ID = peers.get(i);
                // 创建连接对象
                resetPeerconnection(cliend_ID);
                if (i > 0) {
                    peerConnectionClient.addStream(cliend_ID);
                } else {
                    peerConnectionClient.isAddStream(cliend_ID);
                }
                peerConnectionClient.setIceCandidates(cliend_ID);

                peerConnectionClient.createOffer("_offer_start_media", cliend_ID);
                initiator = true;
            }
        }
    }

    /**
     * 连接到房间
     */
    public void connectToRoom() {
        wsSocketutils.connect(MainActivity.this, peerConnectionClient);
        callStartedTimeMs = System.currentTimeMillis();
        CONNECT_STATUS = 1;
    }

    /**
     * 创建peer实例
     *
     * @param cliend_ID 客户端ID
     * @return
     */
    public PeerConnection createPeerConnection(String cliend_ID) {
        return peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(), sfLocalRender, sfAdminRender, cliend_ID);
    }

    /**
     * 重新创建peer实例
     *
     * @param cliend_ID 客户端ID
     */
    public void resetPeerconnection(String cliend_ID) {
        Log.d(TAG, "开始连接视频聊天客户端ID-----" + cliend_ID + "thisUserId==" + thisUserId);
        if (MyApplication.peers.size() > 0) {
            if (MyApplication.peers.containsKey(cliend_ID)) {
                PeerConnection connection = MyApplication.peers.get(cliend_ID);
                connection.close();
                connection = null;
                MyApplication.peers.remove(cliend_ID);

                PeerConnection connectionw = createPeerConnection(cliend_ID);
                MyApplication.peers.put(cliend_ID, connectionw);
            } else {
                PeerConnection connection = createPeerConnection(cliend_ID);
                MyApplication.peers.put(cliend_ID, connection);
                Log.d(TAG, "当前客户端ID为" + cliend_ID + "PeerConnection对象 " + connection.toString());
            }
            if (MyApplication.peers.containsKey(thisUserId)) {
                PeerConnection selfConn = MyApplication.peers.get(thisUserId);
                if (selfConn != null) {
                    selfConn.close();
                    selfConn = null;
                }
                MyApplication.peers.remove(thisUserId);
            }
        } else {
            PeerConnection connection = createPeerConnection(cliend_ID);
            Log.d(TAG, "当前客户端ID为" + cliend_ID + "PeerConnection对象 " + connection.toString());
            MyApplication.peers.put(cliend_ID, connection);
        }
        Log.d(TAG, "PeerConnection对象--" + MyApplication.peers.toString());
    }

    /**
     * 申请公聊上麦
     */
    public void micVideoOn() {
        // 房主发送_offer_agree_video（同意公聊） 用户回answer
        final String mic_video_on = "{\"event\":\"_mic_video_on\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\"所有人\",\"sender_NAME\":\"" + thisUserName + "\",\"media_TYPE\":\"公开\",\"data\":{\"message\":\"申请视频上麦\"}}";
        new Thread(new Runnable() {
            @Override
            public void run() {
                wsSocketutils.sendMessage(mic_video_on);
            }
        }).start();

    }

    private boolean logoutFlag = false;

    /**
     * 设置是否下线
     *
     * @param logoutFlag true 标记为下线 false 标记为在线
     */
    public void setLogoutFlag(boolean logoutFlag) {
        this.logoutFlag = logoutFlag;
    }

    public boolean getLogoutFlag() {
        return logoutFlag;
    }

    /**
     * 是否正在公聊上麦
     *
     * @return 布尔值 true false
     */
    public Boolean getIsStartVideo() {
        return isStartVideo;
    }

    /**
     * 获取公聊上麦的人数
     *
     * @return 公聊上麦的人数
     */
    public int getAgreeVideoCount() {
        return agreeVideoCount;
    }

    /**
     * 申请下麦
     */
    public void micVideoDown() {
        final String mic_video_down = "{\"event\":\"_mic_video_down\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\"所有人\",\"sender_NAME\":\"" + thisUserName + "\",\"data\":{\"message\":\"申请视频下麦\"}}";
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 下线通知
                setLogoutFlag(true);
                wsSocketutils.sendMessage(mic_video_down);
            }
        }).start();

    }

    /**
     * 申请私聊上麦
     */
    public void micVideoOnPrivate() {
        Log.d(TAG, "申请上麦。。。。。");
        String mic_video_on_Private = "{\"event\":\"_mic_video_on\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\"所有人\",\"sender_NAME\":\"" + thisUserName + "\",\"media_TYPE\":\"私聊\",\"data\":{\"message\":\"申请视频上麦\"}}";
        wsSocketutils.sendMessage(mic_video_on_Private);
    }

    /**
     * 强制用户下麦
     */
    public void forbidOnMic(String tragetID) {
        // {"event":"_forbidOnMic","sender_ID":"2","target_ID":1,"data":{"message":"您已被房主禁止文字发言和音视频上麦!"}}
        Log.d(TAG, "强制用户下麦。。。。。");
        String mic_video_forbidOnMic = "{\"event\":\"_forbidOnMic\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\"" + tragetID + "\",\"data\":{\"message\":\"您已被房主禁止文字发言和音视频上麦!\"}}";
        wsSocketutils.sendMessage(mic_video_forbidOnMic);
    }

    /**
     * 同意音频私聊上麦
     *
     * @param currentClient
     */
    private void agreeMicPrivate(String currentClient) {
        Log.d(TAG, "_offer_agree_audio_private");
        // 创建连接对象
        createPeerToPeer(currentClient, "_offer_agree_audio_private");
        initiator = true;
    }

    /**
     * 用户下线通知
     */
    public void offline() {
        String _offline = "{\"event\":\"_offline\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\"server\",\"media_TYPE\":\"公开\",\"data\":{\"message\":\"正常退出会议室\"}}";
        wsSocketutils.sendMessage(_offline);
    }

    /**
     * 发送offer
     *
     * @param sdp       描述信息
     * @param offerType 请求的类型
     * @param client_ID 客户端ID
     */
    public void sendOffer(SessionDescription sdp, String offerType, String client_ID) {
        initiator = true;
        String localSdp = sdp.description;
        String type = sdp.type.canonicalForm();
        //去除换行
        String offersdp = localSdp.replace("\r\n", "\\r\\n");
        Log.d(TAG, offersdp);
        offerSignalling = "{\"event\":\"" + offerType + "\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\"" + client_ID + "\",\"data\":{\"sdp\":{\"type\":\"" + type + "\",\"sdp\":\"" + offersdp + "\"}}}";
        wsSocketutils.sendMessage(offerSignalling);
        if (loopback) {
            // 在回送模式下，重新命名此呼叫以回复并将其路由。
            SessionDescription sdpAnswer = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm("answer"),
                    sdp.description);
            onRemoteDescription(sdpAnswer, client_ID);
        }

    }

    private int count = 0;

    /**
     * 发送answer
     *
     * @param sdp       描述信息
     * @param client_ID 客户端ID
     */
    public void sendAnswer(SessionDescription sdp, String client_ID) {
        Log.d(TAG, "开始发送answer" + count);
        initiator = false;
        String localSdp = sdp.description;
        String type = sdp.type.canonicalForm();
        try {
            // 去除换行
            String answersdp = localSdp.replace("\r\n", "\\r\\n");
            String answerSignalling = "{\"event\":\"_answer\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\"" + client_ID + "\",\"data\":{\"sdp\":{\"type\":\"" + type + "\",\"sdp\":\"" + answersdp + "\"}}}";
            wsSocketutils.sendMessage(answerSignalling);
            Log.d(TAG, "answer发送完成" + thisUserId + client_ID);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "count======" + count++);
    }

    /**
     * 其他用户的客户端ID集合
     */
    private List<String> peers = new ArrayList<>();

    /**
     * 其他用户的客户端名称集合
     */
    private List<String> peerNames = new ArrayList<>();

    /**
     * 获取其他用户的客户端ID
     *
     * @return
     */
    public List<String> getPeers() {
        return peers;
    }

    /**
     * 重载方法,设置麦序列表数据和用户列表数据
     *
     * @param micLists  麦序列表集合
     * @param userLists 用户列表集合
     */
    public void setData(ArrayList<HashMap<String, String>> micLists, ArrayList<HashMap<String, String>> userLists) {
        this.micLists = micLists;
        this.userLists = userLists;
        for (int i = 0; i < userLists.size(); i++) {
            HashMap<String, String> keyVaule = userLists.get(i);
            for (String key : keyVaule.keySet()) {
                String clientName = keyVaule.get(key);
                if (!peers.contains(key)) {
                    if (!thisUserId.equals(key)) {
                        if (!peerNames.contains(clientName)) {
                            peerNames.add(clientName);
                        }
                    }
                    peers.add(key);
                }
            }
        }
        for (int i = 0; i < micLists.size(); i++) {
            HashMap<String, String> keyVaule = micLists.get(i);

            for (String key : keyVaule.keySet()) {
                String clientName = keyVaule.get(key);
                Log.d("key--", key);
                if (key.endsWith("_公")) {
                    key = key.substring(0, key.indexOf("_公"));
                    Log.d("key----", key);
                }
                if (key.endsWith("_私")) {
                    key = key.substring(0, key.indexOf("_私"));
                    Log.d("key", key);
                }
                if (!thisUserId.equals(key)) {
                    if (!peerNames.contains(clientName)) {
                        peerNames.add(clientName);
                    }
                }
                if (!peers.contains(key)) {
                    if (!key.equals(roomConnectionParameters.adminid)) {
                        peers.add(key);
                    }
                }
            }
        }
        Log.d(TAG, "更新麦序列表=====" + micLists);
        Log.d(TAG, "更新用户列表=====" + userLists);
        Log.d(TAG, "更新其他用户idpeers=====" + peers);
        Log.d(TAG, "更新其他用户名称=====" + peerNames);
        if ("mic".equals(clickStatus)) {
            updateList(micLists);
        }
        if ("user".equals(clickStatus)) {
            updateList(userLists);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    public void downVideoMic() {
        isCanOnMic = false; //标记是房主踢你下麦
        micVideoDown();
    }

    public final class ViewHolder {
        public TextView textView;
        public Button agree_video;
        public Button agree_private_video;
        public Button bt_invite;
        public Button bt_down;
        public Button bt_agree_audio;
    }

    private boolean isExistClientId = false;

    public void setAgreeVideoCount(int count) {
        agreeVideoCount = count;
        Log.e("agreeVideoCount+++", agreeVideoCount + "===123");
    }

    private HashMap<String, String> micOnDescriptions = new HashMap<>();

    public HashMap<String, String> getMicOnDescriptions() {
        return micOnDescriptions;
    }

    /*
     * 麦序列表和用户列表适配器
     */
    class MyAdapter extends BaseAdapter {

        private LayoutInflater mInflater;
        private List<String> data;

        public MyAdapter(Context context, List<String> data) {
            this.mInflater = LayoutInflater.from(context);
            this.data = data;
            Log.e("DATA", data.toString());
        }

        @Override
        public int getCount() {
            if (data.size() > 0) {
                return data.size();
            }
            return 0;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView = mInflater.inflate(R.layout.item_list, null);
                holder.textView = (TextView) convertView
                        .findViewById(R.id.tv_username);
                holder.agree_video = (Button) convertView
                        .findViewById(R.id.bt_agree_video);
                holder.agree_private_video = (Button) convertView.findViewById(R.id.bt_agree_private_video);
                holder.bt_invite = (Button) convertView.findViewById(R.id.bt_invite);
                holder.bt_down = (Button) convertView.findViewById(R.id.bt_down);
                holder.bt_agree_audio = (Button) convertView.findViewById(R.id.bt_agree_audio);
                // 音频私聊
                holder.bt_agree_audio.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if ("0".equals(hasAdmin)) { // 用户操作
                            if (!micOnDescriptions.containsKey(clientIDs.get(position))) {// 不在麦序列表中
                                String currentClient = clientIDs.get(position); // 当前音频上麦的用户ID
                                if (currentClient.endsWith("私")) {
                                    currentClient = currentClient.split("_")[0];
                                }
                                for (int i = 0; i < micLists.size(); i++) {
                                    HashMap<String, String> keyVaule = micLists.get(i);
                                    for (String key : keyVaule.keySet()) {
                                        if (!currentClient.equals(key)) {
                                            isExistClientId = true;
                                            break;
                                        } else {
                                            isExistClientId = false;
                                        }
                                    }
                                }
                                Log.d(TAG, "isExistClientId---" + isExistClientId);
                                if (isExistClientId) { // 当前麦序列表中不存在
                                    micOnDescriptions.put(currentClient, "私聊");
                                    //{"event":"_mic_audio_on","sender_ID":"3","target_ID":"所有人","sender_NAME":"3","media_TYPE":"私聊/公开","data":{"message":"申请音频上麦"}}
                                    String micPrivateOffer = "{\"event\":\"_mic_audio_on\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\"所有人\",\"sender_NAME\":\"" + thisUserName + "\",\"media_TYPE\":\"私聊\",\"data\":{\"sdp\":{\"message\":\"申请音频上麦\"}}}";
                                    wsSocketutils.sendMessage(micPrivateOffer);
                                    Log.d(TAG, "data---" + data);
                                    String clientName = data.get(position);
                                    int removeUserIndex = 0;
                                    for (int j = 0; j < userLists.size(); j++) {
                                        HashMap<String, String> innerkeyVaule = userLists.get(j);
                                        for (String innerkey : innerkeyVaule.keySet()) {
                                            if (currentClient.equals(innerkey)) {
                                                removeUserIndex = j;
                                                break;
                                            }
                                        }
                                    }
                                    userLists.remove(removeUserIndex);
                                    HashMap<String, String> mHashMap = new HashMap<>();
                                    mHashMap.put(currentClient, clientName);
                                    // 麦序列表更新数据
                                    micLists.add(mHashMap);
                                    clientIDs.remove(position);
                                    clientNames.remove(clientName);
                                }
                                if (myAdapter != null) {
                                    myAdapter.notifyDataSetChanged();
                                }
                                isExistClientId = true;
                            } else {// 在麦序列表中
                                String currentClient = clientIDs.get(position); // 当前音频上麦的用户ID
                                if (currentClient.endsWith("私")) {
                                    currentClient = currentClient.split("_")[0];
                                }
                                String micPrivateOffer = "{\"event\":\"_mic_audio_down\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\"所有人\",\"sender_NAME\":\"" + thisUserName + "\",\"data\":{\"sdp\":{\"message\":\"申请音频下麦\"}}}";
                                wsSocketutils.sendMessage(micPrivateOffer);
                                // 释放自己的渲染
                                String clientName = data.get(position);
                                Log.d(TAG, "下麦===" + clientName + "---" + currentClient);
                                clientIDs.remove(currentClient);
                                clientNames.remove(clientName);
                                micOnDescriptions.remove(currentClient);
                                int removeUserIndex = 0;
                                for (int j = 0; j < micLists.size(); j++) {
                                    HashMap<String, String> innerkeyVaule = micLists.get(j);
                                    for (String innerkey : innerkeyVaule.keySet()) {
                                        if (currentClient.equals(innerkey)) {
                                            removeUserIndex = j;
                                            break;
                                        }
                                    }
                                }
                                micLists.remove(removeUserIndex);
                                HashMap<String, String> mHashMap = new HashMap<>();
                                mHashMap.put(currentClient.split("_")[0], clientName);
                                userLists.add(mHashMap);

                                if (myAdapter != null) {
                                    myAdapter.notifyDataSetChanged();
                                }
                            }
                        } else {// 房主处理--同意公聊/ 私聊
                            String currentClient = clientIDs.get(position); // 当前音频上麦的用户ID
                            if (currentClient.endsWith("私")) {
                                currentClient = currentClient.split("_")[0];
                            }
                            if (micOnDescriptions.containsKey(currentClient)) {
                                String media_TYPE = micOnDescriptions.get(currentClient);
                                if ("私聊".equals(media_TYPE)) {
                                    micOnDescriptions.put(currentClient, "已私聊");
                                    agreeMicPrivate(currentClient);
                                }
                            }

                        }

                    }

                });
                // 房主权限，强制用户下麦
                holder.bt_down.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String micClientID = clientIDs.get(position);
                        micClientID = micClientID.split("_")[0];

                        if (invites.size() > 0) { // 正在私聊
                            forbidOnMic(micClientID);
                        } else { // 只是上麦
                            forbidOnMic(micClientID);
                        }
                        Log.e("micClientID:", micClientID.toString());
                    }
                });
                // 邀请逻辑 私聊一对一
                holder.bt_invite.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if ("1".equals(hasAdmin)) {
                            if (agreeVideoCount == 0) {
                                holder.bt_invite.setClickable(true);
                            }
                            Log.e("agreeVideoCount--", getAgreeVideoCount() + "");
                            if (getAgreeVideoCount() >= 1) {
                                Toast.makeText(MainActivity.this, "正在与他人私聊中！！", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String micClientID = clientIDs.get(position);
                            Log.e("同意邀请私聊", micClientID);
                            // 在麦序列表中
                            for (int i = 0; i < micLists.size(); i++) {
                                HashMap<String, String> keyVaule = micLists.get(i);
                                for (String key : keyVaule.keySet()) {
                                    if (micClientID.equals(key)) {
                                        Log.e("同意邀请私聊", "同意邀请私聊");
                                        if (isStartVideo) {
                                            agreePrivateVideo(micClientID.split("_")[0]);
                                            currentVideoType = "private";
                                            agreeVideoCount = agreeVideoCount + 1;
                                            Log.e("agreeVideoCount----", getAgreeVideoCount() + "");
                                            holder.agree_video.setClickable(false);
                                            holder.bt_invite.setClickable(false);
                                            holder.bt_invite.setTextColor(Color.RED);
                                            //添加到邀请列表集合中
                                            invites.add(micClientID);
                                            break;
                                        } else {
                                            Toast.makeText(MainActivity.this, "请先开启视频！", Toast.LENGTH_SHORT).show();
                                            return;
                                        }

                                    }
                                }
                            }
                        } else {
                            String currentClientID = clientIDs.get(position);
                            if (currentClientID.endsWith("公")) {
                                currentClientID = currentClientID.split("_")[0];
                            }
                            Log.d(TAG, "currentClientID===" + currentClientID);
                            // 用户，只有自己可以操作自己的
                            Log.d(TAG, "micLists===" + micLists.toString());
                            if (thisUserId.equals(currentClientID)) {
                                String clientID = clientIDs.get(position);
                                Log.d(TAG, "clientID===" + clientID);
                                for (int i = 0; i < micLists.size(); i++) {
                                    HashMap<String, String> keyVaule = micLists.get(i);
                                    for (String key : keyVaule.keySet()) {
                                        if (!clientID.equals(key)) {
                                            isExistClientId = true;
                                            break;
                                        } else {
                                            isExistClientId = false;
                                        }
                                    }
                                }
                                Log.d(TAG, "isExistClientId---" + isExistClientId);
                                if (isExistClientId) { // 当前麦序列表中不存在
                                    if (!isCanOnMic) {   // 可以上麦
                                        Toast.makeText(MainActivity.this, "您已被房主禁止文字发言和音视频上麦!", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    //micVideoOn();
                                    Log.d(TAG, "data---" + data);
                                    String clientName = data.get(position);
                                    int removeUserIndex = 0;
                                    for (int j = 0; j < userLists.size(); j++) {
                                        HashMap<String, String> innerkeyVaule = userLists.get(j);
                                        for (String innerkey : innerkeyVaule.keySet()) {
                                            if (clientID.equals(innerkey)) {
                                                removeUserIndex = j;
                                                break;
                                            }
                                        }
                                    }
                                    userLists.remove(removeUserIndex);
                                    HashMap<String, String> mHashMap = new HashMap<>();
                                    mHashMap.put(clientID + "_公", clientName);
                                    micLists.add(mHashMap);
                                    clientIDs.remove(position);
                                    clientNames.remove(clientName);
                                }
                                if (myAdapter != null) {
                                    myAdapter.notifyDataSetChanged();
                                }
                                isExistClientId = false;
                            } else {
                                Toast.makeText(MainActivity.this, "您没有权限!!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
                holder.agree_video.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String clientName = clientNames.get(position);
                        tvChoice.setText(clientName);
                    }
                });

                // 同意私聊逻辑
                holder.agree_private_video.setOnClickListener(new View.OnClickListener() {
                    @TargetApi(Build.VERSION_CODES.KITKAT)
                    @Override
                    public void onClick(View v) {
                        if ("1".equals(hasAdmin)) {
                            String micClientID = clientIDs.get(position);
                            // 在麦序列表中
                            for (int i = 0; i < micLists.size(); i++) {
                                HashMap<String, String> keyVaule = micLists.get(i);
                                for (String key : keyVaule.keySet()) {
                                    if (micClientID.equals(key)) {
                                        Toast.makeText(MainActivity.this, "只允许邀请", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }
                        } else {
                            String currentClientID = clientIDs.get(position);
                            if (currentClientID.endsWith("私")) {
                                currentClientID = currentClientID.split("_")[0];
                            }
                            // 用户，只有自己可以操作自己的
                            if (thisUserId.equals(currentClientID)) {
                                String clientID = clientIDs.get(position);
                                if (micLists.size() < 1) {
                                    isExistClientId = true;
                                } else {
                                    for (int i = 0; i < micLists.size(); i++) {
                                        HashMap<String, String> keyVaule = micLists.get(i);
                                        for (String key : keyVaule.keySet()) {
                                            if (!clientID.equals(key)) {
                                                isExistClientId = true;
                                                break;
                                            } else {
                                                isExistClientId = false;
                                            }
                                        }
                                    }
                                }
                                Log.e("isExistClientId:", isExistClientId + "");
                                if (isExistClientId) {
                                    if (!isCanOnMic) {   // 可以上麦
                                        Toast.makeText(MainActivity.this, "您已被房主禁止文字发言和音视频上麦!", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    holder.bt_invite.setTextColor(Color.BLACK);
                                    holder.bt_invite.setClickable(true);
                                    micVideoOnPrivate();
                                    Log.e("DATA", data.toString() + "---" + data.size());
                                    if ("1".equals(hasAdmin)) {
                                        holder.agree_private_video.setClickable(false);
                                    } else {
                                        holder.agree_private_video.setClickable(true);
                                    }
                                    Log.e("DATA", position + "");
                                    String clientName = data.get(position);
                                    Log.d(TAG, "上麦用户名称===" + clientName);
                                    int removeUserIndex = 0;
                                    for (int j = 0; j < userLists.size(); j++) {
                                        HashMap<String, String> innerkeyVaule = userLists.get(j);
                                        for (String innerkey : innerkeyVaule.keySet()) {
                                            if (clientID.equals(innerkey)) {
                                                removeUserIndex = j;
                                                break;
                                            }
                                        }
                                    }
                                    HashMap<String, String> mHashMap = new HashMap<>();
                                    mHashMap.put(clientID + "_私", clientName);
                                    micLists.add(mHashMap);
                                    Log.e("DATA", "removeUserIndex:" + removeUserIndex);
                                    Log.e("DATA", "userLists:" + userLists);
                                    userLists.remove(removeUserIndex);
                                    Log.e("DATA", "userLists:" + userLists);
                                    clientIDs.remove(position);
                                    clientNames.remove(clientName);
                                } else {
                                    //下麦操作
                                    micVideoDown();
                                    hideViewRender("2");
                                    String clientName = data.get(position);
                                    String client_ID = clientIDs.get(position);
                                    clientIDs.remove(position);
                                    clientNames.remove(clientName);
                                    int removeUserIndex = 0;
                                    for (int j = 0; j < micLists.size(); j++) {
                                        HashMap<String, String> innerkeyVaule = micLists.get(j);
                                        for (String innerkey : innerkeyVaule.keySet()) {
                                            if (client_ID.equals(innerkey)) {
                                                removeUserIndex = j;
                                                break;
                                            }
                                        }
                                    }
                                    micLists.remove(removeUserIndex);
                                    HashMap<String, String> mHashMap = new HashMap<>();
                                    mHashMap.put(client_ID.split("_")[0], clientName);
                                    userLists.add(mHashMap);
                                }
                                if (myAdapter != null) {
                                    myAdapter.notifyDataSetChanged();
                                }
                                isExistClientId = false;
                            } else {
                                Toast.makeText(MainActivity.this, "您没有权限!!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            String clientName = data.get(position);
            String clientID = clientIDs.get(position);
            Log.e("clientIDs-------:", clientIDs.toString());
            if (roomConnectionParameters.adminid.equals(clientID)) {
                // 是房主 隐藏
                Log.d(TAG, "是房主---");
                holder.agree_video.setVisibility(View.GONE);
                holder.agree_private_video.setVisibility(View.GONE);
            } else {
                Log.d(TAG, "是用户---");
                holder.agree_video.setVisibility(View.VISIBLE);
                holder.agree_video.setText("@");
                holder.agree_private_video.setVisibility(View.VISIBLE);
            }
            if ("mic".equals(clickStatus)) {
                if (clientID.equals(roomConnectionParameters.adminid)) {// 是房主
                    holder.bt_agree_audio.setVisibility(View.GONE);
                }
                if (roomConnectionParameters.adminid.equals(thisUserId)) {//自己是房主
                    // 公聊上麦
                    if (clientID.endsWith("公")) {
                        holder.agree_video.setText("公");
                        holder.agree_private_video.setVisibility(View.GONE);
                    }
                    // 私聊上麦
                    if (clientID.endsWith("私")) {
                        holder.bt_down.setVisibility(View.VISIBLE);
                        holder.agree_video.setText("@");
                        //holder.agree_video.setVisibility(View.GONE);
                    }
                    holder.bt_invite.setVisibility(View.VISIBLE);
                } else { //用户
                    if (thisUserId.equals(clientID.split("_")[0])) { //是自己
                        if (clientID.endsWith("公")) {
                            holder.agree_video.setText("下麦");
                            holder.agree_private_video.setVisibility(View.GONE);
                        }
                        // 私聊上麦
                        if (clientID.endsWith("私")) {
                            holder.agree_private_video.setText("下麦");
                            holder.agree_video.setVisibility(View.GONE);
                        }
                    } else {//其他用户
                        if (clientID.endsWith("公")) {
                            holder.agree_video.setText("公");
                            holder.agree_private_video.setVisibility(View.GONE);
                        }
                        // 私聊上麦
                        if (clientID.endsWith("私")) {
                            holder.agree_private_video.setText("私");
                            holder.agree_video.setVisibility(View.GONE);
                        }
                    }
                }
                if (!micOnDescriptions.containsKey(clientID)) {
                    holder.bt_agree_audio.setVisibility(View.GONE);
                } else {
                    holder.bt_agree_audio.setVisibility(View.VISIBLE);
                }

            }
            Log.e("clientIDs-----", clientIDs.get(position) + "");
            Log.e("invites---", invites.toString() + "222");
            if (invites.contains(clientIDs.get(position))) {
                holder.bt_invite.setClickable(false);
                holder.bt_invite.setTextColor(Color.RED);
            }
            if (clientID.equals(roomConnectionParameters.adminid)) {// 房主
                holder.textView.setTextColor(Color.RED);
                holder.textView.setText(clientName);
                holder.bt_invite.setVisibility(View.GONE);
            } else {
                holder.textView.setTextColor(Color.BLACK);
                holder.textView.setText(clientName);
            }
            if (thisUserId.equals(clientID)) {
                holder.agree_video.setVisibility(View.GONE);
            }
            return convertView;
        }

    }

    public void updateList(ArrayList<HashMap<String, String>> data) {
        Log.d(TAG, "data--" + data);
        // 初始化数据
        clientIDs.removeAll(clientIDs);
        clientNames = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            HashMap<String, String> keyValue = data.get(i);
            for (String key : keyValue.keySet()) {
                String value = keyValue.get(key);
                if (roomConnectionParameters.adminid.equals(key)) {
                    // 是房主
                    clientNames.add(0, value);
                    clientIDs.add(0, key);
                } else {
                    clientIDs.add(key);
                    clientNames.add(value);
                }
            }
        }
        Log.d(TAG, "clientNames" + clientNames);
        Log.d(TAG, "clientIDs" + clientIDs);
        if (myAdapter != null) {
            myAdapter = null;
        }
        myAdapter = new MyAdapter(this, clientNames);
        flMessageList.setAdapter(myAdapter);
        flMessageList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                Toast.makeText(getApplicationContext(), "item---" + arg2, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onLocalDescription(SessionDescription sdp, String offerType, String client_ID) {
        Log.d("initiator", "initiator===" + initiator + client_ID);
        if (initiator) {
            sendOffer(sdp, offerType, client_ID);
        } else {
            sendAnswer(sdp, client_ID);
        }
    }

    @Override
    public void onIceCandidate(IceCandidate candidate, String client_ID) {
        //呼叫接收方将ICE网络候选发送到websocket服务器。
        Log.d(TAG, "start IceCandidate===" + client_ID);
        //String icecandidate = "{\"event\":\"_ice_candidate\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\""+target_ID+"\",\"data\":{\"candidate\":{\"candidate\":\"" + candidate.sdp + "\",\"sdpMid\":\"" + candidate.sdpMid + "\",\"sdpMLineIndex\":" + candidate.sdpMLineIndex + "}}}";
        final String icecandidate = "{\"event\":\"_ice_candidate\",\"sender_ID\":\"" + thisUserId + "\",\"target_ID\":\"" + client_ID + "\",\"data\":{\"candidate\":{\"candidate\":\"" + candidate.sdp + "\",\"sdpMid\":\"" + candidate.sdpMid + "\",\"sdpMLineIndex\":" + candidate.sdpMLineIndex + "}}}";
        if (initiator) {
            Log.d(TAG, initiator + "send IceCandidate success----");
            // 呼叫发起者向GAE服务器发送ICE网络候选。
            wsSocketutils.sendMessage(icecandidate);
            if (loopback) {
                Log.d(TAG, loopback + " loopback send IceCandidate success----");
                //onRemoteIceCandidate(candidate, client_ID);
            }
        } else {
            Log.d(TAG, initiator + "send IceCandidate success----");
            //呼叫接收方将ICE网络候选发送到websocket服务器。
            //onRemoteIceCandidate(candidate, client_ID);
            wsSocketutils.sendMessage(icecandidate);
        }
        Log.d(TAG, "发送candidate到" + client_ID + "端");

    }


    @Override
    public void onPeerConnectionClosed() {
        Map<String, PeerConnection> peers = MyApplication.peers;
        for (String key : peers.keySet()) {
            PeerConnection peer = peers.get(key);
            peer.close();
            peer = null;
        }

    }

    @Override
    public void onPeerConnectionError(String description) {
        reportError(description);
    }

    /**
     * 断开连接
     */
    private void disconnect() {
        activityRunning = false;
        if (peerConnectionClient != null) {
            peerConnectionClient.closeP2P();
            peerConnectionClient = null;
        }
        if (sfLocalRender != null) {
            sfLocalRender.release();
            sfLocalRender = null;
        }
        if (iceConnected && !isError) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    /**
     * 报告错误
     */
    private void reportError(final String description) {
        if (!isError) {
            isError = true;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    disconnectWithErrorMessage(description);
                }
            });

        }

    }

    private void disconnectWithErrorMessage(final String errorMessage) {
        if (commandLineRun || !activityRunning) {
            Log.e(TAG, "Critical error: " + errorMessage);
            disconnect();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(getText(R.string.channel_error_title))
                    .setMessage(errorMessage)
                    .setCancelable(false)
                    .setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            disconnect();
                        }
                    }).create().show();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        activityRunning = false;
        if (peerConnectionClient != null) {
            peerConnectionClient.stopVideoSource();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        activityRunning = true;
        if (peerConnectionClient != null) {
            peerConnectionClient.startVideoSource();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d(TAG, "onKeyDown====");
            if (CONNECT_STATUS == 1) {
                if (wsSocketutils != null) {
                    offline();
                }
                if (peerConnectionClient != null) {
                    peerConnectionClient.closeP2P();
                }
            }
            finish();
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        disconnect();
        activityRunning = false;
        rootEglBase.release();
        // 退出房间
        new Thread(new Runnable() {
            @Override
            public void run() {
                offline();
            }
        });
        //peerConnectionClient.closeP2P();
        // 关闭socket
        wsSocketutils.close();
        super.onDestroy();
    }

    public void setIinitiator() {
        Log.d(TAG, "setIinitiator----");
        initiator = false;
    }

    public void setIinitiator(String isInitiator) {
        initiator = true;
    }

    /**
     * 设置远程SDP描述并创建answer信令
     *
     * @param sdp
     * @param target_ID
     */
    public void onRemoteDescription(SessionDescription sdp, String target_ID) {
        Log.d(TAG, "target_ID---" + target_ID + "initiator---" + initiator);
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        if (peerConnectionClient == null) {
            Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
            return;
        }
        Log.d(TAG, "Received remote " + sdp.type + "delay=" + delta + "ms");
        peerConnectionClient.setRemoteDescription(sdp, target_ID);
        // 没有被引发
        if (!initiator) {
            Log.d("initiator", "Creating ANSWER..." + initiator + target_ID);
            //创建应答 应答SDP将发送给PeerConnectionEvents.onLocalDescription事件中的客户端。
            peerConnectionClient.createAnswer(target_ID);
        }

    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private SpannableStringBuilder builder;
    private ForegroundColorSpan redSpan = new ForegroundColorSpan(Color.RED);
    private ForegroundColorSpan whiteSpan = new ForegroundColorSpan(Color.WHITE);
    private ForegroundColorSpan blueSpan = new ForegroundColorSpan(Color.BLUE);

    /**
     * 聊天列表适配器
     */
    public class MyArrayAdapter extends ArrayAdapter {
        List<HashMap<String, String>> list;

        public MyArrayAdapter(Context context, int resource, List<HashMap<String, String>> objects) {
            super(context, resource, objects);
            list = objects;
        }

        @Override
        public int getCount() {
            if (list.size() > 0) {
                return list.size();
            } else {
                return 0;
            }

        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            Log.d(TAG, "position===" + position);
            String id = chatIDs.get(position);
            //发送者
            if (thisUserId.equals(id)) {
                // 代表 发送消息类型
                return 0;
            } else { //接受者
                // 代表 接收消息类型
                return 1;
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            int type = getItemViewType(position);
            ViewHolders holder = null;
            if (type == 0) {
                // 发送消息类型，加载相应的布局
                if (convertView == null) {
                    convertView = View.inflate(getBaseContext(), R.layout.item_chat_send, null);
                    holder = new ViewHolders(convertView);
                    holder.time = (TextView) convertView.findViewById(R.id.time);
                    holder.head = (ImageView) convertView.findViewById(R.id.head);
                    holder.content = (TextView) convertView.findViewById(R.id.content);
                } else {
                    holder = (ViewHolders) convertView.getTag();
                }
            } else {
                // 接收消息类型，加载相应的布局
                if (convertView == null) {
                    convertView = View.inflate(getBaseContext(), R.layout.item_chat_receive, null);
                    holder = new ViewHolders(convertView);
                    holder.time = (TextView) convertView.findViewById(R.id.time);
                    holder.head = (ImageView) convertView.findViewById(R.id.head);
                    holder.content = (TextView) convertView.findViewById(R.id.content);
                } else {
                    holder = (ViewHolders) convertView.getTag();
                }
            }
            HashMap<String, String> message = list.get(position);

            for (String key : message.keySet()) {
                if (type == 0) {
                    holder.time.setText(getCurrentTime() + " " + chatIDs.get(position));
                } else {
                    holder.time.setText(chatIDs.get(position) + " " + getCurrentTime());
                }
                String userMessage = message.get(key);

                Log.d(TAG, "userMessage----" + userMessage);
                builder = new SpannableStringBuilder(userMessage);
                if ((userMessage.indexOf("@")) == 0 && (userMessage.indexOf(key)) == 1) {// @聊天
                    builder.setSpan(blueSpan, 0, key.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    holder.content.setText(builder);
                } else if (userMessage.contains("悄悄对_" + key + "_说:")) { // @ 私聊
                    builder.setSpan(redSpan, 0, userMessage.indexOf(key) + key.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    holder.content.setText(builder);
                } else if ((userMessage.indexOf("@")) == 0 && (userMessage.indexOf(thisUserId)) == 1) {
                    builder.setSpan(blueSpan, 0, thisUserId.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    holder.content.setText(builder);
                } else if (userMessage.contains("悄悄对_" + thisUserId + "_说:")) {
                    builder.setSpan(redSpan, 0, userMessage.indexOf(thisUserId) + thisUserId.length() + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    holder.content.setText(builder);
                } else {
                    holder.content.setText(userMessage);
                }

            }
            return convertView;
        }
    }

    static class ViewHolders {
        // 发送/接收消息 的时间
        private TextView time;
        // 发送/接收消息 的内容
        private TextView content;
        // 发送/接收者 的头像
        private ImageView head;

        public ViewHolders(View view) {
            view.setTag(this);
        }

    }

    private void setWindow() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);// 去掉标题栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);// 设置全屏*/
        // 设置竖屏显示
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // 选择支持半透明模式,在有surfaceview的activity中使用。
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
    }

    private void initViews() {
        //选择用户列表
        tvChoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //String chatUsers[] = new String[]{"选项1", "选项2", "选项3", "选项4"};
                Log.d("peerNames", "---" + peerNames.toString() + peerNames.size());
                if (peers.size() > 0) {
                    chatUsers = new String[peerNames.size() + 1];
                    chatUsers[0] = "用户列表";
                    for (int i = 0; i < peerNames.size(); i++) {
                        String keyID = peerNames.get(i);
                        chatUsers[i + 1] = keyID;
                    }
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("用户列表")
                        .setSingleChoiceItems(chatUsers, 0, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Log.d(TAG, "which---" + which);
                                sendTargetName = chatUsers[which];
                                tvChoice.setText(sendTargetName);
                                Log.d(TAG, "which---" + sendTargetName);
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        // 房主开始视频
        start_video.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isStartVideo = true;
                if ("1".equals(hasAdmin)) {
                    requestP2P();
                } else {
                    Toast.makeText(MainActivity.this, "你当前不是房主！！", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }
}
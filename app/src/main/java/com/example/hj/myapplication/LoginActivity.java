package com.example.hj.myapplication;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.example.hj.myapplication.Bean.RoomConnectionParameters;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * Created by hj on 2017/4/24.
 * 登陸界面
 */
public class LoginActivity extends Activity {
    @InjectView(R.id.roomid)
    EditText roomid;
    @InjectView(R.id.userid)
    EditText userid;
    @InjectView(R.id.password)
    EditText password;
    @InjectView(R.id.username)
    EditText username;
    @InjectView(R.id.adminid)
    EditText adminid;
    @InjectView(R.id.roomname)
    EditText roomname;
    @InjectView(R.id.roomip)
    EditText roomip;
    private RoomConnectionParameters roomConnectionParameters;
    /**
     * 房间号
     */
    private String roomID;
    /**
     * 用户ID
     */
    private String userID;
    /**
     * 用户密码
     */
    private String passWORD;
    /**
     * 用户名
     */
    private String userName;
    /**
     * 房主ID
     */
    private String adminID;
    /**
     * 房间名
     */
    private String roomNAME;
    /**
     * 服务端ip/房间域
     */
    private String roomIp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ButterKnife.inject(this);
    }

    public void login(View view) {
        roomID = roomid.getText().toString().trim();
        userID = userid.getText().toString().trim();
        passWORD = password.getText().toString().trim();
        userName = username.getText().toString().trim();
        adminID = adminid.getText().toString().trim();
        roomNAME = roomname.getText().toString().trim();
        roomIp = roomip.getText().toString().trim();
        roomIp = "192.168.2.102";
        //roomIp = MyApplication.ipAddress; 获取手机ip
        Log.d("roomip:", roomIp);
        roomConnectionParameters = new RoomConnectionParameters(roomID, userID, passWORD, userName, adminID, roomNAME, roomIp);
        Log.d("login", roomID + userID);
        if ((roomID.isEmpty()) || (userID.isEmpty()) || (passWORD.isEmpty()) || (userName.isEmpty()) || (adminID.isEmpty()) || (roomNAME.isEmpty()) || (roomIp.isEmpty())) {
            Toast.makeText(this, "帐号或密码输入不能为空！", Toast.LENGTH_SHORT).show();
        } else {
            // 连接服务器
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("roomConnectionParameters", roomConnectionParameters);
            startActivity(intent);
            finish();
        }
    }
}

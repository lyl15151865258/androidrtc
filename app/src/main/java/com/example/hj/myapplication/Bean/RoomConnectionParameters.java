package com.example.hj.myapplication.Bean;

import java.io.Serializable;

/**
 * 房间参数类
 * Created by hj on 2017/4/19.
 */
public class RoomConnectionParameters implements Serializable {
    /**
     * 房间ID
     */
    public String roomid;
    /**
     * 用户ID
     */
    public String userid;
    /**
     * 用户密码
     */
    public String password;
    /**
     * 用户名
     */
    public String username;
    /**
     * 管理员ID
     */
    public String adminid;
    /**
     * 房间名
     */
    public String roomname;
    /**
     * 服务端IP
     */
    public String roomip;


    public RoomConnectionParameters(String roomid, String userid, String password, String username, String adminid, String roomname, String roomip) {
        this.roomid = roomid;
        this.userid = userid;
        this.password = password;
        this.username = username;
        this.adminid = adminid;
        this.roomname = roomname;
        this.roomip = roomip;
    }

    @Override
    public String toString() {
        return "RoomConnectionParameters{" +
                "roomid='" + roomid + '\'' +
                ", userid='" + userid + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", adminid='" + adminid + '\'' +
                ", roomname='" + roomname + '\'' +
                '}';
    }
}

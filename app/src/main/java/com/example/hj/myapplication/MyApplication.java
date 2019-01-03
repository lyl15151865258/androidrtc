package com.example.hj.myapplication;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.webrtc.PeerConnection;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by hj on 2017/5/8.
 */
public class MyApplication extends Application {
    //ctrl+shit+O:自动导入包，当然也会清除掉多余的包
    /**
     * 全局初始化map集合，用于存储房间里所有对等连接的对象
     */
    public static Map<String, PeerConnection> peers;
    public static String ipAddress;

    @Override
    public void onCreate() {
        super.onCreate();
        peers = new HashMap<>();
        getPhoneIp();
    }

    /**
     * 获得手机ip地址
     *
     * @return
     */
    public void getPhoneIp() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifiManager.isWifiEnabled()) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            ipAddress = intIP2StringIP(wifiInfo.getIpAddress());//得到IPV4地址
        } else {
            ipAddress = null;
        }
    }

    /**
     * 将得到的int类型的IP转换为String类型
     *
     * @param ip
     * @return
     */
    public static String intIP2StringIP(int ip) {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                (ip >> 24 & 0xFF);

    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }
}

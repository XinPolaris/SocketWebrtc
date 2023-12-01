package com.ryan.socketwebrtc

import android.content.Context
import android.content.SharedPreferences

/**
 *  Created by HuangXin on 2023/12/1.
 */
object IPUtils {

    private const val KEY_IP = "key_ip"

    fun saveServerIP(context: Context, ip: String?) {
        ip ?: return
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("webrtc", 0)
        sharedPreferences.edit().putString(KEY_IP, ip).apply()
    }

    fun getServerIP(context: Context): String? {
        return context.getSharedPreferences("webrtc", 0).getString(KEY_IP, "10.49.54.128")
    }
}
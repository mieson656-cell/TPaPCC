package com.tpappcc

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import org.json.JSONObject

object TpaPccApi {
    private const val BASE = "https://xblfnpiarlhbntfkxwgq.supabase.co/functions/v1/tpaPCC-api"
    private const val PREFS = "tpapcc"
    private const val TOKEN = "device_token"
    private const val DEVICE = "device_id"

    fun token(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.getString(TOKEN, null)?.let { return it }
        val bytes = ByteArray(32); SecureRandom().nextBytes(bytes)
        val value = bytes.joinToString("") { "%02x".format(it) }
        p.edit().putString(TOKEN, value).apply(); return value
    }
    fun deviceId(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DEVICE, null)
    fun register(context: Context, name: String, callback: (Boolean, String?) -> Unit) = post(context, "register_device", JSONObject().put("device_token", token(context)).put("name", name).put("platform", "android"), callback)
    fun createCode(context: Context, callback: (Boolean, String?) -> Unit) = post(context, "create_code", JSONObject(), callback)
    fun redeemCode(context: Context, code: String, callback: (Boolean, String?) -> Unit) = post(context, "redeem_code", JSONObject().put("code", code), callback)
    private fun post(context: Context, action: String, body: JSONObject, callback: (Boolean, String?) -> Unit) {
        Thread {
            try {
                body.put("action", action)
                val conn = (URL(BASE).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 10000; readTimeout = 10000; doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (action != "register_device") setRequestProperty("Authorization", "Bearer " + token(context))
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val text = stream.bufferedReader().use { it.readText() }; val ok = conn.responseCode in 200..299
                if (ok && action == "register_device") { val id = JSONObject(text).optString("device_id", ""); if (id.isNotBlank()) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(DEVICE, id).apply() }
                android.os.Handler(context.mainLooper).post { callback(ok, text) }
            } catch (e: Exception) { android.os.Handler(context.mainLooper).post { callback(false, e.message) } }
        }.start()
    }
}

package com.nativesensortracker

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.*
import android.content.IntentFilter
import okhttp3.MediaType.Companion.toMediaType
import android.provider.Settings

class SensorService : Service(), LocationListener {

    private val channelId = "SensorServiceChannel"
    private val timer = Timer()
    private lateinit var locationManager: LocationManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Sensor Service")
            .setContentText("Collecting data in background...")
            .setSmallIcon(applicationInfo.icon)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                fetchLocationAndSend()
            }
        }, 0, 60 * 1000)

        return START_STICKY
    }

    private fun fetchLocationAndSend() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("SensorService", "Location permission not granted")
            return
        }
        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, Looper.getMainLooper())
    }

    override fun onLocationChanged(location: Location) {
        val batteryStatus = getBatteryLevel()
        val payload = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("altitude", location.altitude)
            put("speed", location.speed)
            put("battery", batteryStatus)
        }
        sendToApi(payload)
    }

    private fun getBatteryLevel(): Int {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level != -1 && scale != -1) (level * 100) / scale else -1
    }

    private fun sendToApi(data: JSONObject) {
        val client = OkHttpClient()
        val requestBody = RequestBody.create("application/json".toMediaType(), data.toString())
        val androidId = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
        val request = Request.Builder()
            .url("https://perpule-data.firebaseio.com/sensor_data/${androidId}.json")
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SensorService", "API call failed: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                Log.d("SensorService", "Data sent: ${response.code}")
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        timer.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Sensor Tracking Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
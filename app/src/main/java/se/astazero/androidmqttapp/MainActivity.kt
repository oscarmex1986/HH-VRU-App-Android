package se.astazero.androidmqttapp

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import org.eclipse.paho.client.mqttv3.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import se.astazero.androidmqttapp.detectedactivity.DetectedActivityService
import se.astazero.androidmqttapp.mqtt.MqttClientHelper
import se.astazero.androidmqttapp.transitions.*
import se.astazero.androidmqttapp.transitions.TransitionsReceiver
import se.astazero.androidmqttapp.transitions.removeActivityTransitionUpdates
import se.astazero.androidmqttapp.transitions.requestActivityTransitionUpdates
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    var running = false // A flag to determine if we should send location updates

    //Setup location services
    var currentLatitude: Double? = null
    var currentLongitude: Double? = null
    var currentAltitude: Double? = null
    var oldLatitude: Double? = null
    var oldLongitude: Double? = null
    var distance: Double? = null
    var userAgent = "OsmNavigator/2.2"
    var activityText = "Starting..."
    var updateMqtt: Boolean = false
    var updateInterval: Long = 100
    var elapsedSinceMessage: Long = 0
    var maxMessageInterval: Long = 5000
    var firstTime = true

    // Set objectID: the individual identification of a VRU and the MQTT topic structure TODO: dynamic
    val objectID = Integer.toHexString(4001)
    var calendar = Calendar.getInstance()
    val topicPath = ""
    //Check/request for permissions on Run Time
    private val PermissionsRequestCode = 123
    private lateinit var managePermissions: ManagePermissions

    // OAM instantiate MQTT client
    private val mqttClient by lazy {
        MqttClientHelper(this)
    }

    private val transitionBroadcastReceiver: TransitionsReceiver = TransitionsReceiver().apply {
        action = { setDetectedActivity(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // List of requested permissions
        val list = listOf<String>(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION

        )

        managePermissions = ManagePermissions(this, list, PermissionsRequestCode)
        managePermissions.checkPermissions()

        // Instantiate location services client
        var map = findViewById(R.id.map) as MapView
        map.setBuiltInZoomControls(true)
        map.setMultiTouchControls(true)
        map.setUseDataConnection(true)
        val mapViewController = map.controller
        mapViewController.setZoom(16)
        var startPoint = GeoPoint(0, 0) // Change these to the first location you would like to show
        mapViewController.setCenter(startPoint)
        map.setTileSource(TileSourceFactory.MAPNIK)
        val myLocationOverlay = MyLocationNewOverlay(map)
        map.overlays.add(myLocationOverlay)
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.enableFollowLocation()

        // Instantiate the Handler to request locations every second
        val mainHandler =
            Handler(Looper.getMainLooper())

        currentLatitude = myLocationOverlay.myLocation?.latitude
        currentLongitude = myLocationOverlay.myLocation?.longitude
        currentAltitude = myLocationOverlay.myLocation?.altitude

        labelLat.text = "Lat: " + currentLatitude
        labelLon.text = "Lon: " + currentLongitude
        labelDist.text = "Dist:" + distance

        // Set MQTT callback
        setMqttCallBack()

        btnStart.setOnClickListener {
            running = running.not()
            if(firstTime){

                val topic = "$topicPath/$objectID" // This is contingent on your MQTT topic structure
                var snackbarMsg: String = "Cannot Publish"
                val payloadMqtt: String = objectID
                snackbarMsg = try {
                    mqttClient.publish(topic, payloadMqtt)
                    "Published to topic '$topic'"
                } catch (ex: MqttException) {
                    "Error publishing to topic '$topic'"
                }
                Toast.makeText(this, snackbarMsg, Toast.LENGTH_LONG)
            }
            if (running) {
                btnStart.text = "Stop Broadcasting Location"
                startService(Intent(this, DetectedActivityService::class.java))
                requestActivityTransitionUpdates()
            } else {
                btnStart.text = "Start Broadcasting Location"
                stopService(Intent(this, DetectedActivityService::class.java))
                removeActivityTransitionUpdates()

            }
        }

        fun mainTimerProcess() {
            currentLatitude = myLocationOverlay.myLocation?.latitude
            currentLongitude = myLocationOverlay.myLocation?.longitude
            currentAltitude = myLocationOverlay.myLocation?.altitude
            calendar = Calendar.getInstance()

            if(currentLatitude != oldLatitude || currentLongitude != oldLongitude){
                updateMqtt = true
            } else if (elapsedSinceMessage >= maxMessageInterval){
                updateMqtt = true
            }

            if(currentLatitude != null ){
                distance = startPoint.distanceToAsDouble(currentLongitude?.let {
                    currentLatitude?.let { it1 ->
                        GeoPoint(
                            it1,
                            it
                        )
                    }
                })/1000

                if (distance!! > 10000){
                    updateMqtt = false
                }

            }




            if (updateMqtt) {
                labelLat.text = "Lat: " + currentLatitude
                labelLon.text = "Lon: " + currentLongitude
                labelDist.text = "Dist:" + distance

                oldLatitude = currentLatitude
                oldLongitude = currentLongitude

                var snackbarMsg: String = "Cannot Publish"
                val topic = topicPath+"/"+objectID+"/position/value"
                val topicUnits = topicPath+"/"+objectID+"/position/unit"
                val payloadMqtt: String = "{${'"'}timestamp${'"'}:" + calendar.timeInMillis.toString() + ",${'"'}position${'"'}:{${'"'}latitude${'"'}:${'"'}"+currentLatitude+"${'"'},${'"'}longitude${'"'}:${'"'}"+ currentLongitude +"${'"'},${'"'}altitude${'"'}:${'"'}"+currentAltitude+"${'"'}}}"
                val payloadUnits : String = "{${'"'}timeStamp${'"'}: ${'"'}UTCmillisecond${'"'}, ${'"'}position${'"'}: {${'"'}latitude${'"'}: ${'"'}DD${'"'}, ${'"'}longitude${'"'}: ${'"'}DD${'"'}, ${'"'}altitude${'"'}: ${'"'}meterabovesealevel${'"'}}}"
                snackbarMsg = try {
                    mqttClient.publish(topicUnits, payloadUnits)
                    "Published to units"
                } catch (ex: MqttException){
                    "Error publishing units"
                }
                snackbarMsg = try {
                    mqttClient.publish(topic, payloadMqtt)
                    "Published to topic '$topic'"
                } catch (ex: MqttException) {
                    "Error publishing to topic '$topic'"
                }
                Toast.makeText(this, snackbarMsg, Toast.LENGTH_LONG)

                updateMqtt = false
                elapsedSinceMessage = 0
            }

        }

        mainHandler.post(object : Runnable {
            override fun run() {
                if (running) {
                    elapsedSinceMessage += updateInterval
                    mainTimerProcess()
                }
                mainHandler.postDelayed(
                    this,
                    updateInterval
                )
            }
        })


    }

    override fun onResume() {
        super.onResume()
        registerReceiver(transitionBroadcastReceiver, IntentFilter(TRANSITIONS_RECEIVER_ACTION))
    }

    override fun onPause() {
        unregisterReceiver(transitionBroadcastReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        removeActivityTransitionUpdates()
        stopService(Intent(this, DetectedActivityService::class.java))
        super.onDestroy()
    }

    private fun setMqttCallBack() {
        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(b: Boolean, s: String) {
                val snackbarMsg = "Connected to host:\n'$MQTT_HOST'."
                Log.w("Debug", snackbarMsg)
                Snackbar.make(findViewById(android.R.id.content), snackbarMsg, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            }

            override fun connectionLost(throwable: Throwable) {
                val snackbarMsg = "Connection to host lost:\n'$MQTT_HOST'"
                Log.w("Debug", snackbarMsg)
                Snackbar.make(findViewById(android.R.id.content), snackbarMsg, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            }

            @Throws(Exception::class)
            override fun messageArrived(topic: String, mqttMessage: MqttMessage) {
                Log.w("Debug", "Message received from host '$MQTT_HOST': $mqttMessage")
            }

            override fun deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken) {
                Log.w("Debug", "Message published to host '$MQTT_HOST'")
            }
        })


    }

    private fun setDetectedActivity(supportedActivity: SupportedActivity) {

        activityText = getString(supportedActivity.activityText)
    }
}
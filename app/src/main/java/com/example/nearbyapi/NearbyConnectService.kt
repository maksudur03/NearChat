package com.example.nearbyapi

import android.app.Notification.PRIORITY_HIGH
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.os.Binder
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.N
import android.os.Build.VERSION_CODES.Q
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.nearbyapi.NearbyConnectService.State.ADVERTISING
import com.example.nearbyapi.NearbyConnectService.State.CONNECTED
import com.example.nearbyapi.NearbyConnectService.State.DISCOVERING
import com.example.nearbyapi.NearbyConnectService.State.UNKNOWN
import com.example.nearbyapi.Utils.BG_NOTIFICATION_CHANNEL_ID
import com.example.nearbyapi.Utils.FOREGROUND_NOTIFICATION_REQUEST_CODE
import com.example.nearbyapi.Utils.getNotificationUpdateCurrentFlags
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy.P2P_CLUSTER
import com.google.android.gms.nearby.connection.Strategy.P2P_STAR

/**
 * @author Munif
 * @since 4/11/24.
 */
class NearbyConnectService : Service() {

    private var state = UNKNOWN
    private val strategy = P2P_STAR

    private val discoveredDevices = HashMap<String, Endpoint>()
    private val pendingDevices = HashMap<String, Endpoint>()
    private val connectedDevices = HashMap<String, Endpoint>()

    private var isConnecting = false
    private var isDiscovering = false
    private var isAdvertising = false

    private lateinit var connectionsClient: ConnectionsClient
    private val binder = NearbyBinder()
    private var messages = ArrayList<Pair<Boolean, String>>()

    inner class NearbyBinder : Binder() {
        fun getService(): NearbyConnectService = this@NearbyConnectService
    }

    override fun onCreate() {
        super.onCreate()
        isServiceActive = true
    }

    override fun onDestroy() {
        super.onDestroy()
        setState(UNKNOWN)
        isServiceActive = false
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        connectionsClient = Nearby.getConnectionsClient(this)
        if (userName.contains("S")) {
            setState(ADVERTISING)
        } else {
            setState(DISCOVERING)
        }
        startAtForeGround()
        return START_STICKY
    }

    private fun startAtForeGround() {
        val notificationIntent = Intent(this, ChatSessionActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this, FOREGROUND_NOTIFICATION_REQUEST_CODE,
            notificationIntent, getNotificationUpdateCurrentFlags()
        )
        val notification =
            NotificationCompat.Builder(this, BG_NOTIFICATION_CHANNEL_ID).apply {
                setContentTitle(getString(R.string.app_name))
                setTicker(getString(R.string.app_name))
                setContentText("You are Online")
                setColor(ContextCompat.getColor(this@NearbyConnectService, R.color.black))
                setSmallIcon(R.drawable.ic_near_chat)
                setContentIntent(pendingIntent)
                if (SDK_INT >= N) {
                    setPriority(IMPORTANCE_HIGH)
                } else {
                    setPriority(PRIORITY_HIGH)
                }
                setOngoing(true)
                setAutoCancel(false)
            }
        if (SDK_INT >= Q) {
            startForeground(12, notification.build(), FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(12, notification.build())
        }
    }

    private fun startAdvertising() {
        println("connect catch startAdvertising")
        isAdvertising = true
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(userName, packageName, advertiseCallback, options)
            .addOnFailureListener { broadcastToastMessage("Advertising Failed") }
    }

    private val advertiseCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            println("connect catch onConnectionInitiated $endpointId")
            val endpoint = Endpoint(endpointId, connectionInfo.endpointName)
            pendingDevices[endpointId] = endpoint
            onConnectionInitiated(endpointId)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            println("connect catch onConnectionResult ${result.status.isSuccess} $endpointId")
            isConnecting = false
            val endpoint = pendingDevices.remove(endpointId)
            if (result.status.isSuccess) {
                if (endpoint != null) {
                    connectedToEndpoint(endpoint)
                }
                broadcastConnectionStatus(true)
            } else {
                onConnectionFailed()
                broadcastConnectionStatus(false)
            }
        }

        override fun onDisconnected(endpointId: String) {
            println("connect catch onDisconnected")
            connectedDevices.remove(endpointId)
            if (connectedDevices.isEmpty()) {
                setState(DISCOVERING)
            }
        }
    }

    private fun onConnectionInitiated(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnFailureListener { exception ->
                broadcastToastMessage("Accept Connection Failed $exception")
            }
    }

    private fun connectedToEndpoint(endpoint: Endpoint) {
        connectedDevices[endpoint.id] = endpoint
        setState(CONNECTED)
    }

    private fun stopAdvertising() {
        isAdvertising = false
        connectionsClient.stopAdvertising()
    }

    private fun startDiscovering() {
        println("connect catch startDiscovery")
        isDiscovering = true
        discoveredDevices.clear()
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(packageName, discoveryCallback, options)
            .addOnFailureListener { exception ->
                isDiscovering = false
                broadcastToastMessage("Discovery Failed ${exception.message}")
            }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            println("connect catch onEndpointFound $endpointId ${info.endpointName}")
            if (info.endpointName.contains("1-")) {
                val endpoint = Endpoint(endpointId, info.endpointName)
                discoveredDevices[endpointId] = endpoint
                if (!isConnecting) {
                    connectToEndpoint(endpoint)
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            println("connect catch onEndpointLost")
        }
    }

    private fun connectToEndpoint(endpoint: Endpoint) {
        isConnecting = true
        connectionsClient
            .requestConnection(userName, endpoint.id, advertiseCallback)
            .addOnFailureListener { exception ->
                isConnecting = false
                onConnectionFailed()
                println("connect catch onEndpointFound fail  $exception")
                broadcastToastMessage("request connection fail $exception")
            }
    }

    private fun stopDiscovering() {
        isDiscovering = false
        connectionsClient.stopDiscovery()
    }

    private fun onConnectionFailed() {
        // Let's try someone else.
        if (state == DISCOVERING && discoveredDevices.isNotEmpty()) {
            connectToEndpoint(discoveredDevices.values.toList()[0])
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            println("connect catch onPayloadReceived")
            val message = String(payload.asBytes() ?: ByteArray(0))
            messages.add(0, Pair(false, message))
            broadcastMessage(message)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            println("connect catch onPayloadTransferUpdate")
        }
    }

    private fun setState(newState: State) {
        if (state == newState) {
            return
        }
        when (newState) {
            DISCOVERING -> {
                if (isAdvertising) {
                    stopAdvertising()
                }
                disconnectFromAllEndpoints()
                startDiscovering()
            }
            ADVERTISING -> {
                if (isDiscovering) {
                    stopDiscovering()
                }
                disconnectFromAllEndpoints()
                startAdvertising()
            }
            CONNECTED -> if (isDiscovering) {
                stopDiscovering()
            }
            UNKNOWN -> stopAllEndpoints()
        }
    }

    private fun stopAllEndpoints() {
        connectionsClient.stopAllEndpoints()
        stopDiscovering()
        stopAdvertising()
        discoveredDevices.clear()
        pendingDevices.clear()
        connectedDevices.clear()
    }

    private fun broadcastMessage(message: String) {
        val intent = Intent("MESSAGE_RECEIVED")
        intent.putExtra("MESSAGE", message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastConnectionStatus(isConnected: Boolean) {
        val intent = Intent("CONNECTION_STATUS")
        intent.putExtra("IS_CONNECTED", connectedDevices.isNotEmpty())
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastToastMessage(message: String) {
        val intent = Intent("MESSAGE_RECEIVED")
        intent.putExtra("TOAST", message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun sendMessage(message: String) {
        println("connect catch sendMessage")
            val payload = Payload.fromBytes(message.toByteArray(Charsets.UTF_8))
            connectionsClient.sendPayload(connectedDevices.keys.toList(), payload)
            messages.add(0, Pair(true, message))
    }

    fun disconnectFromAllEndpoints() {
         connectedDevices.keys.forEach { id ->
             connectionsClient.disconnectFromEndpoint(id)
         }
         connectedDevices.clear()
    }

    fun getMessages(): ArrayList<Pair<Boolean, String>> {
        return messages
    }

    companion object {
        var isServiceActive = false
        var userName = ""
    }

    private enum class State {
        UNKNOWN,
        DISCOVERING,
        ADVERTISING,
        CONNECTED
    }
}
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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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

    private val strategy = P2P_CLUSTER
    private val pendingDevices = HashMap<String, String>()// List of all connected endpoints
    private lateinit var connectionsClient: ConnectionsClient
    private val binder = NearbyBinder()
    private var connectedEndpointId: String? = null
    private var messages = ArrayList<Pair<Boolean, String>>()
    private var isConnectionOngoing = false

    inner class NearbyBinder : Binder() {
        fun getService(): NearbyConnectService = this@NearbyConnectService
    }

    override fun onCreate() {
        super.onCreate()
        isServiceActive = true
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionsClient.run {
            stopAdvertising()
            stopDiscovery()
            stopAllEndpoints()

        }
        isServiceActive = false
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        connectionsClient = Nearby.getConnectionsClient(this)
        startAdvertising()
        startDiscovery()
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
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(userName, packageName, advertiseCallback, options)
            .addOnCompleteListener {
                println("connect catch  complete startAdvertising")
            }
            .addOnFailureListener { broadcastToastMessage("Advertising Failed") }
    }

    private fun startDiscovery() {
        println("connect catch startDiscovery")
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(packageName, discoveryCallback, options)
            .addOnCompleteListener {
                println("connect catch  complete Discovery")
            }
            .addOnFailureListener { exception -> broadcastToastMessage("Discovery Failed ${exception.message}") }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            println("connect catch onEndpointFound $endpointId ${info.endpointName}")
            if (pendingDevices.isEmpty()) {
                if (info.endpointName.startsWith("1-")) {
                    pendingDevices[endpointId] = info.endpointName
                }
                requestConnectionToDevice(endpointId)
            } else {
                pendingDevices.forEach { device ->
                    if (device.key == endpointId && device.value == info.endpointName) {
                        return
                    } else if (device.key == endpointId && device.value != info.endpointName) {
                        pendingDevices[endpointId] = info.endpointName
                    }
                    else if (device.key != endpointId && device.value == info.endpointName) {
                        pendingDevices.remove(device.key)
                        pendingDevices[endpointId] = info.endpointName
                    } else {
                        if (info.endpointName.startsWith("1-")) {
                            pendingDevices[endpointId] = info.endpointName
                        }
                        if (!isConnectionOngoing) {
                            requestConnectionToDevice(endpointId)
                        }
                    }
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            println("connect catch onEndpointLost")

            //connectedEndpointIds.remove(endpointId)
            isConnectionOngoing = false
            connectionsClient.disconnectFromEndpoint(endpointId)
            pendingDevices.remove(endpointId)// Remove from list if disconnected
            broadcastConnectionStatus(false)
        }
    }

    private fun requestConnectionToDevice(endpointId: String) {
        isConnectionOngoing = true
        connectionsClient.requestConnection(userName, endpointId, advertiseCallback)
            .addOnSuccessListener {
                broadcastToastMessage("ConnectionSuccess $endpointId")
            }
            .addOnFailureListener { exception ->
                isConnectionOngoing = false
                connectionsClient.disconnectFromEndpoint(endpointId)
                println("connect catch onEndpointFound fail  $exception")
                broadcastToastMessage("R c F $exception")
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

    private val advertiseCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            println("connect catch onConnectionInitiated $endpointId")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnCompleteListener {
                    println("connect catch acceptConnection complete $endpointId")
                }
                .addOnFailureListener { exception ->
                    broadcastToastMessage("Accept Connection Failed $exception")
                }
            connectedEndpointId = endpointId
            //connectedEndpointIds.add(endpointId)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            println("connect catch onConnectionResult ${result.status.isSuccess} $endpointId")
            if (result.status.isSuccess) {
                broadcastConnectionStatus(true)
            } else {
                broadcastConnectionStatus(false)
            }
            isConnectionOngoing = false
            pendingDevices.remove(endpointId)
            if (pendingDevices.size > 0) {
                requestConnectionToDevice(pendingDevices.keys.toList()[0])
            }
        }

        override fun onDisconnected(endpointId: String) {
            println("connect catch onDisconnected")
            //connectedEndpointIds.remove(endpointId)
            if (pendingDevices.size > 0) {
                requestConnectionToDevice(pendingDevices.keys.toList()[0])
            }// Remove from list if disconnected
            broadcastConnectionStatus(false)
        }
    }

    private fun broadcastMessage(message: String) {
        val intent = Intent("MESSAGE_RECEIVED")
        intent.putExtra("MESSAGE", message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastConnectionStatus(isConnected: Boolean) {
        val intent = Intent("CONNECTION_STATUS")
        intent.putExtra("IS_CONNECTED", isConnected)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastToastMessage(message: String) {
        val intent = Intent("MESSAGE_RECEIVED")
        intent.putExtra("TOAST", message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun sendMessage(message: String) {
        println("connect catch sendMessage")
        connectedEndpointId?.let {
            val payload = Payload.fromBytes(message.toByteArray(Charsets.UTF_8))
            connectionsClient.sendPayload(it, payload)
            messages.add(0, Pair(true, message))
        }
    }

    fun endChat() {
       /* connectedEndpointIds.forEach { id ->
            connectionsClient.disconnectFromEndpoint(id)
        }
        connectedEndpointIds.clear()*/
        connectedEndpointId = null
    }

    fun getMessages(): ArrayList<Pair<Boolean, String>> {
        return messages
    }


    companion object {
        var isServiceActive = false
        var userName = ""
    }
}
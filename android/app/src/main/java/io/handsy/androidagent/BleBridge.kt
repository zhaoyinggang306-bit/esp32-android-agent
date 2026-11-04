package io.handsy.androidagent

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.nio.charset.StandardCharsets
import java.util.UUID

/** BLE central for the ESP32 Agent GATT protocol; see ../../docs/ble-protocol.md. */
class BleBridge(private val context: Context, private val onStatus: (String) -> Unit) {
    companion object {
        private val SERVICE = UUID.fromString("6e400001-a9e8-4b1b-9c39-6d6f8a3c0000")
        private val CMD = UUID.fromString("6e400001-a9e8-4b1b-9c39-6d6f8a3c0001")
        private val REPLY = UUID.fromString("6e400001-a9e8-4b1b-9c39-6d6f8a3c0002")
        private val STATUS = UUID.fromString("6e400001-a9e8-4b1b-9c39-6d6f8a3c0003")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private var gatt: BluetoothGatt? = null
    private var command: BluetoothGattCharacteristic? = null
    private var requestId = 0
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun scanAndConnect() {
        if (adapter == null || !adapter.isEnabled) { onStatus("请先打开蓝牙"); return }
        onStatus("正在搜索 ESP32 Android Agent…")
        val scanner = adapter.bluetoothLeScanner ?: run { onStatus("蓝牙 LE 不可用"); return }
        val callback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                val name = result.device.name ?: return
                if (!name.startsWith("ESP32 Android Agent")) return
                scanner.stopScan(this)
                onStatus("正在连接 $name…")
                gatt = result.device.connectGatt(context, false, gattCallback)
            }
            override fun onScanFailed(errorCode: Int) = onStatus("扫描失败：$errorCode")
        }
        scanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()), ScanSettings.Builder().build(), callback)
        handler.postDelayed({ scanner.stopScan(callback) }, 15_000)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || state != BluetoothProfile.STATE_CONNECTED) {
                command = null; onStatus("已断开（$status）"); return
            }
            g.requestMtu(247)
        }
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { g.discoverServices() }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(SERVICE)
            command = service?.getCharacteristic(CMD)
            if (command == null) { onStatus("未找到 Agent GATT 服务"); return }
            listOf(REPLY, STATUS).forEach { id ->
                service.getCharacteristic(id)?.let { characteristic ->
                    g.setCharacteristicNotification(characteristic, true)
                    characteristic.getDescriptor(CCCD)?.let { descriptor ->
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        g.writeDescriptor(descriptor)
                    }
                }
            }
            onStatus("已连接；USB 插入手机后可发送 HID 动作")
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            onStatus(c.value.toString(StandardCharsets.UTF_8))
        }
    }

    /** Sends one protocol command. The board acknowledges using the same request id. */
    @SuppressLint("MissingPermission")
    fun send(action: String) {
        val c = command ?: run { onStatus("请先连接 ESP32"); return }
        val line = "${++requestId} $action".toByteArray(StandardCharsets.UTF_8)
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        c.value = line
        if (gatt?.writeCharacteristic(c) != true) onStatus("命令发送失败")
    }

    @SuppressLint("MissingPermission")
    fun close() { gatt?.close(); gatt = null; command = null }
}

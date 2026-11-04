package io.handsy.androidagent

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {
    private lateinit var bridge: BleBridge
    private lateinit var status: TextView
    private val projectionRequest = 42

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        status = TextView(this).apply { text = "准备就绪：先把 ESP32 插入安卓手机，再连接。"; setPadding(32, 32, 32, 32) }
        bridge = BleBridge(this) { runOnUiThread { status.text = it } }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        root.addView(status)
        fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }
        root.addView(button("连接 ESP32") { requestBluetoothThenConnect() })
        root.addView(button("授权屏幕读取") { requestScreenCapture() })
        root.addView(button("测试点击") { bridge.send("click") })
        root.addView(button("返回桌面") { bridge.send("homescreen") })
        val input = EditText(this).apply { hint = "ASCII 文本" }
        root.addView(input)
        root.addView(button("输入文本") { bridge.send("type ${input.text}") })
        setContentView(root)
    }
    private fun requestBluetoothThenConnect() {
        if (android.os.Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 1)
        } else bridge.scanAndConnect()
    }
    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, p, results); if (code == 1 && results.all { it == PackageManager.PERMISSION_GRANTED }) bridge.scanAndConnect()
    }
    private fun requestScreenCapture() { startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), projectionRequest) }
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == projectionRequest && result == RESULT_OK && data != null) startForegroundService(Intent(this, ScreenCaptureService::class.java).putExtra("resultCode", result).putExtra("data", data))
    }
    override fun onDestroy() { bridge.close(); super.onDestroy() }
}

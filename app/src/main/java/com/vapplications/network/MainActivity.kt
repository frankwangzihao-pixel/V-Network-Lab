package com.vapplications.network

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val bg = Color.rgb(18, 29, 48)
    private val panel = Color.rgb(31, 49, 76)
    private val muted = Color.rgb(176, 196, 216)
    private val cyan = Color.rgb(141, 229, 246)
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var pingText: TextView
    private lateinit var dnsInput: EditText
    private lateinit var dnsOutput: TextView
    private lateinit var pingButton: Button
    private lateinit var dnsButton: Button
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        updateNetworkInfo()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun shape(color: Int, radius: Int = 18): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun label(text: String, size: Float = 15f, color: Int = Color.WHITE, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun add(parent: LinearLayout, view: View, top: Int = 0) {
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    private fun card(title: String): LinearLayout {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(17), dp(18), dp(18)); background = shape(panel) }
        add(c, label(title, 18f, Color.WHITE, true))
        add(root, c, 14)
        return c
    }
    private fun action(text: String, click: () -> Unit): Button = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(39, 122, 204), Color.rgb(33, 170, 194))).apply { cornerRadius = dp(14).toFloat() }
        setOnClickListener { click() }
    }
    private fun buildUi() {
        val outer = android.widget.FrameLayout(this)
        outer.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(bg, Color.rgb(35, 45, 78), Color.rgb(17, 53, 67)))
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(30)) }
        scroll.addView(root)
        outer.addView(scroll)
        setContentView(outer)

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val icon = ImageView(this).apply { setImageResource(com.vapplications.network.R.drawable.ic_v_network) }
        header.addView(icon, LinearLayout.LayoutParams(dp(54), dp(54)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        add(titles, label("V Network Lab", 23f, Color.WHITE, true))
        add(titles, label("V APPLICATIONS  ·  v0.1", 11f, cyan))
        header.addView(titles)
        add(root, header)

        val overview = card("网络状态")
        statusText = label("检测中…", 20f, cyan, true)
        add(overview, statusText, 12)
        detailText = label("", 14f, muted)
        add(overview, detailText, 8)
        add(overview, action("刷新网络信息") { updateNetworkInfo() }, 12)

        val latency = card("HTTPS 延迟测试")
        add(latency, label("测量 HTTPS 请求往返时间，不是 ICMP Ping。结果会受服务器和网络影响。", 13f, muted), 8)
        pingText = label("尚未测试", 16f, cyan, true)
        add(latency, pingText, 12)
        pingButton = action("开始测试（5 次）") { runLatencyTest() }
        add(latency, pingButton, 12)

        val dns = card("DNS 查询")
        add(dns, label("输入域名，查询系统 DNS 返回的 IP 地址。", 13f, muted), 8)
        dnsInput = EditText(this).apply {
            setSingleLine(true); setText("example.com"); textSize = 16f; setTextColor(Color.WHITE); setHintTextColor(muted)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            backgroundTintList = android.content.res.ColorStateList.valueOf(cyan)
        }
        add(dns, dnsInput, 8)
        dnsOutput = label("尚未查询", 14f, muted)
        add(dns, dnsOutput, 8)
        dnsButton = action("查询 DNS") { runDns() }
        add(dns, dnsButton, 10)

        val settings = card("系统网络工具")
        add(settings, label("打开 Android 网络设置，管理 Wi-Fi、移动网络等连接。", 13f, muted), 8)
        add(settings, action("打开网络设置") {
            try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
            catch (_: Exception) { Toast.makeText(this, "无法打开系统网络设置", Toast.LENGTH_SHORT).show() }
        }, 12)
        add(root, label("隐私：不收集或上传个人信息。测试会连接公共 HTTPS 测试地址。", 12f, muted), 16)
    }

    private fun updateNetworkInfo() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val cap = network?.let { cm.getNetworkCapabilities(it) }
        val props = network?.let { cm.getLinkProperties(it) }
        val type = when {
            cap == null -> "未连接网络"
            cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN 连接"
            cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi 连接"
            cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动网络连接"
            cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网连接"
            else -> "其他网络连接"
        }
        statusText.text = type
        val validated = cap?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val addresses = props?.linkAddresses?.map { it.address.hostAddress ?: "" }?.filter { it.isNotBlank() }?.joinToString(", ") ?: "不可用"
        val dns = props?.dnsServers?.mapNotNull { it.hostAddress }?.joinToString(", ") ?: "不可用"
        val iface = props?.interfaceName ?: "未知"
        detailText.text = "互联网验证：${if (validated) "通过" else "未验证"}\n网络接口：$iface\n本地 IP：$addresses\nDNS 服务器：$dns"
    }

    private fun runLatencyTest() {
        pingButton.isEnabled = false
        pingText.text = "测试中…"
        worker.execute {
            val values = mutableListOf<Long>()
            repeat(5) {
                try {
                    val connection = URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection
                    connection.connectTimeout = 4000; connection.readTimeout = 4000
                    connection.useCaches = false; connection.instanceFollowRedirects = false
                    val start = System.nanoTime()
                    try { connection.connect(); connection.responseCode; values.add((System.nanoTime() - start) / 1_000_000) }
                    finally { connection.disconnect() }
                } catch (_: Exception) { /* Record failure by omitting sample */ }
            }
            val result = if (values.isEmpty()) "测试失败：5 次请求均未成功" else {
                val avg = values.average().roundToInt()
                val jitter = if (values.size < 2) 0 else values.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().roundToInt()
                "平均：${avg} ms  ·  抖动：${jitter} ms\n成功：${values.size}/5  ·  失败：${5-values.size}/5"
            }
            runOnUiThread { if (!destroyed) { pingText.text = result; pingButton.isEnabled = true } }
        }
    }

    private fun runDns() {
        val domain = dnsInput.text.toString().trim()
        if (domain.isBlank() || domain.length > 253 || !domain.matches(Regex("[A-Za-z0-9.-]+"))) {
            dnsOutput.text = "请输入有效域名（例如 example.com）"; return
        }
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(dnsInput.windowToken, 0)
        dnsButton.isEnabled = false
        dnsOutput.text = "查询中…"
        worker.execute {
            val result = try {
                InetAddress.getAllByName(domain).mapNotNull { it.hostAddress }.distinct().joinToString("\n").ifBlank { "未返回地址" }
            } catch (e: Exception) { "查询失败：${e.javaClass.simpleName}" }
            runOnUiThread { if (!destroyed) { dnsOutput.text = result; dnsButton.isEnabled = true } }
        }
    }

    override fun onDestroy() { destroyed = true; worker.shutdownNow(); super.onDestroy() }
}

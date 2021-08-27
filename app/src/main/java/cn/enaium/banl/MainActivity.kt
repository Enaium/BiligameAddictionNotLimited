package cn.enaium.banl

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.github.monkeywie.proxyee.crt.CertUtil
import com.github.monkeywie.proxyee.exception.HttpProxyExceptionHandle
import com.github.monkeywie.proxyee.intercept.HttpProxyInterceptInitializer
import com.github.monkeywie.proxyee.intercept.HttpProxyInterceptPipeline
import com.github.monkeywie.proxyee.intercept.common.CertDownIntercept
import com.github.monkeywie.proxyee.intercept.common.FullRequestIntercept
import com.github.monkeywie.proxyee.intercept.common.FullResponseIntercept
import com.github.monkeywie.proxyee.server.HttpProxyCACertFactory
import com.github.monkeywie.proxyee.server.HttpProxyServer
import com.github.monkeywie.proxyee.server.HttpProxyServerConfig
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.handler.codec.http.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.Charset
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())

        val logText = findViewById<TextView>(R.id.log)

        fun openUrl(url: String) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        val versionTextView = findViewById<TextView>(R.id.version)
        versionTextView.setTextColor(Color.BLUE)
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        versionTextView.append(versionName)

        versionTextView.setOnClickListener {
            log("正在检测...")
            Thread {
                try {
                    val url = URL("https://api.github.com/repos/Enaium/BiligameAddictionNotLimited/releases")
                    val json = String(url.openStream().readBytes())
                    val jsonArray = JSONArray(json)
                    val currentVersion = JSONObject(jsonArray[0].toString()).getString("tag_name")

                    if (!versionName.equals(currentVersion)) {
                        log("发现新版本:${currentVersion}", LogType.WARNING)
                        runOnUiThread {
                            versionTextView.text = "最版本:${currentVersion}点击下载"
                            versionTextView.setTextColor(Color.RED)
                            versionTextView.setOnClickListener {
                                openUrl("https://github.com/Enaium/BiligameAddictionNotLimited/releases")
                            }
                        }
                    } else {
                        log("是最新版本")
                    }
                } catch (t: Throwable) {
                    log("检测失败", LogType.ERROR)
                    t.log()
                }
            }.start()
        }

        val config = getSharedPreferences("config", MODE_PRIVATE)
        val configEdit = config.edit()
        val hostEditText = findViewById<EditText>(R.id.host)
        val portHostEditText = findViewById<EditText>(R.id.port)
        hostEditText.setText(config.getString("host", "127.0.0.1"))
        portHostEditText.setText(config.getInt("port", 25560).toString())

        findViewById<Button>(R.id.github).setOnClickListener {
            openUrl("https://github.com/Enaium")
        }

        findViewById<Button>(R.id.donate).setOnClickListener {
            openUrl("https://donate.enaium.cn/")
        }

        findViewById<Button>(R.id.clear).setOnClickListener {
            logText.text = ""
        }



        findViewById<Button>(R.id.saveCert).setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1
                )
            }

            log("正在保存...")
            Thread {
                val fw = FileWriter(File("/storage/emulated/0/ca.crt"))
                val br = BufferedReader(InputStreamReader(resources.assets.open("ca.crt")))
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    fw.write(line)
                    fw.write("\n")
                }
                fw.close()
            }.start()
            log("保存成功(内部储存)")
        }

        val outError = findViewById<CheckBox>(R.id.outError)
        outError.isChecked = config.getBoolean("outError", false)
        outError.setOnCheckedChangeListener { _, selected ->
            configEdit.putBoolean("outError", selected)
            configEdit.apply()
        }

        val outRequestURI = findViewById<CheckBox>(R.id.outRequestURI)
        outRequestURI.isChecked = config.getBoolean("outRequestURI", false)
        outRequestURI.setOnCheckedChangeListener { _, selected ->
            configEdit.putBoolean("outRequestURI", selected)
            configEdit.apply()
        }

        val startButton = findViewById<Button>(R.id.start)
        startButton.setOnClickListener {
            try {
                if (!hostEditText.text.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))) {
                    log("请输入有效Host", LogType.ERROR)
                    return@setOnClickListener
                }

                val host = hostEditText.text.toString()
                val port = portHostEditText.text.toString().toShort().toInt()

                configEdit.putString("host", host)
                configEdit.putInt("port", port)//先检测是否为short再转为int
                configEdit.apply()

                hostEditText.visibility = View.GONE
                portHostEditText.visibility = View.GONE
                startButton.visibility = View.GONE

                log("等待启动中...")
                Thread {
                    val httpProxyServerConfig = HttpProxyServerConfig()
                    httpProxyServerConfig.isHandleSsl = true
                    val cert = object : HttpProxyCACertFactory {
                        override fun getCACert(): X509Certificate {
                            return CertUtil.loadCert(
                                resources.assets.open("ca.crt")
                            )
                        }

                        override fun getCAPriKey(): PrivateKey {
                            return CertUtil.loadPriKey(
                                resources.assets.open(
                                    "ca_private.der"
                                )
                            )
                        }
                    }

                    val serverConfig =
                        HttpProxyServer()
                            .proxyInterceptInitializer(object : HttpProxyInterceptInitializer() {
                                override fun init(pipeline: HttpProxyInterceptPipeline) {
                                    pipeline.addLast(object : FullRequestIntercept() {
                                        override fun match(
                                            httpRequest: HttpRequest, pipeline: HttpProxyInterceptPipeline
                                        ): Boolean {
                                            if (outRequestURI.isChecked) {
                                                log("URI:${httpRequest.uri()}")
                                            }
                                            return httpRequest.endsWith("api/client/login") || httpRequest.endsWith("app/v2/time/heartbeat")
                                        }

                                        override fun handleRequest(
                                            httpRequest: FullHttpRequest,
                                            pipeline: HttpProxyInterceptPipeline
                                        ) {

                                            httpRequest.clear()

                                            if (httpRequest.endsWith("api/client/login")) {
                                                log("登录不限制成功")
                                            }

                                            if (httpRequest.endsWith("app/v2/time/heartbeat")) {
                                                log("时间不计时成功")
                                            }
                                        }
                                    })

                                    pipeline.addLast(object : FullResponseIntercept() {
                                        override fun match(
                                            httpRequest: HttpRequest,
                                            httpResponse: HttpResponse,
                                            pipeline: HttpProxyInterceptPipeline
                                        ): Boolean {
                                            return httpRequest.endsWith("api/client/can_pay") || httpRequest.endsWith("api/client/user.info")
                                        }

                                        override fun handleResponse(
                                            httpRequest: HttpRequest,
                                            httpResponse: FullHttpResponse,
                                            pipeline: HttpProxyInterceptPipeline
                                        ) {

                                            if (httpRequest.endsWith("api/client/can_pay")) {
                                                httpResponse.setContent("""{code":0,"message":"ok","is_adult":1,"server_message":""}""")
                                                log("充值不限制成功")
                                            }

                                            if (httpRequest.endsWith("api/client/user.info")) {
                                                httpResponse.setContent("""{"realname_verified":1,"code":0,"uname":"不限制登录成功"}""")
                                            }
                                        }
                                    })
                                }
                            }).caCertFactory(cert).httpProxyExceptionHandle(object : HttpProxyExceptionHandle() {
                                override fun beforeCatch(clientChannel: Channel, cause: Throwable) {
                                    if (outRequestURI.isSelected) {
                                        cause.log()
                                    }
                                }
                            }).serverConfig(httpProxyServerConfig)

                    serverConfig
                        .startAsync(
                            host,
                            port
                        ).whenComplete { _, throwable ->
                            if (throwable != null) {
                                log("启动失败", LogType.ERROR)
                                throwable.log()
                            } else {
                                log("启动成功 Host:${host} Port:${port}")
                            }
                        }
                }.start()
            } catch (t: Throwable) {
                t.log()
            }
        }
    }

    fun log(msg: String, log: LogType = LogType.INFO) {
        Thread {
            runOnUiThread {
                val time = "[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}]"

                val type = when (log) {
                    LogType.WARNING -> "[WARNING]"
                    LogType.ERROR -> "[ERROR]"
                    else -> "[INFO]"
                }

                val spannableString = SpannableString("$time$type$msg\n")
                spannableString.setSpan(
                    ForegroundColorSpan(
                        when (log) {
                            LogType.WARNING -> Color.YELLOW
                            LogType.ERROR -> Color.RED
                            else -> Color.GREEN
                        }
                    ), time.length, time.length + type.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                findViewById<TextView>(R.id.log).append(spannableString)

                val scrollView = findViewById<ScrollView>(R.id.logScroll)
                scrollView.post {
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
        }.start()
    }

    fun Throwable.log() {
        if (cause != null) {
            log(cause!!.stackTraceToString(), LogType.ERROR)
        }
    }

    fun FullHttpRequest.clear() {
        content().clear()
        content().clear()
    }

    fun HttpRequest.endsWith(text: String): Boolean {
        return uri().endsWith(text)
    }

    fun FullHttpResponse.setContent(msg: String) {
        content().clear()
        content().writeBytes(Unpooled.wrappedBuffer(msg.toByteArray()))
    }
}
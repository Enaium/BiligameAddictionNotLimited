package cn.enaium.banl

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.github.monkeywie.proxyee.crt.CertUtil
import com.github.monkeywie.proxyee.exception.HttpProxyExceptionHandle
import com.github.monkeywie.proxyee.intercept.HttpProxyInterceptInitializer
import com.github.monkeywie.proxyee.intercept.HttpProxyInterceptPipeline
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
import java.io.*
import java.net.URL
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

        fun checkUpdate() {
            log("正在检测...")
            Thread {
                try {
                    val url = URL("https://api.github.com/repositories/395314078/releases")
                    val json = String(url.openStream().readBytes())
                    val jsonArray = JSONArray(json)
                    val currentVersion = JSONObject(jsonArray[0].toString()).getString("tag_name")

                    if (!versionName.equals(currentVersion)) {
                        log("发现新版本:${currentVersion}", LogType.WARNING)
                        runOnUiThread {
                            versionTextView.text = "最版本:${currentVersion}点击下载"
                            versionTextView.setTextColor(Color.RED)
                            versionTextView.setOnClickListener {
                                openUrl("https://github.com/FuckAntiAddiction/BiligameAddictionNotLimited/releases")
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

        if (config.isOpenCheckUpdate()) {
            checkUpdate()
        }


        versionTextView.setOnClickListener {
            checkUpdate()
        }

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
                try {
                    val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues()
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "ca.crt")
                        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/x-x509-ca-cert")
                        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                        contentResolver.openOutputStream(uri!!)
                    } else {
                        FileOutputStream("/storage/emulated/0/${Environment.DIRECTORY_DOWNLOADS}/ca.crt")
                    }


                    val br = BufferedReader(InputStreamReader(resources.assets.open("ca.crt")))
                    var line: String?
                    while (br.readLine().also { line = it } != null) {
                        outputStream!!.write(line!!.toByteArray())
                        outputStream.write("\n".toByteArray())
                    }
                    outputStream!!.close()
                    log("保存成功(系统下载目录)")
                } catch (t: Throwable) {
                    log("保存失败", LogType.ERROR)
                    t.log()
                }
            }.start()

        }

        findViewById<Button>(R.id.setting).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.information).setOnClickListener {
            startActivity(Intent(this, InformationActivity::class.java))
        }

        val startButton = findViewById<Button>(R.id.start)
        startButton.setOnClickListener {
            try {

                val host = config.getHost()
                val port = config.getPort()

                startButton.visibility = View.INVISIBLE

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
                                            if (config.isOutRequestURI()) {
                                                log("URI:${httpRequest.headers()[HttpHeaderNames.HOST]}${httpRequest.uri()}")
                                            }
                                            return httpRequest.endsWith("app/v2/time/heartbeat")
                                                    || httpRequest.endsWith("api/client/session.renewal")
                                                    || httpRequest.endsWith("api/client/notice.list")
                                        }

                                        override fun handleRequest(
                                            httpRequest: FullHttpRequest,
                                            pipeline: HttpProxyInterceptPipeline
                                        ) {

                                            httpRequest.clear()

                                            if (httpRequest.endsWith("app/v2/time/heartbeat")) {
                                                log("时间不计时到时间防踢出成功")
                                            }

                                            if (httpRequest.endsWith("api/client/session.renewal")
                                                || httpRequest.endsWith("api/client/notice.list")
                                            ) {
                                                log("登录不限制成功")
                                            }
                                        }
                                    })

                                    pipeline.addLast(object : FullResponseIntercept() {
                                        override fun match(
                                            httpRequest: HttpRequest,
                                            httpResponse: HttpResponse,
                                            pipeline: HttpProxyInterceptPipeline
                                        ): Boolean {
                                            return httpRequest.endsWith("api/client/can_pay")
                                                    || httpRequest.endsWith("api/client/user.info")
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
                                                httpResponse.setContent("""{"realname_verified":1,"code":0,"uname":"${config.getUname()}"}""")
                                            }


                                        }
                                    })
                                }
                            }).caCertFactory(cert).httpProxyExceptionHandle(object : HttpProxyExceptionHandle() {
                                override fun beforeCatch(clientChannel: Channel, cause: Throwable) {
                                    if (config.isOutError()) {
                                        cause.log()
                                    }
                                }
                            }).serverConfig(httpProxyServerConfig)

                    serverConfig
                        .startAsync(
                            host,
                            port
                        ).whenComplete { _, throwable ->
                            runOnUiThread {
                                if (throwable != null) {
                                    log("启动失败", LogType.ERROR)
                                    throwable.log()
                                    startButton.visibility = View.VISIBLE
                                } else {
                                    log("启动成功 Host:${host} Port:${port}")
                                    startButton.visibility = View.GONE
                                }
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
        log(stackTraceToString(), LogType.ERROR)
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

    private fun SharedPreferences.isOutError(): Boolean {
        return getBoolean("outError", false)
    }

    private fun SharedPreferences.isOutRequestURI(): Boolean {
        return getBoolean("outRequestURI", false)
    }

    private fun SharedPreferences.getHost(): String {
        return getString("host", "127.0.0.1")!!
    }

    private fun SharedPreferences.getPort(): Int {
        return getInt("port", 25560)
    }

    private fun SharedPreferences.getUname(): String {
        return getString("uname", "不限制登录成功")!!
    }

    private fun SharedPreferences.isOpenCheckUpdate(): Boolean {
        return getBoolean("openCheckUpdate", true)
    }
}
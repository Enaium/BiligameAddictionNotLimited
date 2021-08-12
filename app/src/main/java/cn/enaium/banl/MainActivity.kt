package cn.enaium.banl

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
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
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())

        val logText = findViewById<TextView>(R.id.log)
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

                    logText.append(spannableString)

                    val scrollView = findViewById<ScrollView>(R.id.logScroll)
                    scrollView.post {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                    }
                }
            }.start()
        }

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
                    log(t.message.toString(), LogType.ERROR)
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

        val startButton = findViewById<Button>(R.id.start)
        startButton.setOnClickListener {
            try {

                if (!hostEditText.text.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))) {
                    log("请输入有效Host", LogType.ERROR)
                    return@setOnClickListener
                }

                configEdit.putString("host", hostEditText.text.toString())
                configEdit.putInt("port", portHostEditText.text.toString().toShort().toInt())//先检测是否为short再转为int
                configEdit.apply()

                hostEditText.visibility = View.GONE
                portHostEditText.visibility = View.GONE
                startButton.visibility = View.GONE

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
                                    pipeline.addLast(CertDownIntercept(cert))
                                    pipeline.addLast(object : FullRequestIntercept() {
                                        override fun match(
                                            httpRequest: HttpRequest, pipeline: HttpProxyInterceptPipeline
                                        ): Boolean {
                                            return httpRequest.uri().endsWith("api/client/login") || httpRequest.uri()
                                                .endsWith("app/v2/time/heartbeat")
                                        }

                                        override fun handleRequest(
                                            httpRequest: FullHttpRequest,
                                            pipeline: HttpProxyInterceptPipeline
                                        ) {

                                            httpRequest.headers().clear()
                                            httpRequest.content().clear()

                                            if (httpRequest.uri().endsWith("api/client/login")) {
                                                log("登录不限制成功")
                                            }

                                            if (httpRequest.uri().endsWith("app/v2/time/heartbeat")) {
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
                                            return httpRequest.uri().endsWith("api/client/can_pay")
                                        }

                                        override fun handleResponse(
                                            httpRequest: HttpRequest,
                                            httpResponse: FullHttpResponse,
                                            pipeline: HttpProxyInterceptPipeline?
                                        ) {
                                            httpResponse.content().clear()
                                            httpResponse.content()
                                                .writeBytes(Unpooled.wrappedBuffer("""{code":0,"message":"ok","is_adult":1,"server_message":""""".toByteArray()))
                                            log("充值不限制成功")
                                        }
                                    })
                                }
                            }).caCertFactory(cert).httpProxyExceptionHandle(object : HttpProxyExceptionHandle() {
                                override fun startCatch(e: Throwable) {
                                    log(e.stackTraceToString(), LogType.ERROR)
                                }
                            }).serverConfig(httpProxyServerConfig)
                    try {
                        serverConfig
                            .start(
                                hostEditText.text.toString(),
                                portHostEditText.text.toString().toShort().toInt()
                            )
                    } catch (t: Throwable) {
                        log("启动失败", LogType.ERROR)
                        log(t.stackTraceToString(), LogType.ERROR)
                    }
                }.start()
            } catch (t: Throwable) {
                log(t.stackTraceToString(), LogType.ERROR)
            }
        }
    }
}
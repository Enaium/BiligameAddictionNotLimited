package cn.enaium.banl

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    @SuppressLint("CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        val config = getSharedPreferences("config", MODE_PRIVATE)
        val configEdit = config.edit()

        val unameEditText = findViewById<EditText>(R.id.uname)
        val hostEditText = findViewById<EditText>(R.id.host)
        val portHostEditText = findViewById<EditText>(R.id.port)

        hostEditText.setText(config.getString("host", "127.0.0.1"))
        portHostEditText.setText(config.getInt("port", 25560).toString())
        unameEditText.setText(config.getString("uname", "不限制登录成功"))

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

        findViewById<Button>(R.id.saveConfig).setOnClickListener {
            if (!hostEditText.text.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))) {
                Toast.makeText(this, "请输入有效Host", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val host = hostEditText.text.toString()
            val port = try {
                portHostEditText.text.toString().toShort().toInt()
            } catch (t: Throwable) {
                Toast.makeText(this, "请输入有效Port", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            configEdit.putString("uname", unameEditText.text.toString())
            configEdit.putString("host", host)
            configEdit.putInt("port", port)//先检测是否为short再转为int
            configEdit.apply()

            Toast.makeText(this, "保存完成", Toast.LENGTH_SHORT).show()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }
}
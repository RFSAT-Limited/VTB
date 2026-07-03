package com.rfsat.vtb.log

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.rfsat.vtb.databinding.ActivityLogBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private val listener: () -> Unit = { runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refresh()
        Logger.addListener(listener)

        binding.btnSaveLog.setOnClickListener { saveLogToFile()?.let {
            Toast.makeText(this, "Saved: ${it.name}", Toast.LENGTH_SHORT).show()
        } }
        binding.btnShareLog.setOnClickListener { shareLog() }
        binding.btnClearLog.setOnClickListener { Logger.clear() }
    }

    override fun onDestroy() {
        Logger.removeListener(listener)
        super.onDestroy()
    }

    private fun refresh() {
        binding.tvLog.text = Logger.asText().ifBlank { "(log is empty)" }
        binding.scrollLog.post { binding.scrollLog.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun saveLogToFile(): File? {
        return try {
            val dir = File(getExternalFilesDir(null), "logs").apply { mkdirs() }
            val name = "vtb_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt"
            val file = File(dir, name)
            file.writeText(Logger.asText())
            file
        } catch (t: Throwable) {
            Logger.e("LogActivity", "Failed to save log", t)
            null
        }
    }

    private fun shareLog() {
        val file = saveLogToFile() ?: run {
            Toast.makeText(this, "Could not save log for sharing.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share VTB log"))
    }
}

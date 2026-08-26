package ru.wizand.safeorbit.presentation.client

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.integration.android.IntentIntegrator

class QrScanActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scanner = IntentIntegrator(this).apply {
            setPrompt("Отсканируйте QR с ID сервера и pairing токеном")
            setBeepEnabled(true)
            setOrientationLocked(true)
        }
        scanner.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents != null) {
            // Парсим QR-код в формате serverId|pairingToken
            val parts = result.contents.split("|")
            if (parts.size == 2) {
                val serverId = parts[0]
                val pairingToken = parts[1]
                
                val intent = Intent().apply {
                    putExtra("server_id", serverId)
                    putExtra("pairing_token", pairingToken)
                }
                setResult(Activity.RESULT_OK, intent)
            } else {
                // Если формат не соответствует, передаем как есть
                val intent = Intent().apply {
                    putExtra("qr_result", result.contents)
                }
                setResult(Activity.RESULT_OK, intent)
            }
        }
        finish()
        super.onActivityResult(requestCode, resultCode, data)
    }
}

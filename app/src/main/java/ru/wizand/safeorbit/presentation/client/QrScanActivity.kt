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
            setPrompt("Отсканируйте QR с ID и кодом")
            setBeepEnabled(true)
            setOrientationLocked(true)
        }
        scanner.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents != null) {
            val intent = Intent().apply {
                putExtra("qr_result", result.contents)
            }
            setResult(Activity.RESULT_OK, intent)
        }
        finish()
        super.onActivityResult(requestCode, resultCode, data)
    }
}

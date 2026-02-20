package com.payroc.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.payroc.terminal.navigation.KoardNavigation
import com.payroc.terminal.ui.theme.PayrocTheme
import com.koardlabs.merchant.sdk.KoardMerchantSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PayrocTheme {
                Surface {
                    KoardNavigation(modifier = Modifier)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        lifecycleScope.launch(Dispatchers.IO) {
            // registerActivityForNFC
            Timber.v("Registering activity for NFC")
            KoardMerchantSdk.getInstance().registerActivityForNfc(this@MainActivity)
        }
    }

    override fun onStop() {
        super.onStop()

        lifecycleScope.launch(Dispatchers.IO) {
            // unregisterActivityForNFC
            Timber.v("Unregistering activity for NFC")
            KoardMerchantSdk.getInstance().unregisterActivityForNfc(this@MainActivity)
        }
    }
}

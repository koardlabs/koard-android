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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private val nfcMutex = Mutex()

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

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch(Dispatchers.IO) {
            nfcMutex.withLock {
                Timber.v("Registering activity for NFC")
                KoardMerchantSdk.getInstance().registerActivityForNfc(this@MainActivity)
            }
        }
    }

    override fun onPause() {
        lifecycleScope.launch(Dispatchers.IO) {
            nfcMutex.withLock {
                Timber.v("Unregistering activity for NFC")
                KoardMerchantSdk.getInstance().unregisterActivityForNfc(this@MainActivity)
            }
        }
        super.onPause()
    }
}

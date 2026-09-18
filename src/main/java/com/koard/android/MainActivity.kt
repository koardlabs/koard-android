package com.koard.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.koard.android.navigation.KoardNavigation
import com.koard.android.ui.theme.KoardAndroidSDKTheme
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
    @Volatile private var isResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KoardAndroidSDKTheme {
                Surface {
                    KoardNavigation(modifier = Modifier)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        refreshNfcRegistration()
    }

    override fun onPause() {
        isResumed = false
        lifecycleScope.launch {
            (application as DemoApplication).withInitializedSdk { sdk ->
                Timber.v("Unregistering activity for NFC")
                sdk.unregisterActivityForNfc(this@MainActivity)
            }
        }
        super.onPause()
    }

    /** Reattach the currently resumed Activity after Settings replaces the SDK. */
    fun refreshNfcRegistration() {
        lifecycleScope.launch {
            (application as DemoApplication).withInitializedSdk { sdk ->
                if (isResumed) {
                    Timber.v("Registering activity for NFC")
                    sdk.registerActivityForNfc(this@MainActivity)
                }
            }
        }
    }
}

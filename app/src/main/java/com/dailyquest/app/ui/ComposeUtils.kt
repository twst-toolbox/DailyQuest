@'
package com.dailyquest.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberContext(): Context {
    return LocalContext.current
}

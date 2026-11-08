package dev.pivisolutions.dictus.localcleanup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LocalCleanupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(LocalCleanup.ready(context)) }
    var enabled by remember { mutableStateOf(LocalCleanup.enabled(context)) }
    var busy by remember { mutableStateOf(false) }
    var percent by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(20.dp)) {
        Text(stringResource(R.string.local_cleanup_title))
        Text(stringResource(R.string.local_cleanup_description))
        if (!ready) Button(enabled = !busy && !LocalCleanup.downloading.get(), onClick = {
            scope.launch {
                busy = true; error = null
                try {
                    LocalCleanup.download(context) { percent = it }
                    ready = LocalCleanup.ready(context)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (failure: Exception) { error = failure.message
                } finally { busy = false }
            }
        }) { Text(stringResource(R.string.local_cleanup_download)) }
        if (busy) {
            LinearProgressIndicator(progress = { percent / 100f })
            Text(stringResource(R.string.local_cleanup_progress, percent))
        }
        Row {
            Switch(checked = enabled, enabled = ready && !busy, onCheckedChange = { enabled = it; LocalCleanup.enable(context, it) })
            Text(stringResource(R.string.local_cleanup_enable), Modifier.padding(12.dp))
        }
        error?.let { Text(it) }
    }
}

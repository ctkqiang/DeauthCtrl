package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.manager.JammerLogEntry
import xin.ctkqiang.deauthctrl.viewmodel.BluetoothJammerViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BluetoothJammerScreen(viewModel: BluetoothJammerViewModel) {
    val log by viewModel.log.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val error by viewModel.error.collectAsState()
    val listState = rememberLazyListState()
    val tf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(10.dp)) {
        Text("> BT 全频压制", fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary)
        Text("BLE 泛洪 + 经典蓝牙 Inquiry 查询泛洪", fontFamily = FontFamily.Monospace,
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        Button(onClick = { if (isRunning) viewModel.stop() else viewModel.start() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary)) {
            Text(if (isRunning) "[ 停止蓝牙压制 ]" else "[ 开始蓝牙全频压制 ]",
                fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        }

        error?.let { Spacer(Modifier.height(4.dp)); Text("! $it", fontFamily = FontFamily.Monospace,
            fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }

        if (isRunning) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline)
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("> 压制日志 [${log.size}]", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { viewModel.clearLog() }, contentPadding = PaddingValues(0.dp)) {
                Text("清空", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surface).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (log.isEmpty()) { item { Text("等待攻击…", fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)) } }
            items(log) { e -> JammerLogRow(e, tf) }
        }
    }
}

@Composable
private fun JammerLogRow(e: JammerLogEntry, tf: SimpleDateFormat) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text("[${e.module}]", fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(70.dp))
        Text(tf.format(Date(e.timestamp)), fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        Text(e.message, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

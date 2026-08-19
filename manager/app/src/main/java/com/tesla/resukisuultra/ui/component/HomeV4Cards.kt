package com.tesla.resukisuultra.ui.component
import com.tesla.resukisuultra.ui.theme.ContinuousCornerShape

import android.content.Context
import android.os.BatteryManager
import android.os.StatFs
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * FolkPatch HomeV4 风格卡片:
 * HeroStatusCard (呼吸动画大状态卡) + DeviceStatusCard (设备状态圈) + StorageInfoCard
 */

// ===== 数据收集 =====

private object SystemInfoCollector {
    data class DeviceStatus(
        val batteryTemp: Float = 0f,
        val cpuUsage: Int = 0,
        val batteryLevel: Int = 0,
    )

    data class StorageStatus(
        val storageUsed: Long = 0L,
        val storageTotal: Long = 0L,
    )

    suspend fun collectDeviceStatus(context: Context): DeviceStatus = withContext(Dispatchers.IO) {
        runCatching {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val batteryTemp = (bm?.getIntProperty(2) ?: 0) / 10f
            val batteryLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
            DeviceStatus(
                batteryTemp = batteryTemp,
                cpuUsage = readCpuUsage(),
                batteryLevel = batteryLevel,
            )
        }.getOrDefault(DeviceStatus())
    }

    suspend fun collectStorageStatus(): StorageStatus = withContext(Dispatchers.IO) {
        runCatching {
            val stat = StatFs("/data")
            val total = stat.totalBytes
            val free = stat.availableBytes
            StorageStatus(
                storageUsed = total - free,
                storageTotal = total,
            )
        }.getOrDefault(StorageStatus())
    }

    private fun readCpuUsage(): Int {
        val first = readProcStat() ?: return 0
        Thread.sleep(350)
        val second = readProcStat() ?: return 0
        val totalDiff = (second.first - first.first).coerceAtLeast(1)
        val idleDiff = (second.second - first.second).coerceAtLeast(0)
        return ((totalDiff - idleDiff) * 100 / totalDiff).coerceIn(0L, 100L).toInt()
    }

    private fun readProcStat(): Pair<Long, Long>? {
        return runCatching {
            val line = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") } ?: return null
            val parts = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (parts.size < 5) return null
            val total = parts.sum()
            val idle = parts[3] + (parts.getOrElse(4) { 0 })
            Pair(total, idle)
        }.getOrNull()
    }
}

// ===== Hero 状态卡 =====

@Composable
fun HeroStatusCard(
    isWorking: Boolean,
    workingText: String,
    modeText: String,
    summaryText: String,
    versionText: String? = null,
    isPermissiveJailbreak: Boolean = false,
    onClickInstall: () -> Unit = {},
    onClickJailbreak: () -> Unit = {},
) {
    // 呼吸动画: 仅 working 态常驻 (非 working 静止 — 省 GPU)
    val breathAlpha = if (isWorking) {
        val infiniteTransition = rememberInfiniteTransition(label = "breathing")
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathAlpha"
        ).value
    } else {
        1f
    }

    // 切换用 spring 物理动画 (灵动跟手, 不生硬)
    val containerColor by animateColorAsState(
        targetValue = when {
            isWorking -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isWorking) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "contentColor"
    )

    // 静态渐变 (颜色由 animateColorAsState 驱动), 呼吸 alpha 走 GPU
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            containerColor,
            containerColor.copy(alpha = 0.8f)
        )
    )

    Card(
        onClick = { if (!isWorking) onClickInstall() },
        shape = ContinuousCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = if (isWorking) breathAlpha else 1f
                }
                .background(gradientBrush)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isWorking) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = contentColor,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = workingText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = contentColor,
                        )
                        if (isWorking) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = ContinuousCornerShape(50),
                                color = contentColor.copy(alpha = 0.22f),
                            ) {
                                Text(
                                    text = modeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = contentColor,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.85f),
                    )
                    if (versionText != null && isWorking) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = versionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f),
                        )
                    }
                }
                if (!isWorking && isPermissiveJailbreak) {
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = onClickJailbreak,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text("Jailbreak")
                    }
                }
            }
        }
    }
}

// ===== 设备状态卡 =====

@Composable
fun DeviceStatusCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var deviceStatus by remember { mutableStateOf(SystemInfoCollector.DeviceStatus()) }

    LaunchedEffect(Unit) {
        while (true) {
            deviceStatus = SystemInfoCollector.collectDeviceStatus(context)
            delay(5000)
        }
    }

    TonalLikeCard(
        title = "设备状态",
        icon = Icons.Outlined.Memory,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatusCircle(
                value = "${deviceStatus.batteryTemp}°C",
                label = "电池温度",
                progress = (deviceStatus.batteryTemp / 50f).coerceIn(0f, 1f),
                color = MaterialTheme.colorScheme.primary
            )
            StatusCircle(
                value = "${deviceStatus.cpuUsage}%",
                label = "CPU 使用率",
                progress = (deviceStatus.cpuUsage / 100f).coerceIn(0f, 1f),
                color = MaterialTheme.colorScheme.secondary
            )
            StatusCircle(
                value = "${deviceStatus.batteryLevel}%",
                label = "电量",
                progress = (deviceStatus.batteryLevel / 100f).coerceIn(0f, 1f),
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun StatusCircle(
    value: String,
    label: String,
    progress: Float,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(80.dp)
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = color.copy(alpha = 0.2f),
                strokeWidth = 8.dp,
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = color,
                strokeWidth = 8.dp,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ===== 存储卡 =====

@Composable
fun StorageInfoCard(modifier: Modifier = Modifier) {
    var storageStatus by remember { mutableStateOf(SystemInfoCollector.StorageStatus()) }

    LaunchedEffect(Unit) {
        while (true) {
            storageStatus = SystemInfoCollector.collectStorageStatus()
            delay(5000)
        }
    }

    TonalLikeCard(
        title = "存储空间",
        icon = Icons.Outlined.SdStorage,
        modifier = modifier,
    ) {
        StorageProgressBar(
            label = "内部存储",
            used = storageStatus.storageUsed,
            total = storageStatus.storageTotal,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StorageProgressBar(
    label: String,
    used: Long,
    total: Long,
    color: Color
) {
    val progress = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    val usedText = formatBytes(used)
    val totalText = formatBytes(total)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$usedText / $totalText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format("%.1f %s", value, units[unit])
}

// ===== TonalLikeCard (标题 + 图标 + 分割线 + 内容) =====

@Composable
fun TonalLikeCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ContinuousCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            content()
        }
    }
}

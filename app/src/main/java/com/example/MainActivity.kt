package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.BilliardsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BilliardsTheme {
                MainScreen(
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestAccessibility = { openAccessibilitySettings() },
                    onStartService = { startOverlayService() },
                    onStopService = { stopOverlayService() }
                )
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "ابحث عن 'Chakib Aiming Wheel' وقم بتفعيلها", Toast.LENGTH_LONG).show()
    }

    private fun startOverlayService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "يرجى منح إذن الظهور فوق التطبيقات أولاً", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }
        OverlayService.start(this)
        Toast.makeText(this, "تم تشغيل Chakib Aiming Wheel فوق اللعبة!", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlayService() {
        OverlayService.stop(this)
        Toast.makeText(this, "تم إيقاف المساعد العائم", Toast.LENGTH_SHORT).show()
    }
}

fun checkIsAccessibilityEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(context.packageName)
}

@Composable
fun MainScreen(
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }

    var isAccessibilityEnabled by remember {
        mutableStateOf(checkIsAccessibilityEnabled(context))
    }

    var isServiceRunning by remember {
        mutableStateOf(OverlayService.isRunning || AimAccessibilityService.isRunning)
    }

    // Live configs
    var config by remember { mutableStateOf(AimOverlayConfig.load(context)) }
    var table by remember { mutableStateOf(AimOverlayConfig.loadCalibration(context)) }

    // Tab / Mode
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Home, 1 = Table Calibration ("القيس"), 2 = Settings

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
                isAccessibilityEnabled = checkIsAccessibilityEnabled(context)
                isServiceRunning = OverlayService.isRunning || AimAccessibilityService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF041C11),
                        Color(0xFF062919),
                        Color(0xFF0B1410)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            HeaderSection()

            Spacer(modifier = Modifier.height(14.dp))

            // Navigation Tabs (الرئيسية / عيار القيس / الإعدادات)
            TabSelector(selectedTab = selectedTab, onTabSelected = { selectedTab = it })

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedTab) {
                0 -> {
                    // 1. Accessibility Service Card (The Ultimate Solution to Android 12+ Freeze)
                    AccessibilityFeatureCard(
                        isEnabled = isAccessibilityEnabled,
                        onOpenSettings = onRequestAccessibility
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Standard Overlay Controls
                    ServiceControlCard(
                        isServiceRunning = isServiceRunning,
                        hasPermission = hasOverlayPermission,
                        onStart = {
                            onStartService()
                            isServiceRunning = true
                        },
                        onStop = {
                            onStopService()
                            isServiceRunning = false
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Clear Guide on how to avoid freezing in both modes
                    TouchPassThroughGuideCard()

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. Live Interactive Preview
                    LiveTablePreviewCard(config = config, table = table)
                }
                1 -> {
                    // TABLE CALIBRATION ("القيس على الطاولة بحرية")
                    TableCalibrationCard(
                        table = table,
                        onTableChanged = { newTable ->
                            table = newTable
                            AimOverlayConfig.saveCalibration(context, newTable)
                        }
                    )
                }
                2 -> {
                    // AIM & GUIDELINES CUSTOMIZATION
                    SettingsCard(
                        config = config,
                        onConfigChanged = { newConfig ->
                            config = newConfig
                            AimOverlayConfig.save(context, newConfig)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "chakib aiming wheel biliardo",
                color = Color(0xFF6EE7B7),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "مساعد خطوط الرماية وحفر الطاولة لـ 8 Ball Pool",
                color = Color(0xFFA7F3D0).copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF00FF66).copy(alpha = 0.2f))
                .border(1.5.dp, Color(0xFF00FF66), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("8", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
fun TabSelector(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F2D1F))
            .border(1.dp, Color(0xFF1B4D36), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TabButton(
            title = "🎯 التحكم",
            isSelected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            modifier = Modifier.weight(1f)
        )
        TabButton(
            title = "📐 عيار القيس",
            isSelected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            modifier = Modifier.weight(1f)
        )
        TabButton(
            title = "⚙️ الإعدادات",
            isSelected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TabButton(title: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF00FF66).copy(alpha = 0.25f) else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) Color(0xFF00FF66) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else Color(0xFFA7F3D0).copy(alpha = 0.7f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

@Composable
fun AccessibilityFeatureCard(
    isEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isEnabled) Color(0xFF0A2E1D) else Color(0xFF1E2818)),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isEnabled) Color(0xFF00FF66) else Color(0xFFFFD700)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛡️", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "وضع إمكانية الوصول (الحل النهائي لمنع التجميد)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isEnabled) "● مفعّل ونشط (بدون تجميد للمس نهائياً)" else "○ موصى به لتجاوز حماية أندرويد 12+",
                            color = if (isEnabled) Color(0xFF00FF66) else Color(0xFFFFD700),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "يستخدم هذا الوضع نافذة TYPE_ACCESSIBILITY_OVERLAY الموثوقة نظامياً. هذا هو النظام المعتمد في كل برامج التصويب الاحترافية (Aiming Master / BitAim) ولا يمكن لنظام أندرويد أو للعبة 8 Ball Pool تجميد الشاشة أو منع لمس العصا نهائياً!",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEnabled) Color(0xFF14532D) else Color(0xFFFFD700)
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (isEnabled) "إعدادات إمكانية الوصول (مفعّل)" else "تفعيل Chakib Aiming Wheel في إمكانية الوصول",
                    color = if (isEnabled) Color(0xFF6EE7B7) else Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun ServiceControlCard(
    isServiceRunning: Boolean,
    hasPermission: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2D1F)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isServiceRunning) Color(0xFF00FF66) else Color(0xFF1B4D36)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "الوضع العادي (الظهور فوق التطبيقات)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (isServiceRunning) "● نشط فوق الشاشة" else "○ متوقف",
                        color = if (isServiceRunning) Color(0xFF00FF66) else Color.Gray,
                        fontSize = 12.sp
                    )
                }

                if (isServiceRunning) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00FF66).copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("ON", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!isServiceRunning) {
                Button(
                    onClick = onStart,
                    enabled = hasPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FF66),
                        disabledContainerColor = Color(0xFF1B4D36)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_overlay_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تشغيل المساعد العائم",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            } else {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("stop_overlay_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "إيقاف المساعد العائم",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TouchPassThroughGuideCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF082216)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💡", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "طريقة الرماية بدون تجميد الهاتف إطلاقاً:",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• الطريقة 1 (الأسهل والأقوى): فعّل 'إمكانية الوصول' بالأعلى. لن تحتاج لأي نقرة إضافية، ستلعب وتسحب العصا مباشرة وبحرية تامة لأن النافذة تصبح موثوقة من أندرويد.\n\n" +
                        "• الطريقة 2 (الظهور العادي): عندما ترى خط الهدف وتريد ضرب الكرة، اضغط زر 🎮 SHOOT في الشريط العلوي، ستختفي الخطوط مؤقتاً لتسحب العصا وتضرب براحتك، ثم انقر 🎯 MOVE لإظهارها من جديد.",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun TableCalibrationCard(
    table: CalibratedTable,
    onTableChanged: (CalibratedTable) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2D1F)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B4D36))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📐 ضبط القيس (أبعاد الطاولة والجيوب الستة)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "اختر قياس الطاولة ليطابق شاشة هاتفك في لعبة 8 Ball Pool تماماً:",
                color = Color(0xFFA7F3D0).copy(alpha = 0.8f),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onTableChanged(
                            table.copy(leftRatio = 0.08f, rightRatio = 0.92f, topRatio = 0.14f, bottomRatio = 0.86f, isLandscape = true)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("أفقي قياسي (8 Ball)", fontSize = 11.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        onTableChanged(
                            table.copy(leftRatio = 0.04f, rightRatio = 0.96f, topRatio = 0.10f, bottomRatio = 0.90f, isLandscape = true)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("شاشة عريضة", fontSize = 11.sp, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Adjust Left & Right cushions
            Text("المسافة الجانبية (يمين / يسار): ${(table.leftRatio * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
            Slider(
                value = table.leftRatio,
                onValueChange = { newLeft ->
                    onTableChanged(table.copy(leftRatio = newLeft, rightRatio = 1f - newLeft))
                },
                valueRange = 0.02f..0.20f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00FF66),
                    activeTrackColor = Color(0xFF00FF66)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Adjust Top & Bottom cushions
            Text("المسافة العلوية والسفلية: ${(table.topRatio * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
            Slider(
                value = table.topRatio,
                onValueChange = { newTop ->
                    onTableChanged(table.copy(topRatio = newTop, bottomRatio = 1f - newTop))
                },
                valueRange = 0.06f..0.30f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E5FF),
                    activeTrackColor = Color(0xFF00E5FF)
                )
            )
        }
    }
}

@Composable
fun SettingsCard(
    config: AimOverlayConfig,
    onConfigChanged: (AimOverlayConfig) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2D1F)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B4D36))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "⚙️ خيارات خطوط الليزر والتصويب",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Ball Radius Slider
            Text(
                text = "حجم الكرية المستهدفة: ${config.ballRadiusDp.toInt()} dp",
                color = Color.White,
                fontSize = 13.sp
            )
            Slider(
                value = config.ballRadiusDp,
                onValueChange = { onConfigChanged(config.copy(ballRadiusDp = it)) },
                valueRange = 18f..40f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00FF66),
                    activeTrackColor = Color(0xFF00FF66)
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Cushion Rebound Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("انعكاس الجدران (Bank Shots)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("إظهار خط ارتداد الكرية من جدران الطاولة", color = Color.Gray, fontSize = 11.sp)
                }
                Switch(
                    checked = config.showRebound,
                    onCheckedChange = { onConfigChanged(config.copy(showRebound = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00FF66),
                        checkedTrackColor = Color(0xFF00FF66).copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Line Thickness Slider
            Text(
                text = "سماكة خطوط الليزر: ${"%.1f".format(config.lineThicknessDp)} dp",
                color = Color.White,
                fontSize = 13.sp
            )
            Slider(
                value = config.lineThicknessDp,
                onValueChange = { onConfigChanged(config.copy(lineThicknessDp = it)) },
                valueRange = 1.5f..5f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E5FF),
                    activeTrackColor = Color(0xFF00E5FF)
                )
            )
        }
    }
}

@Composable
fun LiveTablePreviewCard(config: AimOverlayConfig, table: CalibratedTable) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF092015)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B4D36))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "معاينة حية لطاولة 8 Ball Pool وخطوط الحفر الستة",
                color = Color(0xFF6EE7B7),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = "دائرة واحدة فوق الكرية، وخطوط الليزر تشير للجيوب الستة:",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF072718))
                    .border(1.5.dp, Color(0xFF2C6B4F), RoundedCornerShape(10.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val l = table.leftRatio * w
                    val t = table.topRatio * h
                    val r = table.rightRatio * w
                    val b = table.bottomRatio * h
                    val midX = (l + r) / 2f

                    // Table Cushion Border
                    drawRoundRect(
                        color = Color(0xFF00FF66).copy(alpha = 0.4f),
                        topLeft = Offset(l, t),
                        size = Size(r - l, b - t),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    val pockets = listOf(
                        Offset(l, t), Offset(midX, t), Offset(r, t),
                        Offset(l, b), Offset(midX, b), Offset(r, b)
                    )

                    // Target Ball Position
                    val ballPos = Offset(w * 0.45f, h * 0.55f)
                    val radius = config.ballRadiusDp.dp.toPx() * 0.7f

                    // Draw 6 Laser Lines to each pocket
                    pockets.forEachIndexed { idx, p ->
                        val isBest = (idx == 0 || idx == 2)
                        val lineColor = if (isBest) Color(0xFFFFD700) else Color(0xFF00E5FF)
                        drawLine(
                            color = lineColor.copy(alpha = 0.85f),
                            start = ballPos,
                            end = p,
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = Color(0xFFFFD700),
                            radius = 6.dp.toPx(),
                            center = p,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    // Draw the Single Target Circle
                    drawCircle(
                        color = Color.White,
                        radius = radius,
                        center = ballPos,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                        radius = radius * 0.6f,
                        center = ballPos,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = ballPos
                    )
                }
            }
        }
    }
}

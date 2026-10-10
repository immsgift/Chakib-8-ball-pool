package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.BilliardDark
import com.example.ui.theme.BilliardSurface
import com.example.ui.theme.BilliardSurfaceVariant
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonGreen
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class AppStep {
    CALIBRATION,
    GAME_MODE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AimAssistantApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AimAssistantApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density

    // Config & Persistent Calibration
    var config by remember { mutableStateOf(AimOverlayConfig.load(context)) }
    var calibratedTable by remember { mutableStateOf(AimOverlayConfig.loadCalibration(context)) }

    // Start in Game Mode if already calibrated, else Calibration
    var currentStep by remember {
        mutableStateOf(if (calibratedTable.isCalibrated) AppStep.GAME_MODE else AppStep.GAME_MODE)
    }

    // Overlay Service State
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isOverlayRunning by remember { mutableStateOf(OverlayService.isRunning) }

    // HUD States matching screenshot
    var isCircleLocked by remember { mutableStateOf(false) }
    var showGuidelines by remember { mutableStateOf(true) }
    var bounceMode by remember { mutableIntStateOf(1) } // 0 = off, 1 = 1-bounce, 2 = 2-bounces
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Draggable HUD Bar Position
    var hudDragOffset by remember { mutableStateOf(Offset.Zero) }

    // ONLY ONE SINGLE TARGET CIRCLE (Matching user request & screenshot)
    var targetCirclePos by remember { mutableStateOf<Offset?>(null) }
    var shotAngleDegrees by remember { mutableFloatStateOf(270f) } // Points straight up towards top

    // Calibration Corners
    var cornerTL by remember { mutableStateOf<Offset?>(null) }
    var cornerTR by remember { mutableStateOf<Offset?>(null) }
    var cornerBR by remember { mutableStateOf<Offset?>(null) }
    var cornerBL by remember { mutableStateOf<Offset?>(null) }
    var activeCornerDrag by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                isOverlayRunning = OverlayService.isRunning
                config = AimOverlayConfig.load(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val requestOverlayPermission = {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            context.startActivity(fallback)
        }
    }

    val toggleOverlayService = {
        if (!hasOverlayPermission) {
            requestOverlayPermission()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            if (isOverlayRunning) {
                OverlayService.stop(context)
                isOverlayRunning = false
                Toast.makeText(context, "تم إيقاف المساعد العائم", Toast.LENGTH_SHORT).show()
            } else {
                OverlayService.start(context)
                isOverlayRunning = true
                Toast.makeText(
                    context,
                    "تم تشغيل المساعد بنجاح! افتح الآن لعبة 8 Ball Pool وستجد الدائرة والخطوط فوق اللعبة مباشرة",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val updateConfig = { newConfig: AimOverlayConfig ->
        config = newConfig
        AimOverlayConfig.save(context, newConfig)
        context.sendBroadcast(Intent(AimOverlayConfig.ACTION_CONFIG_CHANGED))
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BilliardDark)
            .testTag("aim_assistant_root")
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        if (cornerTL == null || cornerTR == null || cornerBR == null || cornerBL == null) {
            cornerTL = Offset(screenW * calibratedTable.tlX, screenH * calibratedTable.tlY)
            cornerTR = Offset(screenW * calibratedTable.trX, screenH * calibratedTable.trY)
            cornerBR = Offset(screenW * calibratedTable.brX, screenH * calibratedTable.brY)
            cornerBL = Offset(screenW * calibratedTable.blX, screenH * calibratedTable.blY)
        }

        val tl = cornerTL ?: Offset(screenW * 0.08f, screenH * 0.12f)
        val tr = cornerTR ?: Offset(screenW * 0.92f, screenH * 0.12f)
        val br = cornerBR ?: Offset(screenW * 0.92f, screenH * 0.88f)
        val bl = cornerBL ?: Offset(screenW * 0.08f, screenH * 0.88f)

        // Initialize single target circle position (placed over the target ball)
        if (targetCirclePos == null) {
            val midX = (tl.x + tr.x + br.x + bl.x) / 4f
            val midY = (tl.y + tr.y + br.y + bl.y) / 4f
            targetCirclePos = Offset(midX, midY + (bl.y - tl.y) * 0.05f)
        }

        val targetCircle = targetCirclePos ?: Offset(screenW * 0.5f, screenH * 0.55f)
        val ballRadius = config.ballRadiusDp * density

        // 6 Pockets Coordinates
        val pockets = listOf(
            tl, // Top-Left
            tr, // Top-Right
            Offset((tr.x + br.x) * 0.5f, (tr.y + br.y) * 0.5f), // Middle-Right
            br, // Bottom-Right
            bl, // Bottom-Left
            Offset((tl.x + bl.x) * 0.5f, (tl.y + bl.y) * 0.5f)  // Middle-Left
        )

        // Touch Interaction: Dragging Single Circle or Rotating Angle
        var isDraggingTargetCircle by remember { mutableStateOf(false) }
        var isDraggingAngleHandle by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentStep, isCircleLocked) {
                    detectDragGestures(
                        onDragStart = { startPos ->
                            if (currentStep == AppStep.CALIBRATION) {
                                val handleRadius = 42.dp.toPx()
                                activeCornerDrag = when {
                                    (startPos - tl).getDistance() <= handleRadius -> "TL"
                                    (startPos - tr).getDistance() <= handleRadius -> "TR"
                                    (startPos - br).getDistance() <= handleRadius -> "BR"
                                    (startPos - bl).getDistance() <= handleRadius -> "BL"
                                    else -> null
                                }
                            } else {
                                if (!isCircleLocked) {
                                    val rad = Math.toRadians(shotAngleDegrees.toDouble())
                                    val handleOffsetDist = ballRadius + 32.dp.toPx()
                                    val handlePos = targetCircle + Offset(cos(rad).toFloat() * handleOffsetDist, sin(rad).toFloat() * handleOffsetDist)

                                    val distToHandle = (startPos - handlePos).getDistance()
                                    val distToCircle = (startPos - targetCircle).getDistance()

                                    if (distToHandle <= 28.dp.toPx()) {
                                        isDraggingAngleHandle = true
                                    } else if (distToCircle <= ballRadius * 3f) {
                                        isDraggingTargetCircle = true
                                    } else {
                                        // Dragging outside rotates the aim direction smoothly
                                        isDraggingAngleHandle = true
                                        val angleRad = atan2((startPos.y - targetCircle.y).toDouble(), (startPos.x - targetCircle.x).toDouble())
                                        var deg = Math.toDegrees(angleRad).toFloat()
                                        if (deg < 0) deg += 360f
                                        shotAngleDegrees = deg
                                    }
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (currentStep == AppStep.CALIBRATION) {
                                val pad = 12.dp.toPx()
                                when (activeCornerDrag) {
                                    "TL" -> cornerTL = Offset((tl.x + dragAmount.x).coerceIn(pad, tr.x - 40f), (tl.y + dragAmount.y).coerceIn(pad + 60f, bl.y - 60f))
                                    "TR" -> cornerTR = Offset((tr.x + dragAmount.x).coerceIn(tl.x + 40f, screenW - pad), (tr.y + dragAmount.y).coerceIn(pad + 60f, br.y - 60f))
                                    "BR" -> cornerBR = Offset((br.x + dragAmount.x).coerceIn(bl.x + 40f, screenW - pad), (br.y + dragAmount.y).coerceIn(tr.y + 60f, screenH - pad - 80f))
                                    "BL" -> cornerBL = Offset((bl.x + dragAmount.x).coerceIn(pad, br.x - 40f), (bl.y + dragAmount.y).coerceIn(tl.y + 60f, screenH - pad - 80f))
                                }
                            } else {
                                if (isDraggingTargetCircle) {
                                    targetCirclePos = Offset(
                                        (targetCircle.x + dragAmount.x).coerceIn(20f, screenW - 20f),
                                        (targetCircle.y + dragAmount.y).coerceIn(60f, screenH - 20f)
                                    )
                                } else if (isDraggingAngleHandle) {
                                    val currentPos = change.position
                                    val angleRad = atan2((currentPos.y - targetCircle.y).toDouble(), (currentPos.x - targetCircle.x).toDouble())
                                    var deg = Math.toDegrees(angleRad).toFloat()
                                    if (deg < 0) deg += 360f
                                    shotAngleDegrees = deg
                                }
                            }
                        },
                        onDragEnd = {
                            activeCornerDrag = null
                            isDraggingTargetCircle = false
                            isDraggingAngleHandle = false
                        },
                        onDragCancel = {
                            activeCornerDrag = null
                            isDraggingTargetCircle = false
                            isDraggingAngleHandle = false
                        }
                    )
                }
        ) {
            // Main Canvas: Renders Pool Table, Single Target Circle, and ALL 6 POCKET RAYS
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawScreenshotStylePoolTable(
                    w = size.width,
                    h = size.height,
                    tl = tl, tr = tr, br = br, bl = bl,
                    pockets = pockets,
                    currentStep = currentStep,
                    targetCircle = targetCircle,
                    shotAngleDegrees = shotAngleDegrees,
                    ballRadius = ballRadius,
                    showGuidelines = showGuidelines,
                    bounceMode = bounceMode,
                    config = config
                )
            }

            // Top Bar with Prominent Floating Overlay Toggle Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep == AppStep.GAME_MODE) {
                        OutlinedButton(
                            onClick = { currentStep = AppStep.CALIBRATION },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                            modifier = Modifier
                                .testTag("recalibrate_top_button")
                                .border(1.dp, NeonGreen, RoundedCornerShape(20.dp))
                        ) {
                            Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonGreen)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("معايرة الزوايا", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonGreen)
                        }
                    } else {
                        Text(
                            text = "المعايرة (خطوة 1)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Direct Overlay Launch Button for 8 Ball Pool!
                    Button(
                        onClick = toggleOverlayService,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOverlayRunning) Color(0xFFEF4444) else NeonGreen,
                            contentColor = if (isOverlayRunning) Color.White else Color(0xFF003915)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("top_overlay_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isOverlayRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isOverlayRunning) "إيقاف المساعد" else "تشغيل فوق 8 Ball Pool",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Permission Warning Banner if missing
                if (!hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { requestOverlayPermission() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF33250A))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "إذن الظهور فوق التطبيقات مطلوب للعبة!",
                                    color = Color(0xFFFFD600),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "اضغط هنا لتفعيل إذن الظهور فوق لعبة 8 Ball Pool مباشرة",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Step 1: Bottom Button ("Done - Save Calibration")
            if (currentStep == AppStep.CALIBRATION) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "اسحب المقابض لتطابق زوايا طاولة 8 Ball Pool",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    Button(
                        onClick = {
                            val saved = CalibratedTable(
                                tlX = (tl.x / screenW).coerceIn(0f, 1f),
                                tlY = (tl.y / screenH).coerceIn(0f, 1f),
                                trX = (tr.x / screenW).coerceIn(0f, 1f),
                                trY = (tr.y / screenH).coerceIn(0f, 1f),
                                brX = (br.x / screenW).coerceIn(0f, 1f),
                                brY = (br.y / screenH).coerceIn(0f, 1f),
                                blX = (bl.x / screenW).coerceIn(0f, 1f),
                                blY = (bl.y / screenH).coerceIn(0f, 1f),
                                isCalibrated = true
                            )
                            calibratedTable = saved
                            AimOverlayConfig.saveCalibration(context, saved)
                            currentStep = AppStep.GAME_MODE
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("done_save_calibration_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color(0xFF003915)
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تم - حفظ المعايرة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // Step 2: Floating HUD Bar (Matching the Reference Screenshot!)
            if (currentStep == AppStep.GAME_MODE) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(hudDragOffset.x.roundToInt(), hudDragOffset.y.roundToInt()) }
                        .statusBarsPadding()
                        .padding(top = 56.dp)
                        .align(Alignment.TopCenter)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                hudDragOffset = Offset(
                                    hudDragOffset.x + dragAmount.x,
                                    hudDragOffset.y + dragAmount.y
                                )
                            }
                        }
                ) {
                    ScreenshotStyleHudBar(
                        isLocked = isCircleLocked,
                        onToggleLock = { isCircleLocked = !isCircleLocked },
                        showGuidelines = showGuidelines,
                        onToggleGuidelines = { showGuidelines = !showGuidelines },
                        bounceMode = bounceMode,
                        onCycleBounce = { bounceMode = (bounceMode + 1) % 3 },
                        onOpenSettings = { showSettingsSheet = true },
                        onCloseOrReset = {
                            val midX = (tl.x + tr.x + br.x + bl.x) / 4f
                            val midY = (tl.y + tr.y + br.y + bl.y) / 4f
                            targetCirclePos = Offset(midX, midY + (bl.y - tl.y) * 0.05f)
                            shotAngleDegrees = 270f
                        }
                    )
                }

                // Step 2: Bottom Status Readout with Hint
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isCircleLocked) "الدائرة مقفلة - اضغط على ✥ لتحريكها" else "ضع الدائرة فوق الكرة التالية لتظهر جميع مسارات الحفر",
                        color = if (isCircleLocked) Color(0xFFFFD600) else Color(0xFFA7F3D0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xCC0F172A))
                            .border(1.dp, Color(0x4000FF66), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeonGreen)
                        )
                        Text(
                            text = "6 مسارات للحفر نشطة",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text("•", color = Color(0xFF64748B))
                        Text(
                            text = if (bounceMode > 0) "${bounceMode}x ارتداد" else "مباشر",
                            color = Color(0xFFFFD600),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("•", color = Color(0xFF64748B))
                        Text(
                            text = if (isOverlayRunning) "يعمل فوق 8 Ball Pool" else "غير مشغل فوق اللعبة",
                            color = if (isOverlayRunning) NeonGreen else Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    // Settings Bottom Sheet
    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState,
            containerColor = BilliardSurface,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إعدادات مساعد التهديف",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = { showSettingsSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Floating Overlay Toggle inside Sheet
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = BilliardSurfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "المساعد العائم فوق لعبة 8 Ball Pool",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isOverlayRunning) "المساعد يعمل الآن فوق اللعبة" else "إظهار الدائرة والخطوط فوق 8 Ball Pool",
                                    color = Color(0xFFA7F3D0),
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = toggleOverlayService,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isOverlayRunning) Color(0xFFEF4444) else NeonGreen,
                                    contentColor = if (isOverlayRunning) Color.White else Color(0xFF003915)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    if (isOverlayRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (isOverlayRunning) "إيقاف" else "تشغيل", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!hasOverlayPermission) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = requestOverlayPermission,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD600))
                            ) {
                                Text("منح إذن الظهور فوق التطبيقات")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ball Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "حجم الدائرة (مطابقة حجم الكرة)", color = Color(0xFFE2E8F0), fontSize = 13.sp)
                    Text(
                        text = "${config.ballRadiusDp.toInt()} dp",
                        color = NeonGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Slider(
                    value = config.ballRadiusDp,
                    onValueChange = { updateConfig(config.copy(ballRadiusDp = it)) },
                    valueRange = 16f..42f,
                    steps = 13,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonGreen,
                        activeTrackColor = NeonGreen,
                        inactiveTrackColor = BilliardSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Recalibrate Button in Sheet
                OutlinedButton(
                    onClick = {
                        showSettingsSheet = false
                        currentStep = AppStep.CALIBRATION
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)
                ) {
                    Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("معايرة زوايا وحواف الطاولة (الخطوة 1)")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Top floating HUD bar matching the screenshot with 5 icons:
 * 1. Move (4-way arrow ✥)
 * 2. Eye (👁 / ⊘)
 * 3. Bounce (🔀)
 * 4. Settings (🎚)
 * 5. Close (✕)
 */
@Composable
fun ScreenshotStyleHudBar(
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    showGuidelines: Boolean,
    onToggleGuidelines: () -> Unit,
    bounceMode: Int,
    onCycleBounce: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseOrReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xF0FFFFFF)) // Clean white card matching screenshot
            .border(1.dp, Color(0x6094A3B8), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Move Icon (4-way arrows ✥)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (!isLocked) Color(0x25000000) else Color(0x10000000))
                .clickable { onToggleLock() }
                .testTag("hud_move_button"),
            contentAlignment = Alignment.Center
        ) {
            Text("✥", fontSize = 18.sp, color = if (!isLocked) Color(0xFF0F172A) else Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
        }

        // 2. Eye Icon (👁 / ⊘)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (showGuidelines) Color(0x25000000) else Color(0x10000000))
                .clickable { onToggleGuidelines() }
                .testTag("hud_eye_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(if (showGuidelines) "👁" else "⊘", fontSize = 16.sp, color = Color(0xFF0F172A))
        }

        // 3. Bounce Icon (🔀)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (bounceMode > 0) Color(0x30F59E0B) else Color(0x10000000))
                .clickable { onCycleBounce() }
                .testTag("hud_bounce_button"),
            contentAlignment = Alignment.Center
        ) {
            Text("🔀", fontSize = 16.sp, color = if (bounceMode > 0) Color(0xFFD97706) else Color(0xFF0F172A))
        }

        // 4. Settings Icon (🎚 Sliders)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x10000000))
                .clickable { onOpenSettings() }
                .testTag("hud_settings_button"),
            contentAlignment = Alignment.Center
        ) {
            Text("🎚", fontSize = 16.sp, color = Color(0xFF0F172A))
        }

        // 5. Close Icon (✕ Red)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x15EF4444))
                .clickable { onCloseOrReset() }
                .testTag("hud_close_button"),
            contentAlignment = Alignment.Center
        ) {
            Text("✕", fontSize = 16.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Renders the pool table with the SINGLE TARGET CIRCLE and ALL 6 POCKET LINES directly matching the screenshot!
 */
fun DrawScope.drawScreenshotStylePoolTable(
    w: Float,
    h: Float,
    tl: Offset,
    tr: Offset,
    br: Offset,
    bl: Offset,
    pockets: List<Offset>,
    currentStep: AppStep,
    targetCircle: Offset,
    shotAngleDegrees: Float,
    ballRadius: Float,
    showGuidelines: Boolean,
    bounceMode: Int,
    config: AimOverlayConfig
) {
    // 1. Table Felt Background (Tournament Blue/Slate felt like screenshot)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF2B7FA8), Color(0xFF1E5B7A), Color(0xFF133E54)),
            center = Offset(w * 0.5f, h * 0.5f),
            radius = w * 0.95f
        ),
        size = Size(w, h)
    )

    // Calibrated Cushion Frame
    val framePath = Path().apply {
        moveTo(tl.x, tl.y)
        lineTo(tr.x, tr.y)
        lineTo(br.x, br.y)
        lineTo(bl.x, bl.y)
        close()
    }

    drawPath(path = framePath, color = Color(0x1800E5FF))
    drawPath(path = framePath, color = Color(0xFF00E5FF), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

    // Inlaid Diamond Sights
    val diamondColor = Color(0x70FFFFFF)
    val dRadius = 3.dp.toPx()
    for (i in 1..3) {
        val frac = i / 4f
        drawCircle(diamondColor, dRadius, Offset(tl.x + (tr.x - tl.x) * frac, tl.y + (tr.y - tl.y) * frac))
        drawCircle(diamondColor, dRadius, Offset(bl.x + (br.x - bl.x) * frac, bl.y + (br.y - bl.y) * frac))
    }
    for (i in 1..5) {
        val frac = i / 6f
        drawCircle(diamondColor, dRadius, Offset(tl.x + (bl.x - tl.x) * frac, tl.y + (bl.y - tl.y) * frac))
        drawCircle(diamondColor, dRadius, Offset(tr.x + (br.x - tr.x) * frac, tr.y + (br.y - tr.y) * frac))
    }

    // 2. Six Pockets
    val pocketRadius = 18.dp.toPx()
    pockets.forEach { p ->
        drawCircle(Color(0xFF071118), pocketRadius, p)
        drawCircle(Color(0xFF334155), pocketRadius, p, style = Stroke(width = 3.dp.toPx()))
        drawCircle(Color(0x9000E5FF), 3.5.dp.toPx(), p)
    }

    // STEP 1: Calibration Handles
    if (currentStep == AppStep.CALIBRATION) {
        val handleRadius = 24.dp.toPx()
        val cornerPoints = listOf(
            Triple(tl, "TL", Color(0xFF00FF66)),
            Triple(tr, "TR", Color(0xFF00FF66)),
            Triple(br, "BR", Color(0xFF00FF66)),
            Triple(bl, "BL", Color(0xFF00FF66))
        )

        cornerPoints.forEach { (pos, _, color) ->
            drawCircle(Color(0x4000FF66), handleRadius + 8.dp.toPx(), pos)
            drawCircle(Color(0xEE0F172A), handleRadius, pos)
            drawCircle(color, handleRadius, pos, style = Stroke(width = 2.5.dp.toPx()))
            drawLine(color, pos - Offset(handleRadius * 0.5f, 0f), pos + Offset(handleRadius * 0.5f, 0f), strokeWidth = 2.dp.toPx())
            drawLine(color, pos - Offset(0f, handleRadius * 0.5f), pos + Offset(0f, handleRadius * 0.5f), strokeWidth = 2.dp.toPx())
            drawCircle(Color.White, 3.5.dp.toPx(), pos)
        }
        return
    }

    // STEP 2: GAME MODE (MATCHING THE SCREENSHOT EXACTLY!)
    val railSegments = listOf(
        Pair(tl, tr),
        Pair(tr, br),
        Pair(br, bl),
        Pair(bl, tl)
    )

    if (showGuidelines) {
        // A. PROJECT REAL-TIME LASER LINES TO ALL 6 POCKETS SIMULTANEOUSLY!
        pockets.forEach { pocket ->
            // Glowing cyan/white laser beam connecting the single circle to the pocket
            drawLine(
                color = Color(0x3500E5FF),
                start = targetCircle,
                end = pocket,
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xD0FFFFFF),
                start = targetCircle,
                end = pocket,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Target ring at each pocket entrance
            drawCircle(Color(0x6000E5FF), 12.dp.toPx(), pocket, style = Stroke(width = 2.dp.toPx()))
            drawCircle(Color.White, 3.5.dp.toPx(), pocket)
        }

        // B. Primary Shot Line & Cushion Rebounds (in the aim direction)
        val rad = Math.toRadians(shotAngleDegrees.toDouble())
        val dirX = cos(rad).toFloat()
        val dirY = sin(rad).toFloat()

        val startShot = targetCircle + Offset(dirX * ballRadius, dirY * ballRadius)
        val hit1 = calculateCalibratedRayIntersection(startShot, Offset(dirX, dirY), railSegments)

        if (hit1 != null) {
            drawLine(
                color = Color(0x40FFFFFF),
                start = startShot,
                end = hit1.point,
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = startShot,
                end = hit1.point,
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(Color.White, 4.dp.toPx(), hit1.point)

            // Cushion Bank Rebound
            if (bounceMode > 0) {
                var curStart = hit1.point
                var curDir = hit1.reflectedDir
                for (b in 0 until bounceMode) {
                    val nextHit = calculateCalibratedRayIntersection(curStart, curDir, railSegments)
                    if (nextHit != null) {
                        drawLine(
                            color = Color(0xFFFFD600),
                            start = curStart,
                            end = nextHit.point,
                            strokeWidth = 2.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
                            cap = StrokeCap.Round
                        )
                        drawCircle(Color(0xFFFFD600), 3.5.dp.toPx(), nextHit.point)
                        curStart = nextHit.point
                        curDir = nextHit.reflectedDir
                    } else break
                }
            }
        }

        // C. Ghost Cue Ball along the shot line (Matching screenshot cue placement)
        val ghostDist = 70.dp.toPx()
        val ghostPos = targetCircle - Offset(dirX * ghostDist, dirY * ghostDist)
        drawLine(
            color = Color.White,
            start = ghostPos,
            end = targetCircle - Offset(dirX * ballRadius, dirY * ballRadius),
            strokeWidth = 2.dp.toPx()
        )
        drawCircle(
            color = Color(0x30FFFFFF),
            radius = ballRadius,
            center = ghostPos
        )
        drawCircle(
            color = Color.White,
            radius = ballRadius,
            center = ghostPos,
            style = Stroke(width = 1.8.dp.toPx())
        )
        drawCircle(Color(0xFFEF4444), 3.dp.toPx(), ghostPos)
    }

    // 3. DRAW ONLY ONE SINGLE TARGET CIRCLE (Matching Screenshot!)
    drawCircle(Color(0x60000000), ballRadius, targetCircle + Offset(2.dp.toPx(), 2.5.dp.toPx()))
    drawCircle(Color(0x3000E5FF), ballRadius, targetCircle)
    drawCircle(Color.White, ballRadius, targetCircle, style = Stroke(width = 2.8.dp.toPx()))
    drawCircle(Color(0xFF00E5FF), ballRadius * 0.55f, targetCircle, style = Stroke(width = 1.2.dp.toPx()))
    drawCircle(Color.White, 3.dp.toPx(), targetCircle)
    // 4 Cardinal Ticks
    val tick = 6.dp.toPx()
    drawLine(Color.White, targetCircle - Offset(ballRadius, 0f), targetCircle - Offset(ballRadius - tick, 0f), strokeWidth = 1.5.dp.toPx())
    drawLine(Color.White, targetCircle + Offset(ballRadius - tick, 0f), targetCircle + Offset(ballRadius, 0f), strokeWidth = 1.5.dp.toPx())
    drawLine(Color.White, targetCircle - Offset(0f, ballRadius), targetCircle - Offset(0f, ballRadius - tick), strokeWidth = 1.5.dp.toPx())
    drawLine(Color.White, targetCircle + Offset(0f, ballRadius - tick), targetCircle + Offset(0f, ballRadius), strokeWidth = 1.5.dp.toPx())

    // 4. DRAW ROTATION HANDLE DIAL (To rotate the shot vector)
    val rad = Math.toRadians(shotAngleDegrees.toDouble())
    val handleDist = ballRadius + 32.dp.toPx()
    val handlePos = targetCircle + Offset(cos(rad).toFloat() * handleDist, sin(rad).toFloat() * handleDist)
    drawCircle(Color(0xEEFFFFFF), 11.dp.toPx(), handlePos)
    drawCircle(Color(0xFF0F172A), 11.dp.toPx(), handlePos, style = Stroke(width = 1.5.dp.toPx()))
    drawCircle(Color(0xFF00E5FF), 3.5.dp.toPx(), handlePos)
}

data class CalibratedHit(
    val point: Offset,
    val reflectedDir: Offset
)

fun calculateCalibratedRayIntersection(
    start: Offset,
    dir: Offset,
    railSegments: List<Pair<Offset, Offset>>
): CalibratedHit? {
    var minT = Float.MAX_VALUE
    var hitPoint: Offset? = null
    var bestNormal: Offset? = null

    railSegments.forEach { (a, b) ->
        val segVec = b - a
        val cross = dir.x * segVec.y - dir.y * segVec.x
        if (Math.abs(cross) > 0.0001f) {
            val toStart = a - start
            val t = (toStart.x * segVec.y - toStart.y * segVec.x) / cross
            val u = (toStart.x * dir.y - toStart.y * dir.x) / cross

            if (t > 0.001f && t < minT && u in 0f..1f) {
                minT = t
                hitPoint = start + dir * t

                val segLen = segVec.getDistance().coerceAtLeast(1f)
                val normal = Offset(-segVec.y / segLen, segVec.x / segLen)
                bestNormal = normal
            }
        }
    }

    if (hitPoint != null && bestNormal != null) {
        var n = bestNormal!!
        val dot = dir.x * n.x + dir.y * n.y
        if (dot > 0f) {
            n = Offset(-n.x, -n.y)
        }
        val refDir = dir - n * (2f * (dir.x * n.x + dir.y * n.y))
        val refLen = refDir.getDistance().coerceAtLeast(1f)
        return CalibratedHit(hitPoint!!, Offset(refDir.x / refLen, refDir.y / refLen))
    }

    return null
}

fun Offset.getDistance(): Float {
    return sqrt((x * x + y * y).toDouble()).toFloat()
}

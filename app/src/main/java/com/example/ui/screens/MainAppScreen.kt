package com.example.ui.screens

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VolumeGestureLog
import com.example.gesture.VolumeGestureDetector
import com.example.service.VolumeGestureOverlayService
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Service activity check states
    var isOverlayActive by remember { mutableStateOf(VolumeGestureOverlayService.isServiceRunning) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // Recheck permissions periodically when window gains focus/state resumes
    LaunchedEffect(Unit) {
        while (true) {
            isOverlayActive = VolumeGestureOverlayService.isServiceRunning
            hasOverlayPermission = Settings.canDrawOverlays(context)
            delay(1200)
        }
    }

    // Settings values collected from ViewModel
    val enabled by viewModel.gestureEnabled.collectAsState()
    val side by viewModel.triggerSide.collectAsState()
    val height by viewModel.triggerHeight.collectAsState()
    val width by viewModel.triggerWidth.collectAsState()
    val sensitivity by viewModel.sensitivity.collectAsState()
    val haptic by viewModel.hapticFeedback.collectAsState()
    val sound by viewModel.soundFeedback.collectAsState()
    val invisibleTrigger by viewModel.invisibleTrigger.collectAsState()
    val manualControlAfterGesture by viewModel.manualControlAfterGesture.collectAsState()
    val manualControlSwipe by viewModel.manualControlSwipe.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.ui.theme.DarkBackground)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            com.example.ui.theme.DarkBackground,
                            Color(0xFF14171E)
                        )
                    )
                )
        ) {
            // Elegant Header Area
            HeaderSection(isOverlayActive = isOverlayActive && hasOverlayPermission)

            // Single unified scrollable settings dashboard
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // 1. Draw Over Apps warning card (if not granted)
                if (!hasOverlayPermission) {
                    item {
                        PermissionRequiredCard(context)
                    }
                }

                // 2. Main Service Management Card
                item {
                    ServiceControlCard(
                        context = context,
                        isOverlayActive = isOverlayActive,
                        hasOverlayPermission = hasOverlayPermission,
                        side = side
                    )
                }

                // 3. Settings Divider Title
                item {
                    Text(
                        text = "Preferências do Gesto",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }

                // 4. Invisible Trigger / Stealth Mode toggle
                item {
                    SettingsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = "Ocultar",
                                        tint = if (invisibleTrigger) com.example.ui.theme.DarkAccent else com.example.ui.theme.DarkSecondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Gatilho Oculto (Modo Invisível)",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (invisibleTrigger) 
                                        "Ativo: A barra lateral fica 100% invisível ao olho nu. Ideal para usar discretamente em apps como YouTube e Instagram." 
                                    else 
                                        "Inativo: exibe uma linha fina colorida na borda da tela guiando o posicionamento.",
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = com.example.ui.theme.DarkSecondaryText
                                )
                            }
                            Switch(
                                checked = invisibleTrigger,
                                onCheckedChange = { viewModel.updateInvisibleTrigger(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = com.example.ui.theme.DarkAccentDarker,
                                    checkedTrackColor = com.example.ui.theme.DarkAccent,
                                    uncheckedThumbColor = com.example.ui.theme.DarkSecondaryText,
                                    uncheckedTrackColor = com.example.ui.theme.DarkSurfaceVariant
                                )
                            )
                        }
                    }
                }

                // 5. Persistent manual volume control
                item {
                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Controle manual",
                                            tint = if (manualControlAfterGesture) com.example.ui.theme.DarkAccent else com.example.ui.theme.DarkSecondaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Controle Manual após Gesto",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (manualControlAfterGesture)
                                            "Ativo: o visual do volume fica aberto com controles manuais e botão discreto de fechar."
                                        else
                                            "Inativo: o visual do volume fecha assim que você solta o gesto.",
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = com.example.ui.theme.DarkSecondaryText
                                    )
                                }
                                Switch(
                                    checked = manualControlAfterGesture,
                                    onCheckedChange = { viewModel.updateManualControlAfterGesture(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = com.example.ui.theme.DarkAccentDarker,
                                        checkedTrackColor = com.example.ui.theme.DarkAccent,
                                        uncheckedThumbColor = com.example.ui.theme.DarkSecondaryText,
                                        uncheckedTrackColor = com.example.ui.theme.DarkSurfaceVariant
                                    )
                                )
                            }

                            AnimatedVisibility(visible = manualControlAfterGesture) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TouchApp,
                                            contentDescription = "Arraste manual",
                                            tint = if (manualControlSwipe) com.example.ui.theme.DarkAccent else com.example.ui.theme.DarkSecondaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Arraste no Controle Manual",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Ajuste o volume arrastando a área do controle para cima ou para baixo.",
                                                color = com.example.ui.theme.DarkSecondaryText,
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = manualControlSwipe,
                                        onCheckedChange = { viewModel.updateManualControlSwipe(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = com.example.ui.theme.DarkAccentDarker,
                                            checkedTrackColor = com.example.ui.theme.DarkAccent,
                                            uncheckedThumbColor = com.example.ui.theme.DarkSecondaryText,
                                            uncheckedTrackColor = com.example.ui.theme.DarkSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Active Screen Side Selector (Left vs Right)
                item {
                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                Icon(
                                    imageVector = Icons.Default.AlignHorizontalLeft,
                                    contentDescription = "Lado",
                                    tint = com.example.ui.theme.DarkAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Lado de Ativação do Gesto",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            }
                            Text(
                                text = "Selecione o lado da tela para iniciar o ajuste do volume.",
                                fontSize = 11.sp,
                                color = com.example.ui.theme.DarkSecondaryText,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("LEFT" to "Esquerdo", "RIGHT" to "Direito").forEach { option ->
                                    val isSelected = side == option.first
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) com.example.ui.theme.DarkAccentDark else Color.White.copy(alpha = 0.04f))
                                            .clickable { viewModel.updateTriggerSide(option.first) }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = option.second,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) com.example.ui.theme.DarkAccent else Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Sensitivity Scroll Step configuration
                item {
                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Sensibilidade do Gesto",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Quantos pixels são necessários arrastar para mudar 1 passo do volume (menor valor = mudança mais rápida).",
                                fontSize = 11.sp,
                                color = com.example.ui.theme.DarkSecondaryText,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Slider(
                                    value = sensitivity,
                                    onValueChange = { viewModel.updateSensitivity(it) },
                                    valueRange = 20f..100f,
                                    steps = 8,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = com.example.ui.theme.DarkAccent,
                                        activeTrackColor = com.example.ui.theme.DarkAccent,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.08f)
                                    )
                                )
                                Text(
                                    text = "${sensitivity.toInt()}px",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(42.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }

                // 7. Touch Area Height and Width Dimensions
                item {
                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Formato da Área Ativadora",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Dimensões da região invisível na lateral da tela.",
                                fontSize = 11.sp,
                                color = com.example.ui.theme.DarkSecondaryText,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Height Slider
                            Text(
                                text = "Altura (Percentual da tela): ${(height * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Slider(
                                value = height,
                                onValueChange = { viewModel.updateTriggerHeight(it) },
                                valueRange = 0.3f..0.9f,
                                colors = SliderDefaults.colors(
                                    thumbColor = com.example.ui.theme.DarkAccent,
                                    activeTrackColor = com.example.ui.theme.DarkAccent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.08f)
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Width Slider
                            Text(
                                text = "Espessura da Área (Toque): ${width}dp",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Slider(
                                value = width.toFloat(),
                                onValueChange = { viewModel.updateTriggerWidth(it.toInt()) },
                                valueRange = 16f..64f,
                                steps = 3,
                                colors = SliderDefaults.colors(
                                    thumbColor = com.example.ui.theme.DarkAccent,
                                    activeTrackColor = com.example.ui.theme.DarkAccent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.08f)
                                )
                            )
                        }
                    }
                }

                // 8. Sensory Tactile Responses Feedback (Vibration, Sounds)
                item {
                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Feedback do Sensor",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Como o celular avisa você sobre a mudança de volume.",
                                fontSize = 11.sp,
                                color = com.example.ui.theme.DarkSecondaryText,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Haptic response
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Vibration,
                                        contentDescription = "Haptic",
                                        tint = if (haptic) com.example.ui.theme.DarkAccent else com.example.ui.theme.DarkSecondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(text = "Vibrar a cada passo", color = Color.White, fontSize = 13.sp)
                                }
                                Switch(
                                    checked = haptic,
                                    onCheckedChange = { viewModel.updateHapticFeedback(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = DarkAccentDarker,
                                        checkedTrackColor = DarkAccent,
                                        uncheckedThumbColor = DarkSecondaryText,
                                        uncheckedTrackColor = DarkSurfaceVariant
                                    )
                                )
                            }

                            // Ticks Sound response
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "Sound",
                                        tint = if (sound) DarkAccent else DarkSecondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(text = "Efeito sonoro de clique", color = Color.White, fontSize = 13.sp)
                                }
                                Switch(
                                    checked = sound,
                                    onCheckedChange = { viewModel.updateSoundFeedback(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = DarkAccentDarker,
                                        checkedTrackColor = DarkAccent,
                                        uncheckedThumbColor = DarkSecondaryText,
                                        uncheckedTrackColor = DarkSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }

                // 9. Fully Integrated Interactive Simulation Card
                item {
                    InteractiveSandboxCard(viewModel = viewModel, audioManager = audioManager)
                }

                // 10. Tutorial & Instuction details
                item {
                    QuickTutorialCard()
                }
            }
        }
    }
}

@Composable
fun HeaderSection(isOverlayActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 12.dp, start = 24.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "SonicGest",
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp,
                    fontFamily = FontFamily.SansSerif
                )
            )
            Text(
                text = "CONTROLE DE GESTOS ULTRA-BÁSICO",
                color = com.example.ui.theme.DarkSecondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Light Pulsing Badge status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(com.example.ui.theme.DarkSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOverlayActive) com.example.ui.theme.DarkAccent else Color(0xFFFF8A80))
            )
            Text(
                text = if (isOverlayActive) "Ativo" else "Inativo",
                color = if (isOverlayActive) com.example.ui.theme.DarkAccent else Color(0xFFFF8A80),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PermissionRequiredCard(context: Context) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFBC02D).copy(alpha = 0.12f)),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBC02D).copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alerta",
                    tint = Color(0xFFFBC02D),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Acesso Especial Requerido",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Para usar as guias e os gestos de volume por cima dos seus apps favoritos (como Instagram e YouTube), é necessário conceder a permissão de desenho de sobreposição.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFBC02D)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Liberar nas Configurações",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun ServiceControlCard(
    context: Context,
    isOverlayActive: Boolean,
    hasOverlayPermission: Boolean,
    side: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.DarkSurface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOverlayActive) 
                                com.example.ui.theme.DarkAccent.copy(alpha = 0.15f) 
                            else 
                                Color.White.copy(alpha = 0.05f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isOverlayActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = "Status",
                        tint = if (isOverlayActive) com.example.ui.theme.DarkAccent else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Serviço de Som Lateral",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (isOverlayActive) 
                            "Ativo na lateral ${if (side == "LEFT") "Esquerda" else "Direita"} da sua tela" 
                        else 
                            "O serviço em segundo plano está desligado",
                        fontSize = 12.sp,
                        color = com.example.ui.theme.DarkSecondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    val intent = Intent(context, VolumeGestureOverlayService::class.java)
                    if (isOverlayActive) {
                        context.stopService(intent)
                    } else {
                        if (hasOverlayPermission) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        } else {
                            val overlayIntent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(overlayIntent)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOverlayActive) Color(0xFFEF9A9A) else com.example.ui.theme.DarkAccent,
                    contentColor = if (isOverlayActive) Color(0xFF5D0000) else com.example.ui.theme.DarkAccentDarker
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isOverlayActive) "Parar Controlador de Som" else "Iniciar Controlador de Som",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InteractiveSandboxCard(viewModel: MainViewModel, audioManager: AudioManager) {
    val context = LocalContext.current
    var isSandboxEngaged by remember { mutableStateOf(false) }
    var sandboxVolumePercent by remember { mutableStateOf(0) }
    var currentScrollDir by remember { mutableStateOf("") }

    // Read sensitivity and sound response from settings
    val sensitivity by viewModel.sensitivity.collectAsState()
    val playSound by viewModel.soundFeedback.collectAsState()

    // Init volume levels
    LaunchedEffect(isSandboxEngaged) {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        sandboxVolumePercent = if (max > 0) ((current.toFloat() / max) * 100).roundToInt() else 0
    }

    // Touch gesture detector inside simulator
    val sandboxDetector = remember(sensitivity, playSound) {
        VolumeGestureDetector(
            context = context,
            onGestureStart = {
                isSandboxEngaged = true
                currentScrollDir = "Ativo"
            },
            onVolumeAdjusted = { direction, steps ->
                val flag = if (playSound) AudioManager.FLAG_PLAY_SOUND else 0
                val adjustType = if (direction == "Aumento") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                for (i in 0 until steps) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjustType, flag)
                }
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                sandboxVolumePercent = if (max > 0) ((current.toFloat() / max) * 100).roundToInt() else 0
                currentScrollDir = direction
            },
            onGestureEnd = { startPercentage, finalPercentage, direction, change ->
                isSandboxEngaged = false
                currentScrollDir = ""
                // Record the volume adjustment log
                viewModel.insertLog(
                    VolumeGestureLog(
                        timestamp = System.currentTimeMillis(),
                        direction = direction,
                        changeAmount = finalPercentage - startPercentage,
                        startVolume = startPercentage,
                        finalVolume = finalPercentage
                    )
                )
            },
            getCurrentVolume = {
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (max > 0) ((current.toFloat() / max) * 100).roundToInt() else 0
            },
            getMaxVolume = { 100 },
            allowSingleFingerDrag = true
        ).apply {
            sensitivityPixels = sensitivity
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.DarkSurface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Área de Teste Rápido",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 15.sp
            )
            Text(
                text = "Simule o gesto tocando e segurando na moldura abaixo; depois arraste para cima/baixo:",
                fontSize = 11.sp,
                color = com.example.ui.theme.DarkSecondaryText,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Phone bezel interactive frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .drawBehind {
                        // Subtle sidebar active overlay indication
                        val sideColor = com.example.ui.theme.DarkAccent.copy(alpha = 0.4f)
                        drawRoundRect(
                            color = sideColor,
                            topLeft = Offset(size.width - 6.dp.toPx(), size.height * 0.15f),
                            size = androidx.compose.ui.geometry.Size(6.dp.toPx(), size.height * 0.7f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                        )
                    }
                    .pointerInteropFilter { motionEvent ->
                        sandboxDetector.onTouchEvent(motionEvent)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!isSandboxEngaged) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Toque",
                            tint = com.example.ui.theme.DarkAccent.copy(alpha = 0.7f),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Toque, Segure e Arraste",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mantenha o dedo na tela e deslize verticalmente para ver o volume do celular mudar.",
                            color = com.example.ui.theme.DarkSecondaryText,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )
                    }
                } else {
                    // Floating simulation volume indicator HUD
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .width(110.dp)
                            .height(150.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(com.example.ui.theme.DarkSurface)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "$sandboxVolumePercent%",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        )
                        Text(
                            text = if (currentScrollDir == "Aumento") "AUMENTANDO" else if (currentScrollDir == "Diminuição") "DIMINUINDO" else "Gesto Ativo",
                            color = com.example.ui.theme.DarkAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Custom Slider scale
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(sandboxVolumePercent / 100f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(com.example.ui.theme.DarkAccent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickTutorialCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.DarkSurface.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.03f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Ajuda",
                    tint = com.example.ui.theme.DarkAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Como usar o controle por gestos?",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            val tutorials = listOf(
                "1. Ative o serviço usando o botão de ativação acima nas configurações.",
                "2. Fora do app, encoste um dedo na lateral selecionada da tela e segure por um instante.",
                "3. Arraste verticalmente: para cima aumenta e para baixo diminui o som.",
                "4. O gesto antigo com dois dedos também continua disponível na área de teste.",
                "5. Ative o 'Gatilho Oculto (Modo Invisível)' para que a guia fique 100% invisível ao assistir vídeos ou jogar!"
            )

            tutorials.forEach { text ->
                Text(
                    text = text,
                    color = com.example.ui.theme.DarkSecondaryText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        content()
    }
}

@Composable
fun BottomNavBar(selectedTab: String, onTabSelected: (String) -> Unit) {
    // Hidden empty bar placeholder implementation to maintain test compatibility seamlessly
    Spacer(modifier = Modifier.size(0.dp))
}

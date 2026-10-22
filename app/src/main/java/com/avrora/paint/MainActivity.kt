package com.avrora.paint

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AvroraPaintTheme {
                AppRoot()
            }
        }
    }
}

// Переключение между галереей работ и редактором.
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editor = remember { EditorState() }
    var doc by remember { mutableStateOf<Document?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var splash by remember { mutableStateOf(true) }

    if (splash) {
        SplashScreen { splash = false }
        return
    }

    val current = doc
    if (current == null) {
        GalleryScreen(
            refreshKey = refresh,
            onNew = { template -> doc = template.createDocument() },
            onOpen = { info ->
                if (!busy) {
                    busy = true
                    scope.launch {
                        val loaded = ProjectStore.load(context, info.id)
                        busy = false
                        if (loaded != null) {
                            doc = loaded
                        } else {
                            Toast.makeText(context, "Не удалось открыть работу", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    } else {
        EditorScreen(
            doc = current,
            editor = editor,
            onExit = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        if (current.dirty) ProjectStore.save(context, current)
                        busy = false
                        doc = null
                        refresh++
                    }
                }
            }
        )
    }

    if (busy) {
        Box(
            modifier = Modifier.fillMaxSize().background(AvroraColors.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AvroraColors.Fluorite)
        }
    }
}

// Заставка: логотип плавно проявляется со свечением. Касание — пропустить.
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        delay(300)
        onDone()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .auroraBackground()
            .clickable { onDone() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .graphicsLayer {
                    val p = progress.value
                    alpha = p
                    scaleX = 0.6f + 0.4f * p
                    scaleY = 0.6f + 0.4f * p
                }
                .background(
                    Brush.radialGradient(listOf(AvroraColors.Violet.copy(alpha = 0.45f), Color.Transparent))
                )
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.avrora_logo),
                contentDescription = "Avrora",
                modifier = Modifier
                    .size(width = 190.dp, height = 141.dp)
                    .graphicsLayer {
                        val p = progress.value
                        alpha = p
                        scaleX = 0.85f + 0.15f * p
                        scaleY = 0.85f + 0.15f * p
                    }
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "AVRORA PAINT",
                color = AvroraColors.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 8.sp,
                modifier = Modifier.graphicsLayer { alpha = ((progress.value - 0.4f) / 0.6f).coerceIn(0f, 1f) }
            )
        }
    }
}

package com.avrora.paint

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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

package com.bqsqwad.proedit

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bqsqwad.proedit.editor.BqClip
import com.bqsqwad.proedit.editor.BqMediaEngine
import com.bqsqwad.proedit.editor.BqProject
import com.bqsqwad.proedit.editor.BqProjectHistory
import com.bqsqwad.proedit.editor.BqSuperpowerFxCatalog
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BqProEditApp() }
    }
}

@OptIn(UnstableApi::class)
@androidx.compose.runtime.Composable
private fun BqProEditApp() {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val engine = remember { BqMediaEngine(context) }
    val history = remember { BqProjectHistory() }
    var project by remember { mutableStateOf(history.current) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var exportState by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = BqClip(
            id = java.util.UUID.randomUUID().toString(),
            uri = uri,
            name = uri.lastPathSegment ?: "Imported clip"
        )
        val next = project.copy(clips = project.clips + clip, selectedClipId = clip.id)
        history.commit(next)
        project = next
        player.setMediaItem(engine.mediaItem(uri))
        player.prepare()
        player.playWhenReady = false
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(1L)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    androidx.compose.runtime.LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = player.currentPosition
            kotlinx.coroutines.delay(100)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("BQ Pro Edit", style = MaterialTheme.typography.headlineSmall)
                    Text("BQ Sqwad Technologies · Professional Editor", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(onClick = {
                    history.undo()?.let { project = it }
                    project.selectedClipId?.let { id -> project.clips.find { it.id == id }?.let { player.setMediaItem(engine.mediaItem(it.uri)); player.prepare() } }
                }, enabled = history.canUndo) { Text("Undo") }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = {
                    history.redo()?.let { project = it }
                }, enabled = history.canRedo) { Text("Redo") }
            }

            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { PlayerView(it).apply { this.player = player; useController = true } }
                )
                if (project.clips.isEmpty()) {
                    Text("Import a video to begin", modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium)
                }
            }

            if (duration > 1L && project.clips.isNotEmpty()) {
                Slider(
                    value = position.coerceIn(0L, duration).toFloat(),
                    onValueChange = { player.seekTo(it.toLong()); position = it.toLong() },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${position / 1000}s / ${duration / 1000}s", style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Button(onClick = { picker.launch(arrayOf("video/*")) }) { Text("Import Video") }
                Button(onClick = { if (player.isPlaying) player.pause() else player.play() }) { Text(if (isPlaying) "Pause" else "Play") }
                OutlinedButton(onClick = {
                    val id = project.selectedClipId ?: return@OutlinedButton
                    val clip = project.clips.firstOrNull { it.id == id } ?: return@OutlinedButton
                    val next = project.copy(clips = project.clips.map { if (it.id == id) it.copy(startMs = player.currentPosition) else it })
                    history.commit(next); project = next
                }, enabled = project.selectedClipId != null) { Text("Set In") }
                OutlinedButton(onClick = {
                    val id = project.selectedClipId ?: return@OutlinedButton
                    val clip = project.clips.firstOrNull { it.id == id } ?: return@OutlinedButton
                    val next = project.copy(clips = project.clips.map { if (it.id == id) it.copy(endMs = player.currentPosition.coerceAtLeast(clip.startMs + 1)) else it })
                    history.commit(next); project = next
                }, enabled = project.selectedClipId != null) { Text("Set Out") }
                OutlinedButton(onClick = {
                    val id = project.selectedClipId ?: return@OutlinedButton
                    val next = project.copy(clips = project.clips.filterNot { it.id == id }, selectedClipId = project.clips.firstOrNull { it.id != id }?.id)
                    history.commit(next); project = next
                    player.stop(); player.clearMediaItems()
                }, enabled = project.selectedClipId != null) { Text("Delete") }
                Button(onClick = {
                    val id = project.selectedClipId ?: return@Button
                    val clip = project.clips.firstOrNull { it.id == id } ?: return@Button
                    exportState = "Exporting…"
                    engine.export(clip, onStarted = { }, onCompleted = { file ->
                        saveToGallery(context, file)
                        exportState = "Export complete"
                    }, onError = { exportState = "Export failed: ${it.errorCodeName}" })
                }, enabled = project.selectedClipId != null && exportState == null) { Text("Export MP4") }
            }

            exportState?.let {
                Text(it, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.primary)
                if (it != "Exporting…") {
                    OutlinedButton(onClick = { exportState = null }, modifier = Modifier.padding(top = 4.dp)) { Text("Dismiss") }
                }
            }

            Text("Timeline", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(project.clips, key = { it.id }) { clip ->
                    Card(onClick = {
                        val next = project.copy(selectedClipId = clip.id)
                        history.commit(next); project = next
                        player.setMediaItem(engine.mediaItem(clip.uri)); player.prepare()
                    }) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(if (clip.id == project.selectedClipId) "▶ ${clip.name}" else clip.name, maxLines = 1)
                            Text("In ${clip.startMs / 1000}s · Out ${clip.endMs?.div(1000) ?: "end"}s", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Text("BQ Superpower FX", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BqSuperpowerFxCatalog.effects) { effect ->
                    OutlinedButton(onClick = { }) { Text(effect.name) }
                }
            }
        }
    }
}

private fun saveToGallery(context: android.content.Context, file: File) {
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "BQ_Pro_Edit_${System.currentTimeMillis()}.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/BQ Pro Edit")
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
    try {
        resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
        values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
    }
}

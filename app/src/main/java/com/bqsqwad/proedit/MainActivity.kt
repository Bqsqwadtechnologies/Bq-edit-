package com.bqsqwad.proedit

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.bqsqwad.proedit.editor.BqProjectStore
import com.bqsqwad.proedit.editor.BqSuperpowerFxCatalog
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BqProEditApp() }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun BqProEditApp() {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val engine = remember { BqMediaEngine(context) }
    val store = remember { BqProjectStore(context) }
    val savedProject = remember { store.load() }
    val history = remember { BqProjectHistory(savedProject ?: BqProject()) }
    var project by remember { mutableStateOf(history.current) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(project.playheadMs) }
    var duration by remember { mutableLongStateOf(1L) }
    var exportState by remember { mutableStateOf<String?>(null) }

    fun commit(next: BqProject) {
        history.commit(next)
        project = next
        store.save(next)
    }

    fun loadClip(clip: BqClip) {
        player.setMediaItem(engine.mediaItem(clip.uri))
        player.prepare()
        player.playWhenReady = false
        position = clip.startMs
        player.seekTo(clip.startMs)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val clips = uris.map { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            BqClip(
                id = UUID.randomUUID().toString(),
                uri = uri,
                name = uri.lastPathSegment ?: "Imported clip"
            )
        }
        val next = project.copy(
            clips = project.clips + clips,
            selectedClipId = clips.last().id
        )
        commit(next)
        loadClip(clips.last())
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(1L)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = player.currentPosition
            val selected = project.selectedClipId
            if (selected != null) {
                store.save(project.copy(playheadMs = position))
            }
            kotlinx.coroutines.delay(100)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(R.drawable.bq_pro_edit_logo),
                    contentDescription = "BQ Pro Edit logo",
                    modifier = Modifier.width(56.dp).height(56.dp).padding(end = 8.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("BQ Pro Edit", style = MaterialTheme.typography.headlineSmall)
                    Text("BQ Sqwad Technologies · Professional Editor", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(onClick = { history.undo()?.let { project = it; store.save(it) } }, enabled = history.canUndo) {
                    Text("Undo")
                }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = { history.redo()?.let { project = it; store.save(it) } }, enabled = history.canRedo) {
                    Text("Redo")
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { PlayerView(it).apply { player = player; useController = true } }
                )
                if (project.clips.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.bq_pro_edit_logo),
                            contentDescription = null,
                            modifier = Modifier.width(88.dp).height(88.dp).padding(bottom = 8.dp)
                        )
                        Text("Import videos to begin", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            if (duration > 1L && project.clips.isNotEmpty()) {
                Slider(
                    value = position.coerceIn(0L, duration).toFloat(),
                    onValueChange = {
                        position = it.toLong()
                        player.seekTo(position)
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${position / 1000}s / ${duration / 1000}s", style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                Button(onClick = { picker.launch(arrayOf("video/*")) }) { Text("Import Videos") }
                Button(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Text(if (isPlaying) "Pause" else "Play")
                }
                OutlinedButton(onClick = {
                    val id = project.selectedClipId ?: return@OutlinedButton
                    val clip = project.clips.firstOrNull { it.id == id } ?: return@OutlinedButton
                    commit(project.copy(clips = project.clips.map { if (it.id == id) it.copy(startMs = player.currentPosition) else it }))
                }, enabled = project.selectedClipId != null) { Text("Set In") }
                OutlinedButton(onClick = {
                    val id = project.selectedClipId ?: return@OutlinedButton
                    val clip = project.clips.firstOrNull { it.id == id } ?: return@OutlinedButton
                    val out = player.currentPosition.coerceAtLeast(clip.startMs + 1L)
                    commit(project.copy(clips = project.clips.map { if (it.id == id) it.copy(endMs = out) else it }))
                }, enabled = project.selectedClipId != null) { Text("Set Out") }
                OutlinedButton(onClick = {
                    val id = project.selectedClipId ?: return@OutlinedButton
                    val clip = project.clips.firstOrNull { it.id == id } ?: return@OutlinedButton
                    val copy = clip.copy(id = UUID.randomUUID().toString(), name = "${clip.name} copy")
                    val index = project.clips.indexOfFirst { it.id == id }
                    val nextClips = project.clips.toMutableList().apply { add((index + 1).coerceAtMost(size), copy) }
                    commit(project.copy(clips = nextClips, selectedClipId = copy.id))
                    loadClip(copy)
                }, enabled = project.selectedClipId != null) { Text("Duplicate") }
                OutlinedButton(onClick = {
                    val id = project.selectedClipId ?: return@OutlinedButton
                    val index = project.clips.indexOfFirst { it.id == id }
                    if (index > 0) {
                        val nextClips = project.clips.toMutableList()
                        val item = nextClips.removeAt(index)
                        nextClips.add(index - 1, item)
                        commit(project.copy(clips = nextClips))
                    }
                }, enabled = project.selectedClipId != null) { Text("← Move") }
                OutlinedButton(onClick = {
                    val id = project.selectedClipId ?: return@OutlinedButton
                    val index = project.clips.indexOfFirst { it.id == id }
                    if (index >= 0 && index < project.clips.lastIndex) {
                        val nextClips = project.clips.toMutableList()
                        val item = nextClips.removeAt(index)
                        nextClips.add(index + 1, item)
                        commit(project.copy(clips = nextClips))
                    }
                }, enabled = project.selectedClipId != null) { Text("Move →") }
                OutlinedButton(onClick = {
                    val id = project.selectedClipId ?: return@OutlinedButton
                    val next = project.clips.filterNot { it.id == id }
                    commit(project.copy(clips = next, selectedClipId = next.firstOrNull()?.id))
                    player.stop(); player.clearMediaItems()
                }, enabled = project.selectedClipId != null) { Text("Delete") }
                Button(onClick = {
                    if (project.clips.isEmpty()) return@Button
                    exportState = "Exporting…"
                    engine.exportProject(project,
                        onStarted = { },
                        onCompleted = { file ->
                            saveToGallery(context, file)
                            exportState = "Export complete"
                        },
                        onError = { exportState = "Export failed: ${it.errorCodeName}" }
                    )
                }, enabled = project.clips.isNotEmpty() && exportState == null) { Text("Export MP4") }
            }

            project.selectedClipId?.let { id ->
                val clip = project.clips.firstOrNull { it.id == id }
                if (clip != null) {
                    Text("Selected: ${clip.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        OutlinedButton(onClick = {
                            commit(project.copy(clips = project.clips.map { if (it.id == id) it.copy(rotationDegrees = it.rotationDegrees + 90f) else it }))
                        }) { Text("Rotate 90°") }
                        OutlinedButton(onClick = {
                            val speed = (clip.speed + 0.25f).coerceAtMost(4f)
                            commit(project.copy(clips = project.clips.map { if (it.id == id) it.copy(speed = speed) else it }))
                        }) { Text("Speed +") }
                        OutlinedButton(onClick = {
                            val speed = (clip.speed - 0.25f).coerceAtLeast(0.25f)
                            commit(project.copy(clips = project.clips.map { if (it.id == id) it.copy(speed = speed) else it }))
                        }) { Text("Speed −") }
                        OutlinedButton(onClick = {
                            val scale = (clip.scale + 0.1f).coerceAtMost(4f)
                            commit(project.copy(clips = project.clips.map { if (it.id == id) it.copy(scale = scale) else it }))
                        }) { Text("Scale +") }
                        OutlinedButton(onClick = {
                            val scale = (clip.scale - 0.1f).coerceAtLeast(0.25f)
                            commit(project.copy(clips = project.clips.map { if (it.id == id) it.copy(scale = scale) else it }))
                        }) { Text("Scale −") }
                        Text("${clip.speed}× · ${clip.scale}× · ${clip.rotationDegrees}°", modifier = Modifier.padding(top = 10.dp))
                    }
                }
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
                        commit(project.copy(selectedClipId = clip.id))
                        loadClip(clip)
                    }) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(if (clip.id == project.selectedClipId) "▶ ${clip.name}" else clip.name, maxLines = 1)
                            Text("In ${clip.startMs / 1000}s · Out ${clip.endMs?.div(1000) ?: "end"}", style = MaterialTheme.typography.labelSmall)
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
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
    }
}

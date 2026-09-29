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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.bqsqwad.proedit.editor.BqEffectKind
import com.bqsqwad.proedit.editor.BqEffectLayer
import com.bqsqwad.proedit.editor.BqExportConfig
import com.bqsqwad.proedit.editor.BqMediaEngine
import com.bqsqwad.proedit.editor.BqMediaType
import com.bqsqwad.proedit.editor.BqProject
import com.bqsqwad.proedit.editor.BqProjectHistory
import com.bqsqwad.proedit.editor.BqProjectStore
import com.bqsqwad.proedit.editor.BqProjectValidator
import com.bqsqwad.proedit.editor.BqSuperpowerFxCatalog
import com.bqsqwad.proedit.editor.BqSuperpowerFxKind
import com.bqsqwad.proedit.editor.BqTimelineOps
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay

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
    val initial = remember { store.load() ?: BqProject() }
    val history = remember { BqProjectHistory(initial) }
    var project by remember { mutableStateOf(history.current) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(project.playheadMs) }
    var duration by remember { mutableLongStateOf(1L) }
    var status by remember { mutableStateOf<String?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var inspectorTab by remember { mutableStateOf(0) }
    var exportBusy by remember { mutableStateOf(false) }

    fun commit(next: BqProject) {
        val errors = BqProjectValidator.validate(next)
        if (errors.isNotEmpty()) { status = errors.first(); return }
        history.commit(next.copy(lastSavedAt = System.currentTimeMillis()))
        project = history.current
        store.save(project)
    }

    fun selectClip(clip: BqClip) {
        commit(project.copy(selectedClipId = clip.id))
        if (clip.mediaType == BqMediaType.VIDEO) {
            runCatching {
                player.setVideoEffects(engine.videoEffects(clip))
                player.setMediaItem(engine.mediaItem(clip), clip.startMs)
                player.prepare()
                player.playWhenReady = false
                position = clip.startMs
                player.seekTo(clip.startMs)
            }.onFailure { status = "Unable to preview media: ${it.message ?: "unknown error"}" }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val newClips = uris.mapNotNull { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val mime = context.contentResolver.getType(uri).orEmpty()
            val type = when {
                mime.startsWith("image/") -> BqMediaType.IMAGE
                mime.startsWith("audio/") -> BqMediaType.AUDIO
                else -> BqMediaType.VIDEO
            }
            val name = uri.lastPathSegment ?: "Imported media"
            if (project.clips.any { it.uri == uri && it.mediaType == type }) null
            else BqClip(UUID.randomUUID().toString(), uri, name, mediaType = type)
        }
        if (newClips.isNotEmpty()) {
            val assets = newClips.map { clip -> engine.inspect(clip.uri, clip.name).copy(type = clip.mediaType) }
            commit(project.copy(clips = project.clips + newClips, assets = project.assets + assets, selectedClipId = newClips.last().id))
            selectClip(newClips.last())
            status = "Imported ${newClips.size} media item(s)"
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(1L) }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    LaunchedEffect(project) {
        delay(700)
        store.save(project)
    }
    LaunchedEffect(project.selectedClipId, project.clips) {
        project.clips.firstOrNull { it.id == project.selectedClipId }?.let { clip ->
            player.setPlaybackSpeed(clip.speed.coerceIn(0.25f, 4f))
            player.setVideoEffects(engine.videoEffects(clip))
        }
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = player.currentPosition
            project.selectedClipId?.let { id -> if (project.clips.any { it.id == id }) store.save(project.copy(playheadMs = position)) }
            delay(100)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(com.bqsqwad.proedit.R.drawable.bq_pro_edit_logo), "BQ Pro Edit", Modifier.width(48.dp).height(48.dp))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(project.name, style = MaterialTheme.typography.titleLarge)
                    Text("BQ Pro Edit · BQ Sqwad Technologies", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = { showRename = true }) { Text("Rename") }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { history.undo()?.let { project = it; store.save(it) } }, enabled = history.canUndo) { Text("Undo") }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { history.redo()?.let { project = it; store.save(it) } }, enabled = history.canRedo) { Text("Redo") }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1.7f).fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        AndroidView(Modifier.fillMaxSize(), factory = { PlayerView(it).apply { player = player; useController = false } })
                        if (project.clips.none { it.mediaType == BqMediaType.VIDEO }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(painterResource(com.bqsqwad.proedit.R.drawable.bq_pro_edit_logo), null, Modifier.width(72.dp).height(72.dp))
                                Text("Import video to start editing")
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { if (player.isPlaying) player.pause() else player.play() }, enabled = project.clips.any { it.mediaType == BqMediaType.VIDEO }) { Text(if (isPlaying) "Pause" else "Play") }
                        Spacer(Modifier.width(6.dp))
                        Text("${position / 1000}s / ${duration / 1000}s")
                        Slider(value = position.coerceIn(0L, duration).toFloat(), onValueChange = { position = it.toLong(); player.seekTo(position) }, valueRange = 0f..duration.toFloat(), Modifier.weight(1f).padding(horizontal = 8.dp))
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { picker.launch(arrayOf("video/*", "image/*", "audio/*")) }) { Text("Import Media") }
                        OutlinedButton(onClick = {
                            val id = project.selectedClipId ?: return@OutlinedButton
                            val clip = project.clips.firstOrNull { it.id == id } ?: return@OutlinedButton
                            val effectiveEnd = clip.endMs ?: project.assets.firstOrNull { it.uri == clip.uri }?.durationMs ?: 0L
                            val pair = BqTimelineOps.split(clip.copy(endMs = effectiveEnd.takeIf { it > clip.startMs }), position)
                            if (pair == null) status = "Move the playhead inside the selected clip to split"
                            else {
                                val index = project.clips.indexOfFirst { it.id == id }
                                val list = project.clips.toMutableList().apply { removeAt(index); add(index, pair.first); add(index + 1, pair.second) }
                                commit(project.copy(clips = list, selectedClipId = pair.second.id))
                            }
                        }, enabled = project.selectedClipId != null) { Text("Split") }
                        OutlinedButton(onClick = {
                            val id = project.selectedClipId ?: return@OutlinedButton
                            val clip = project.clips.firstOrNull { it.id == id } ?: return@OutlinedButton
                            val copy = BqTimelineOps.duplicate(clip)
                            val index = project.clips.indexOfFirst { it.id == id }
                            val list = project.clips.toMutableList().apply { add(index + 1, copy) }
                            commit(project.copy(clips = list, selectedClipId = copy.id))
                        }, enabled = project.selectedClipId != null) { Text("Duplicate") }
                        OutlinedButton(onClick = {
                            val id = project.selectedClipId ?: return@OutlinedButton
                            val index = project.clips.indexOfFirst { it.id == id }
                            if (index > 0) {
                                val list = project.clips.toMutableList()
                                val moved = list.removeAt(index)
                                list.add(index - 1, moved)
                                commit(project.copy(clips = list))
                            }
                        }, enabled = project.selectedClipId?.let { id -> project.clips.indexOfFirst { it.id == id } > 0 } == true) { Text("← Move") }
                        OutlinedButton(onClick = {
                            val id = project.selectedClipId ?: return@OutlinedButton
                            val index = project.clips.indexOfFirst { it.id == id }
                            if (index >= 0 && index < project.clips.lastIndex) {
                                val list = project.clips.toMutableList()
                                val moved = list.removeAt(index)
                                list.add(index + 1, moved)
                                commit(project.copy(clips = list))
                            }
                        }, enabled = project.selectedClipId?.let { id -> project.clips.indexOfFirst { it.id == id } >= 0 && project.clips.indexOfFirst { it.id == id } < project.clips.lastIndex } == true) { Text("Move →") }
                        OutlinedButton(onClick = {
                            val id = project.selectedClipId ?: return@OutlinedButton
                            val clip = project.clips.firstOrNull { it.id == id } ?: return@OutlinedButton
                            if (position <= clip.startMs) status = "Move the playhead after the clip start to trim"
                            else commit(project.copy(clips = project.clips.map { if (it.id == id) it.copy(startMs = position.coerceAtMost((it.endMs ?: Long.MAX_VALUE) - 1L)) else it }))
                        }, enabled = project.selectedClipId != null) { Text("Trim In") }
                        OutlinedButton(onClick = {
                            val id = project.selectedClipId ?: return@OutlinedButton
                            val clip = project.clips.firstOrNull { it.id == id } ?: return@OutlinedButton
                            val end = clip.endMs ?: project.assets.firstOrNull { it.uri == clip.uri }?.durationMs
                            if (end == null || position <= clip.startMs || position >= end) status = "Move the playhead inside the clip to trim its end"
                            else commit(project.copy(clips = project.clips.map { if (it.id == id) it.copy(endMs = position) else it }))
                        }, enabled = project.selectedClipId != null) { Text("Trim Out") }
                        OutlinedButton(onClick = {
                            val id = project.selectedClipId ?: return@OutlinedButton
                            val list = project.clips.filterNot { it.id == id }
                            commit(project.copy(clips = list, selectedClipId = list.firstOrNull()?.id))
                            player.stop(); player.clearMediaItems()
                        }, enabled = project.selectedClipId != null) { Text("Delete") }
                        Button(onClick = {
                            if (exportBusy) return@Button
                            exportBusy = true; status = "Exporting MP4…"
                            engine.exportProject(project, BqExportConfig(), onStarted = {}, onProgress = {}, onCompleted = { file -> saveToGallery(context, file); exportBusy = false; status = "Export complete" }, onError = { exportBusy = false; status = "Export failed: ${it.message ?: "unknown error"}" })
                        }, enabled = project.clips.any { it.mediaType == BqMediaType.VIDEO } && !exportBusy) { Text(if (exportBusy) "Exporting…" else "Export MP4") }
                        if (exportBusy) OutlinedButton(onClick = { engine.cancelExport(); exportBusy = false; status = "Export cancelled" }) { Text("Cancel") }
                    }
                    Text("Timeline · ${project.clips.size} clips", style = MaterialTheme.typography.titleMedium, Modifier.padding(vertical = 4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        items(project.clips, key = { it.id }) { clip ->
                            Card(onClick = { selectClip(clip) }) {
                                Column(Modifier.padding(9.dp).width(150.dp)) {
                                    Text(if (clip.id == project.selectedClipId) "● ${clip.name}" else clip.name, maxLines = 1)
                                    Text("${clip.mediaType} · ${clip.speed}×", style = MaterialTheme.typography.labelSmall)
                                    Text("${clip.startMs}–${clip.endMs ?: "end"} ms", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))
                Card(Modifier.weight(0.9f).fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = inspectorTab) {
                            listOf("Inspector", "Effects", "FX", "Media").forEachIndexed { index, title -> Tab(selected = inspectorTab == index, onClick = { inspectorTab = index }, text = { Text(title) }) }
                        }
                        val selected = project.clips.firstOrNull { it.id == project.selectedClipId }
                        when (inspectorTab) {
                            0 -> Inspector(selected, project, ::commit)
                            1 -> EffectsPanel(selected, project, ::commit)
                            2 -> SuperpowerPanel(selected, project, ::commit)
                            else -> MediaPanel(project, ::selectClip)
                        }
                    }
                }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary, Modifier.padding(top = 5.dp)) }
        }
    }

    if (showRename) {
        var value by remember(project.id) { mutableStateOf(project.name) }
        AlertDialog(onDismissRequest = { showRename = false }, title = { Text("Rename project") }, text = { androidx.compose.material3.OutlinedTextField(value, { value = it }, label = { Text("Project name") }) }, confirmButton = { TextButton(onClick = { commit(project.copy(name = value.trim().ifBlank { "Untitled Project" })); showRename = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } })
    }
}

@Composable
private fun Inspector(selected: BqClip?, project: BqProject, commit: (BqProject) -> Unit) {
    if (selected == null) { Text("Select a clip from the timeline", Modifier.padding(16.dp)); return }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(selected.name, style = MaterialTheme.typography.titleMedium) }
        item { Text("Speed ${selected.speed}×") }
        item { Slider(selected.speed, { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(speed = v) else it })) }, .25f..4f) }
        item { Text("Scale ${selected.scale}") }
        item { Slider(selected.scale, { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(scale = v) else it })) }, .25f..3f) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(onClick = { commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(rotationDegrees = it.rotationDegrees + 90f) else it })) }) { Text("Rotate +90°") }; OutlinedButton(onClick = { commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(rotationDegrees = 0f) else it })) }) { Text("Reset") } } }
        item { Text("Position X ${selected.x}") }
        item { Slider(selected.x, { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(x = v) else it })) }, -1f..1f) }
        item { Text("Position Y ${selected.y}") }
        item { Slider(selected.y, { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(y = v) else it })) }, -1f..1f) }
        item { Text("Volume ${selected.volume}") }
        item { Slider(selected.volume, { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(volume = v) else it })) }, 0f..2f) }
        item { Text("Audio track", style = MaterialTheme.typography.titleSmall) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(audio = it.audio.copy(muted = !it.audio.muted)) else it }))
                }) { Text(if (selected.audio.muted) "Unmute" else "Mute") }
                OutlinedButton(onClick = {
                    commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(audio = it.audio.copy(solo = !it.audio.solo)) else it }))
                }) { Text(if (selected.audio.solo) "Unsolo" else "Solo") }
            }
        }
        item { Text("Fade in: ${selected.audio.fadeInMs / 1000f}s") }
        item { Slider(selected.audio.fadeInMs.toFloat(), { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(audio = it.audio.copy(fadeInMs = v.toLong())) else it })) }, 0f..10_000f) }
        item { Text("Fade out: ${selected.audio.fadeOutMs / 1000f}s") }
        item { Slider(selected.audio.fadeOutMs.toFloat(), { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(audio = it.audio.copy(fadeOutMs = v.toLong())) else it })) }, 0f..10_000f) }
        item { Text("Non-destructive: source media is never modified.", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun EffectsPanel(selected: BqClip?, project: BqProject, commit: (BqProject) -> Unit) {
    if (selected == null) { Text("Select a clip first", Modifier.padding(16.dp)); return }
    val kinds = BqEffectKind.entries
    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        item { Text("Color / image effects", style = MaterialTheme.typography.titleMedium) }
        items(kinds) { kind ->
            OutlinedButton(onClick = {
                val defaultAmount = when (kind) {
                    BqEffectKind.SATURATION -> 20f
                    BqEffectKind.BLUR, BqEffectKind.GLOW -> 4f
                    BqEffectKind.TEMPERATURE, BqEffectKind.TINT -> 8f
                    else -> 0.15f
                }
                val effect = BqEffectLayer(
                    UUID.randomUUID().toString(),
                    kind.name.replace('_', ' '),
                    kind,
                    parameters = mapOf("amount" to com.bqsqwad.proedit.editor.BqFloatParameter(defaultAmount))
                )
                commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(effects = it.effects + effect) else it }))
            }, Modifier.fillMaxWidth()) { Text("Add ${kind.name.lowercase()}") }
        }
        items(selected.effects, key = { it.id }) { effect ->
            val amount = effect.parameters["amount"]?.value ?: 0f
            val range = when (effect.kind) {
                BqEffectKind.SATURATION, BqEffectKind.TEMPERATURE, BqEffectKind.TINT -> -100f..100f
                BqEffectKind.BLUR -> 0.1f..40f
                BqEffectKind.GLOW -> 0.1f..20f
                else -> -1f..1f
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(effect.name, Modifier.weight(1f))
                        TextButton(onClick = {
                            val updated = effect.copy(enabled = !effect.enabled)
                            commit(project.copy(clips = project.clips.map { c ->
                                if (c.id == selected.id) c.copy(effects = c.effects.map { if (it.id == effect.id) updated else it }) else c
                            }))
                        }) { Text(if (effect.enabled) "On" else "Off") }
                        TextButton(onClick = {
                            commit(project.copy(clips = project.clips.map { c ->
                                if (c.id == selected.id) c.copy(effects = c.effects.filterNot { it.id == effect.id }) else c
                            }))
                        }) { Text("Remove") }
                    }
                    Text("Amount: %.2f".format(amount), style = MaterialTheme.typography.labelSmall)
                    Slider(value = amount.coerceIn(range.start, range.endInclusive), onValueChange = { value ->
                        val params = effect.parameters.toMutableMap()
                        params["amount"] = com.bqsqwad.proedit.editor.BqFloatParameter(value, effect.parameters["amount"]?.keyframes ?: emptyList())
                        val updated = effect.copy(parameters = params)
                        commit(project.copy(clips = project.clips.map { c ->
                            if (c.id == selected.id) c.copy(effects = c.effects.map { if (it.id == effect.id) updated else it }) else c
                        }))
                    }, valueRange = range)
                }
            }
        }
        item { Text("Stacked effects: ${selected.effects.size}. Changes are non-destructive and persisted.", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun SuperpowerPanel(selected: BqClip?, project: BqProject, commit: (BqProject) -> Unit) {
    if (selected == null) { Text("Select a clip first", Modifier.padding(16.dp)); return }
    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        item { Text("BQ Superpower FX", style = MaterialTheme.typography.titleMedium) }
        item { Text("Tracking/compositing state is stored non-destructively. Rendering modules can consume the same FX layer state.", style = MaterialTheme.typography.labelSmall) }
        items(BqSuperpowerFxCatalog.effects) { effect ->
            OutlinedButton(onClick = {
                val fx = com.bqsqwad.proedit.editor.BqFxLayer(UUID.randomUUID().toString(), effect.kind)
                commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(fxLayers = it.fxLayers + fx) else it }))
            }, Modifier.fillMaxWidth()) { Text(effect.name) }
        }
        item { Text("Configured layers: ${selected.fxLayers.size}", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun MediaPanel(project: BqProject, onSelect: (BqClip) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Project Media Bin", style = MaterialTheme.typography.titleMedium) }
        items(project.clips, key = { it.id }) { clip -> Card(onClick = { onSelect(clip) }) { Column(Modifier.padding(9.dp).fillMaxWidth()) { Text(clip.name); Text(clip.mediaType.name, style = MaterialTheme.typography.labelSmall); Text(clip.uri.toString(), style = MaterialTheme.typography.labelSmall, maxLines = 2) } } }
    }
}

private fun saveToGallery(context: android.content.Context, file: File) {
    val values = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, "BQ_Pro_Edit_${System.currentTimeMillis()}.mp4"); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4"); put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/BQ Pro Edit"); put(MediaStore.Video.Media.IS_PENDING, 1) }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
    try {
        resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("Unable to open gallery output")
        values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0); resolver.update(uri, values, null, null)
    } catch (e: Exception) { resolver.delete(uri, null, null) }
}

package com.bqsqwad.proedit

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bqsqwad.proedit.editor.BqClip
import com.bqsqwad.proedit.editor.BqColorGrade
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
import com.bqsqwad.proedit.editor.BqSubtitleSrt
import com.bqsqwad.proedit.editor.BqSubjectPart
import com.bqsqwad.proedit.editor.BqSubjectTarget
import com.bqsqwad.proedit.editor.BqTimelineOps
import com.bqsqwad.proedit.editor.BqTransitionKind
import com.bqsqwad.proedit.editor.BqTransitionSpec
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BqProEditTheme { BqProEditApp() } }
    }
}

private val BqLightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF2F4F7),
    onBackground = Color.Black,
    onSurface = Color.Black
)

private val BqDarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    background = Color.Black,
    surface = Color(0xFF090909),
    surfaceVariant = Color(0xFF181818),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
private fun BqProEditTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) BqDarkColors else BqLightColors, content = content)
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
    var showNewProject by remember { mutableStateOf(false) }
    var inspectorTab by remember { mutableStateOf(0) }
    var exportBusy by remember { mutableStateOf(false) }
    var exportConfig by remember { mutableStateOf(BqExportConfig()) }
    var showGuide by remember { mutableStateOf(!context.getSharedPreferences("bq_pro_edit_onboarding", 0).getBoolean("seen", false)) }
    var guideFeature by remember { mutableStateOf<BqGuideFeature?>(null) }
    fun commit(next: BqProject) {
        val errors = BqProjectValidator.validate(next)
        if (errors.isNotEmpty()) { status = errors.first(); return }
        history.commit(next.copy(lastSavedAt = System.currentTimeMillis()))
        project = history.current
        store.save(project)
    }

    val srtImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" }.onSuccess { text -> val caps = BqSubtitleSrt.import(text); if (caps.isEmpty()) status = "No valid SRT captions found" else { commit(project.copy(captions = caps)); status = "Imported ${caps.size} caption(s)" } }.onFailure { status = "SRT import failed" } }
    val srtExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri -> if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(BqSubtitleSrt.export(project.captions)) } ?: error("Unable to write SRT") }.onSuccess { status = "SRT exported" }.onFailure { status = "SRT export failed" } }

    fun selectClip(clip: BqClip) {
        val selectedProject = project.copy(selectedClipId = clip.id)
        commit(selectedProject)
        if (clip.mediaType == BqMediaType.VIDEO) {
            runCatching {
                player.setVideoEffects(engine.videoEffects(selectedProject, clip))
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
            else {
                val asset = engine.inspect(uri, name).copy(type = type)
                BqClip(UUID.randomUUID().toString(), uri, name, endMs = asset.durationMs.takeIf { it > 0L }, mediaType = type)
            }
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
            player.setVideoEffects(engine.videoEffects(project, clip))
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
                OutlinedButton(onClick = { showGuide = true }) { Text("Guide") }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { showRename = true }) { Text("Rename") }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { showNewProject = true }) { Text("New") }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = {
                    val copy = project.copy(id = UUID.randomUUID().toString(), name = project.name + " Copy", lastSavedAt = System.currentTimeMillis())
                    history.commit(copy)
                    project = history.current
                    store.save(project)
                    status = "Project duplicated"
                }) { Text("Duplicate") }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { history.undo()?.let { project = it; store.save(it) } }, enabled = history.canUndo) { Text("Undo") }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { history.redo()?.let { project = it; store.save(it) } }, enabled = history.canRedo) { Text("Redo") }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1.7f).fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = false } }, modifier = Modifier.fillMaxSize())
                        if (project.clips.none { it.mediaType == BqMediaType.VIDEO }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(painterResource(com.bqsqwad.proedit.R.drawable.bq_pro_edit_logo), null, Modifier.width(72.dp).height(72.dp))
                                Text("Import video to start editing")
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { if (player.isPlaying) player.pause() else player.play() }, enabled = project.clips.any { it.mediaType == BqMediaType.VIDEO }) { Text(if (isPlaying) "Pause" else "Play") }
                        OutlinedButton(onClick = {
                            val clip = project.clips.firstOrNull { it.id == project.selectedClipId }
                            if (clip != null) runCatching {
                                val retriever = MediaMetadataRetriever()
                                retriever.setDataSource(context, clip.uri)
                                val bitmap = retriever.getFrameAtTime((position * 1000L).coerceAtLeast(0L), MediaMetadataRetriever.OPTION_CLOSEST)
                                retriever.release()
                                if (bitmap != null) saveFrameToGallery(context, bitmap)
                                status = if (bitmap != null) "Frame captured" else "Frame capture unavailable"
                            }.onFailure { status = "Frame capture failed: " + (it.message ?: "unknown error") }
                        }, enabled = project.clips.any { it.mediaType == BqMediaType.VIDEO }) { Text("Frame") }
                        Spacer(Modifier.width(6.dp))
                        Text("${position / 1000}s / ${duration / 1000}s")
                        Slider(value = position.coerceIn(0L, duration).toFloat(), onValueChange = { position = it.toLong(); player.seekTo(position) }, valueRange = 0f..duration.toFloat(), modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Export:", style = MaterialTheme.typography.labelSmall)
                            OutlinedButton(onClick = { exportConfig = BqExportConfig(1920,1080,30,12_000_000) }, enabled = !exportBusy) { Text("FHD 30") }
                            OutlinedButton(onClick = { exportConfig = BqExportConfig(1280,720,30,8_000_000) }, enabled = !exportBusy) { Text("HD 30") }
                            OutlinedButton(onClick = { exportConfig = BqExportConfig(1920,1080,60,20_000_000) }, enabled = !exportBusy) { Text("FHD 60") }
                        }
                        Button(onClick = {
                            if (exportBusy) return@Button
                            val errors = BqProjectValidator.validate(project)
                            if (errors.isNotEmpty()) { status = errors.first(); return@Button }
                            exportBusy = true
                            status = "Exporting " + exportConfig.width + "×" + exportConfig.height + " @ " + exportConfig.fps + "fps…"
                            engine.exportProject(project, exportConfig, onStarted = {}, onProgress = {}, onCompleted = { file -> saveToGallery(context, file); exportBusy = false; status = "Export complete" }, onError = { exportBusy = false; status = "Export failed: " + (it.message ?: "unknown error") })
                        }, enabled = project.clips.any { it.mediaType == BqMediaType.VIDEO } && !exportBusy) { Text(if (exportBusy) "Exporting…" else "Export MP4") }
                        if (exportBusy) OutlinedButton(onClick = { engine.cancelExport(); exportBusy = false; status = "Export cancelled" }) { Text("Cancel") }
                    }
                    Text("Timeline · ${project.clips.size} clips", modifier = Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        items(project.clips, key = { it.id }) { clip ->
                            Card(onClick = { selectClip(clip) }) {
                                Column(Modifier.padding(9.dp).width(190.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(if (clip.id == project.selectedClipId) "● ${clip.name}" else clip.name, maxLines = 1)
                                    Text("${clip.mediaType} · ${clip.speed}×", style = MaterialTheme.typography.labelSmall)
                                    Text("${clip.startMs}–${clip.endMs ?: "end"} ms", style = MaterialTheme.typography.labelSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        TextButton(onClick = {
                                            val pair = BqTimelineOps.split(clip, project.playheadMs)
                                            if (pair == null) status = "Split needs a playhead inside the clip"
                                            else {
                                                val (left, right) = pair
                                                commit(project.copy(clips = project.clips.flatMap { if (it.id == clip.id) listOf(left, right) else listOf(it) }, selectedClipId = right.id))
                                                status = "Clip split at ${project.playheadMs} ms"
                                            }
                                        }) { Text("Split") }
                                        TextButton(onClick = {
                                            val end = clip.endMs
                                            if (end == null || project.playheadMs <= clip.startMs || project.playheadMs >= end) status = "Trim needs a playhead inside the clip"
                                            else {
                                                val trimmed = BqTimelineOps.trim(clip, clip.startMs, project.playheadMs)
                                                if (trimmed != null) { commit(project.copy(clips = project.clips.map { if (it.id == clip.id) trimmed else it })); status = "Trimmed to playhead" }
                                            }
                                        }) { Text("Trim") }
                                        TextButton(onClick = {
                                            val copy = BqTimelineOps.duplicate(clip)
                                            commit(project.copy(clips = project.clips + copy, selectedClipId = copy.id)); status = "Clip duplicated"
                                        }) { Text("Copy") }
                                        TextButton(onClick = {
                                            if (project.clips.size <= 1) status = "Keep at least one clip in the project"
                                            else { commit(project.copy(clips = project.clips.filterNot { it.id == clip.id }, selectedClipId = project.clips.firstOrNull { it.id != clip.id }?.id)); status = "Clip deleted" }
                                        }) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))
                Card(Modifier.weight(0.9f).fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = inspectorTab) {
                            listOf("Inspector", "Color", "Effects", "FX", "Media", "Captions", "Keyframes", "Chroma", "Advanced").forEachIndexed { index, title -> Tab(selected = inspectorTab == index, onClick = { inspectorTab = index }, text = { Text(title) }) }
                        }
                        val selected = project.clips.firstOrNull { it.id == project.selectedClipId }
                        when (inspectorTab) {
                            0 -> Inspector(selected, project, ::commit)
                            1 -> ColorGradePanel(selected, project, ::commit)
                            2 -> EffectsPanel(selected, project, ::commit)
                            3 -> SuperpowerPanel(selected, project, ::commit)
                            4 -> MediaPanel(project, ::selectClip, { picker.launch(arrayOf("video/*", "image/*", "audio/*")) })
                            5 -> CaptionsPanel(project, ::commit, { srtImport.launch(arrayOf("text/plain", "application/x-subrip", "text/srt")) }, { srtExport.launch("BQ_Pro_Edit_Captions.srt") })
                            6 -> KeyframesPanel(selected, project, ::commit)
                            7 -> ChromaPanel(selected, project, ::commit)
                            else -> AdvancedPanel(project, ::commit)
                        }
                    }
                }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 5.dp)) }
        }
    }

    if (showGuide) {
        BqFeatureGuide(
            onDismiss = { showGuide = false; context.getSharedPreferences("bq_pro_edit_onboarding", 0).edit().putBoolean("seen", true).apply() },
            onShow = { feature -> guideFeature = feature },
            onCloseSpotlight = { guideFeature = null }
        )
    }
    guideFeature?.let { feature ->
        BqFeatureSpotlight(feature = feature, onDismiss = { guideFeature = null })
    }

    if (showRename) {
        var value by remember(project.id) { mutableStateOf(project.name) }
        AlertDialog(onDismissRequest = { showRename = false }, title = { Text("Rename project") }, text = { androidx.compose.material3.OutlinedTextField(value, { value = it }, label = { Text("Project name") }) }, confirmButton = { TextButton(onClick = { commit(project.copy(name = value.trim().ifBlank { "Untitled Project" })); showRename = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } })
    if (showNewProject) {
        AlertDialog(
            onDismissRequest = { showNewProject = false },
            title = { Text("New project") },
            text = { Text("Start a fresh project? The current project is already saved locally and can be reopened by restarting BQ Pro Edit.") },
            confirmButton = {
                TextButton(onClick = {
                    val fresh = BqProject()
                    history.commit(fresh)
                    project = history.current
                    store.save(project)
                    position = 0L
                    duration = 1L
                    player.stop()
                    player.clearMediaItems()
                    showNewProject = false
                    status = "New project created"
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewProject = false }) { Text("Cancel") } }
        )
    }

    }
}

@Composable
private fun Inspector(selected: BqClip?, project: BqProject, commit: (BqProject) -> Unit) {
    if (selected == null) { Text("Select a clip from the timeline", Modifier.padding(16.dp)); return }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(selected.name, style = MaterialTheme.typography.titleMedium) }
        item { Text("Speed ${selected.speed}×") }
        item { Slider(value = selected.speed, onValueChange = { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(speed = v) else it })) }, valueRange = .25f..4f) }
        item { Text("Scale ${selected.scale}") }
        item { Slider(value = selected.scale, onValueChange = { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(scale = v) else it })) }, valueRange = .25f..3f) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(onClick = { commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(rotationDegrees = it.rotationDegrees + 90f) else it })) }) { Text("Rotate +90°") }; OutlinedButton(onClick = { commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(rotationDegrees = 0f) else it })) }) { Text("Reset") } } }
        item { Text("Position X ${selected.x}") }
        item { Slider(value = selected.x, onValueChange = { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(x = v) else it })) }, valueRange = -1f..1f) }
        item { Text("Position Y ${selected.y}") }
        item { Slider(value = selected.y, onValueChange = { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(y = v) else it })) }, valueRange = -1f..1f) }
        item { Text("Volume ${selected.volume}") }
        item { Slider(value = selected.volume, onValueChange = { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(volume = v) else it })) }, valueRange = 0f..2f) }
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
        item { Slider(value = selected.audio.fadeInMs.toFloat(), onValueChange = { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(audio = it.audio.copy(fadeInMs = v.toLong())) else it })) }, valueRange = 0f..10_000f) }
        item { Text("Fade out: ${selected.audio.fadeOutMs / 1000f}s") }
        item { Slider(value = selected.audio.fadeOutMs.toFloat(), onValueChange = { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(audio = it.audio.copy(fadeOutMs = v.toLong())) else it })) }, valueRange = 0f..10_000f) }
        item { Text("Non-destructive: source media is never modified.", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun ColorGradePanel(selected: BqClip?, project: BqProject, commit: (BqProject) -> Unit) {
    if (selected == null) { Text("Select a clip first", Modifier.padding(16.dp)); return }
    val g = selected.colorGrade
    val rows = listOf("Exposure" to g.exposure, "Contrast" to g.contrast, "Highlights" to g.highlights, "Shadows" to g.shadows, "Whites" to g.whites, "Blacks" to g.blacks, "Saturation" to g.saturation, "Temperature" to g.temperature, "Tint" to g.tint)
    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Color Grade", style = MaterialTheme.typography.titleMedium) }
        items(rows) { entry ->
            Text("${entry.first}: %.2f".format(entry.second), style = MaterialTheme.typography.labelSmall)
            val range = if (entry.first == "Saturation" || entry.first == "Temperature" || entry.first == "Tint") -100f..100f else -1f..1f
            Slider(value = entry.second.coerceIn(range.start, range.endInclusive), onValueChange = { v ->
                val next = when (entry.first) {
                    "Exposure" -> g.copy(exposure = v)
                    "Contrast" -> g.copy(contrast = v)
                    "Highlights" -> g.copy(highlights = v)
                    "Shadows" -> g.copy(shadows = v)
                    "Whites" -> g.copy(whites = v)
                    "Blacks" -> g.copy(blacks = v)
                    "Saturation" -> g.copy(saturation = v)
                    "Temperature" -> g.copy(temperature = v)
                    else -> g.copy(tint = v)
                }
                commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(colorGrade = next) else it }))
            }, valueRange = range)
        }
        item { OutlinedButton(onClick = { commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(colorGrade = BqColorGrade()) else it })) }) { Text("Reset Grade") } }
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
        item { Text("Lock effects to a selected person and body area so the target remains explicit in project state.", style = MaterialTheme.typography.labelSmall) }
        items(BqSuperpowerFxCatalog.effects) { effect ->
            OutlinedButton(onClick = {
                val fx = com.bqsqwad.proedit.editor.BqFxLayer(UUID.randomUUID().toString(), effect.kind)
                commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(fxLayers = it.fxLayers + fx) else it }))
            }, Modifier.fillMaxWidth()) { Text(effect.name) }
        }
        selected.fxLayers.forEach { fx ->
            item(key = fx.id) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(fx.kind.name.replace('_',' '))
                        Text("Target person: " + (fx.tracking.selectedPersonId?.toString() ?: "not selected"), style = MaterialTheme.typography.labelSmall)
                        Text("Target part: " + (fx.tracking.target?.part?.name ?: "WHOLE_PERSON"), style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            BqSubjectPart.values().forEach { part ->
                                OutlinedButton(onClick = {
                                    val person = fx.tracking.selectedPersonId ?: 0
                                    val binding = fx.tracking.copy(
                                        selectedPersonId = person,
                                        target = BqSubjectTarget(person, part, fx.tracking.confidenceThreshold)
                                    )
                                    val updated = fx.copy(tracking = binding)
                                    commit(project.copy(clips = project.clips.map { clip ->
                                        if (clip.id == selected.id) clip.copy(fxLayers = clip.fxLayers.map { if (it.id == fx.id) updated else it }) else clip
                                    }))
                                }) { Text(part.name.replace('_',' '), style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }
        item { Text("Configured layers: ${selected.fxLayers.size}", style = MaterialTheme.typography.labelSmall) }
    }
}
@Composable
private fun KeyframesPanel(selected: BqClip?, project: BqProject, commit: (BqProject) -> Unit) {
    if (selected == null) { Text("Select a clip first", Modifier.padding(16.dp)); return }
    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        item { Text("Effect keyframes", style = MaterialTheme.typography.titleMedium) }
        item { Text("Set an effect amount at the current playhead, then move the playhead and set another value.", style = MaterialTheme.typography.labelSmall) }
        items(selected.effects, key = { it.id }) { effect ->
            val parameter = effect.parameters["amount"]
            if (parameter != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text(effect.name)
                        Text("${parameter.keyframes.size} keyframe(s)", style = MaterialTheme.typography.labelSmall)
                        Button(onClick = {
                            val key = com.bqsqwad.proedit.editor.BqKeyframe(project.playheadMs, parameter.value)
                            val keys = (parameter.keyframes.filterNot { it.timeMs == key.timeMs } + key).sortedBy { it.timeMs }
                            val params = effect.parameters.toMutableMap()
                            params["amount"] = com.bqsqwad.proedit.editor.BqFloatParameter(parameter.value, keys)
                            val updated = effect.copy(parameters = params)
                            commit(project.copy(clips = project.clips.map { c ->
                                if (c.id == selected.id) c.copy(effects = c.effects.map { if (it.id == effect.id) updated else it }) else c
                            }))
                        }) { Text("Add keyframe at ${project.playheadMs} ms") }
                        if (parameter.keyframes.isNotEmpty()) {
                            parameter.keyframes.forEach { key ->
                                TextButton(onClick = {
                                    val params = effect.parameters.toMutableMap()
                                    params["amount"] = com.bqsqwad.proedit.editor.BqFloatParameter(
                                        parameter.value, parameter.keyframes.filterNot { it.timeMs == key.timeMs }
                                    )
                                    val updated = effect.copy(parameters = params)
                                    commit(project.copy(clips = project.clips.map { c ->
                                        if (c.id == selected.id) c.copy(effects = c.effects.map { if (it.id == effect.id) updated else it }) else c
                                    }))
                                }) { Text("Remove ${key.timeMs} ms keyframe") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChromaPanel(selected: BqClip?, project: BqProject, commit: (BqProject) -> Unit) {
    if (selected == null) { Text("Select a clip first", Modifier.padding(16.dp)); return }
    val chroma = selected.chromaKey
    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        item { Text("Chroma key / green screen", style = MaterialTheme.typography.titleMedium) }
        item {
            OutlinedButton(onClick = {
                commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(chromaKey = chroma.copy(enabled = !chroma.enabled)) else it }))
            }) { Text(if (chroma.enabled) "Disable chroma key" else "Enable chroma key") }
        }
        item { Text("Similarity %.2f".format(chroma.similarity)) }
        item { Slider(value = chroma.similarity, onValueChange = { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(chromaKey = chroma.copy(similarity = v)) else it })) }, valueRange = 0f..1f) }
        item { Text("Softness %.2f".format(chroma.softness)) }
        item { Slider(value = chroma.softness, onValueChange = { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(chromaKey = chroma.copy(softness = v)) else it })) }, valueRange = 0f..1f) }
        item { Text("Spill suppression %.2f".format(chroma.spillSuppression)) }
        item { Slider(value = chroma.spillSuppression, onValueChange = { v -> commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(chromaKey = chroma.copy(spillSuppression = v)) else it })) }, valueRange = 0f..1f) }
        item { Text("Key color: #%08X".format(chroma.colorArgb)) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Masks")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = {
                            val mask = com.bqsqwad.proedit.editor.BqMask(UUID.randomUUID().toString(), com.bqsqwad.proedit.editor.BqMaskShape.RECTANGLE, feather = 0.05f)
                            commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(masks = it.masks + mask) else it }))
                        }) { Text("Rectangle") }
                        OutlinedButton(onClick = {
                            val mask = com.bqsqwad.proedit.editor.BqMask(UUID.randomUUID().toString(), com.bqsqwad.proedit.editor.BqMaskShape.ELLIPSE, feather = 0.05f)
                            commit(project.copy(clips = project.clips.map { if (it.id == selected.id) it.copy(masks = it.masks + mask) else it }))
                        }) { Text("Ellipse") }
                    }
                    selected.masks.forEach { mask ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mask.shape.name, Modifier.weight(1f))
                            TextButton(onClick = {
                                commit(project.copy(clips = project.clips.map { c ->
                                    if (c.id == selected.id) c.copy(masks = c.masks.map { if (it.id == mask.id) it.copy(invert = !it.invert) else it }) else c
                                }))
                            }) { Text(if (mask.invert) "Normal" else "Invert") }
                            TextButton(onClick = {
                                commit(project.copy(clips = project.clips.map { c -> if (c.id == selected.id) c.copy(masks = c.masks.filterNot { it.id == mask.id }) else c }))
                            }) { Text("Remove") }
                        }
                    }
                    Text("Mask layers: " + selected.masks.size, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item { Text("Chroma settings are persisted with the clip and masks are non-destructive.", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun AdvancedPanel(project: BqProject, commit: (BqProject) -> Unit) {
    val a = project.advanced
    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Professional Advanced", style = MaterialTheme.typography.titleMedium) }
        item { Text("Advanced state is non-destructive and saved with the project.", style = MaterialTheme.typography.labelSmall) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Text("Proxy editing"); OutlinedButton(onClick = { commit(project.copy(advanced = a.copy(proxy = a.proxy.copy(enabled = !a.proxy.enabled)))) }) { Text(if (a.proxy.enabled) "Disable proxy" else "Enable proxy") } } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Text("Motion blur"); val selectedId = project.selectedClipId; val current = selectedId?.let { a.motionBlur[it] } ?: com.bqsqwad.proedit.editor.BqMotionBlur(); OutlinedButton(onClick = { if (selectedId != null) { val next = a.motionBlur.toMutableMap().apply { put(selectedId, current.copy(enabled = !current.enabled)) }; commit(project.copy(advanced = a.copy(motionBlur = next))) } }, enabled = selectedId != null) { Text(if (current.enabled) "Disable clip motion blur" else "Enable clip motion blur") }; Text("Samples: " + current.samples, style = MaterialTheme.typography.labelSmall); Slider(value = current.samples.toFloat(), onValueChange = { value -> if (selectedId != null) { val next = a.motionBlur.toMutableMap().apply { put(selectedId, current.copy(samples = value.toInt().coerceIn(1,64))) }; commit(project.copy(advanced = a.copy(motionBlur = next))) } }, valueRange = 1f..64f, steps = 62) } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Text("Beat markers"); Button(onClick = { val t = project.playheadMs; if (a.audioMarkers.none { it.timeMs == t }) commit(project.copy(advanced = a.copy(audioMarkers = (a.audioMarkers + com.bqsqwad.proedit.editor.BqAudioMarker(t)).sortedBy { it.timeMs }))) }) { Text("Add beat at playhead") }; Text(a.audioMarkers.size.toString() + " beat marker(s)", style = MaterialTheme.typography.labelSmall) } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Text("Export presets"); if (a.exportPresets.isEmpty()) { OutlinedButton(onClick = { val presets = listOf(com.bqsqwad.proedit.editor.BqExportPreset("fhd30","Full HD 30",1920,1080,30,12000000),com.bqsqwad.proedit.editor.BqExportPreset("hd30","HD 30",1280,720,30,8000000),com.bqsqwad.proedit.editor.BqExportPreset("fhd60","Full HD 60",1920,1080,60,20000000)); commit(project.copy(advanced = a.copy(exportPresets = presets))) }) { Text("Create standard presets") } } else { a.exportPresets.forEach { Text(it.name + ": " + it.width + "×" + it.height + " @ " + it.fps + "fps", style = MaterialTheme.typography.bodySmall) } } } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Text("Power FX library"); OutlinedButton(onClick = { val preset = com.bqsqwad.proedit.editor.BqPowerPreset("energy-aura","Energy Aura",com.bqsqwad.proedit.editor.BqSuperpowerFxKind.ELECTRIC_AURA,mapOf("intensity" to 1f,"radius" to 1f)); if (a.powerPresets.none { it.id == preset.id }) commit(project.copy(advanced = a.copy(powerPresets = a.powerPresets + preset))) }) { Text("Add Energy Aura preset") }; Text(a.powerPresets.size.toString() + " power preset(s)", style = MaterialTheme.typography.labelSmall) } } }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Transition builder")
                    val id = project.selectedClipId
                    val current = id?.let { a.transitions[it] } ?: BqTransitionSpec()
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        OutlinedButton(enabled = id != null, onClick = {
                            val clipId = id ?: return@OutlinedButton
                            commit(project.copy(advanced = a.copy(transitions = a.transitions + (clipId to current.copy(kind = BqTransitionKind.FADE, durationMs = 500L)))))
                        }) { Text("Fade") }
                        OutlinedButton(enabled = id != null, onClick = {
                            val clipId = id ?: return@OutlinedButton
                            commit(project.copy(advanced = a.copy(transitions = a.transitions + (clipId to current.copy(kind = BqTransitionKind.DIP_BLACK, durationMs = 500L)))))
                        }) { Text("Dip black") }
                        OutlinedButton(enabled = id != null, onClick = {
                            val clipId = id ?: return@OutlinedButton
                            commit(project.copy(advanced = a.copy(transitions = a.transitions + (clipId to current.copy(kind = BqTransitionKind.BLUR, durationMs = 500L)))))
                        }) { Text("Blur") }
                    }
                    if (id != null) {
                        Text("Duration: " + current.durationMs + " ms", style = MaterialTheme.typography.labelSmall)
                        Slider(value = current.durationMs.toFloat(), onValueChange = { v ->
                            commit(project.copy(advanced = a.copy(transitions = a.transitions + (id to current.copy(durationMs = v.toLong().coerceIn(0L, 5000L))))))
                        }, valueRange = 0f..5000f)
                    }
                    Text("Compositor: " + a.cornerPins.size + " pins · " + a.rotoscopes.size + " rotoscopes · " + a.blendLayers.size + " blend layers · " + a.particleEmitters.size + " particle emitters", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun CaptionsPanel(project: BqProject, commit: (BqProject) -> Unit, importSrt: () -> Unit, exportSrt: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        item { Text("Captions", style = MaterialTheme.typography.titleMedium) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(onClick = importSrt) { Text("Import SRT") }; OutlinedButton(onClick = exportSrt, enabled = project.captions.isNotEmpty()) { Text("Export SRT") } } }
        item {
            androidx.compose.material3.OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Caption text") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(onClick = {
                if (draft.isNotBlank()) {
                    val start = project.playheadMs
                    val end = (start + 3_000L).coerceAtLeast(start + 500L)
                    val caption = com.bqsqwad.proedit.editor.BqCaption(
                        UUID.randomUUID().toString(), draft.trim(), start, end
                    )
                    commit(project.copy(captions = project.captions + caption))
                    draft = ""
                }
            }, enabled = draft.isNotBlank()) { Text("Add caption at playhead") }
        }
        items(project.captions, key = { it.id }) { caption ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Text(caption.text, style = MaterialTheme.typography.bodyLarge)
                    Text("${caption.startMs}–${caption.endMs} ms", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = {
                        commit(project.copy(captions = project.captions.filterNot { it.id == caption.id }))
                    }) { Text("Remove") }
                }
            }
        }
        item { Text("Captions are stored with the project and remain editable.", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun MediaPanel(project: BqProject, onSelect: (BqClip) -> Unit, onImport: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Project Media Bin", style = MaterialTheme.typography.titleMedium)
                    Text("Import video, photo or audio. Media stays linked to the project.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onImport) { Text("Import media") }
                }
            }
        }
        items(project.clips, key = { it.id }) { clip -> Card(onClick = { onSelect(clip) }) { Column(Modifier.padding(9.dp).fillMaxWidth()) { Text(clip.name); Text(clip.mediaType.name, style = MaterialTheme.typography.labelSmall); Text(clip.uri.toString(), style = MaterialTheme.typography.labelSmall, maxLines = 2) } } }
    }
}

private fun saveFrameToGallery(context: android.content.Context, bitmap: android.graphics.Bitmap) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "BQ_Pro_Edit_Frame_" + System.currentTimeMillis() + ".jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BQ Pro Edit")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Unable to create gallery item")
    try {
        resolver.openOutputStream(uri)?.use { output -> if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, output)) error("Unable to encode frame") } ?: error("Unable to open gallery output")
        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); resolver.update(uri, values, null, null)
    } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
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


private enum class BqGuideFeature(
    val title: String,
    val color: Color,
    val how: String
) {
    IMPORT("Import media", Color(0xFF1565C0), "Tap Import in the Media area, choose video, photo or audio, then select the clip in the timeline."),
    TIMELINE("Timeline editing", Color(0xFF6A1B9A), "Select a clip, then use the editor controls to trim, split, duplicate, move and adjust timing. Undo and Redo restore earlier states."),
    TRANSFORM("Transform & framing", Color(0xFF00838F), "Use Inspector to change scale, X/Y position, rotation, opacity and clip framing without changing the source file."),
    SPEED("Speed & time", Color(0xFF2E7D32), "Use the Speed slider in Inspector. Keyframes can animate effect values over the playhead timeline."),
    COLOR("Color grading", Color(0xFFEF6C00), "Open Color and adjust exposure, contrast, highlights, shadows, whites, blacks, saturation, temperature and tint."),
    EFFECTS("Effects", Color(0xFFC62828), "Open Effects, add an effect, turn it on or off, change its amount and remove it. The stack is saved with the project."),
    CHROMA("Chroma key & masks", Color(0xFF00695C), "Open Chroma, enable green-screen removal, tune similarity/softness/spill and add non-destructive masks."),
    SUPERPOWER("Person-locked Superpower FX", Color(0xFFAD1457), "Open FX, add a power, choose the tracked person and body part, then use the timeline to control when the power is active."),
    AUDIO("Audio", Color(0xFF4527A0), "Use Inspector audio controls for volume, mute, solo and fades. Imported audio can be kept as project media."),
    CAPTIONS("Captions & SRT", Color(0xFF283593), "Open Captions, add text at the playhead, or import/export SRT. Captions remain editable in the project."),
    ADVANCED("Professional tools", Color(0xFF37474F), "Open Advanced for proxy settings, motion blur, beat markers, export presets, transitions and the advanced compositor state."),
    EXPORT("Export", Color(0xFF7B1FA2), "Choose FHD/HD/FHD60, validate the project, then Export MP4. Progress can be cancelled and completed video is saved to the gallery.")
}

@Composable
private fun BqFeatureGuide(
    onDismiss: () -> Unit,
    onShow: (BqGuideFeature) -> Unit,
    onCloseSpotlight: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("BQ Pro Edit — How to use", style = MaterialTheme.typography.headlineSmall)
                        Text("Every feature has a quick explanation. Tap Show Me to spotlight it.", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BqGuideFeature.entries) { feature ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(8.dp).height(54.dp).background(feature.color))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(feature.title, style = MaterialTheme.typography.titleSmall)
                                    Text(feature.how, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.width(6.dp))
                                OutlinedButton(onClick = { onShow(feature) }) { Text("Show Me") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BqFeatureSpotlight(feature: BqGuideFeature, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                Modifier.fillMaxWidth(0.92f),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth().height(8.dp).background(feature.color))
                    Text(feature.title, style = MaterialTheme.typography.headlineSmall)
                    Text(feature.how, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Spotlight mode: the rest of the screen is dimmed so this feature is the only bright instruction box.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Got it") }
                }
            }
        }
    }
}

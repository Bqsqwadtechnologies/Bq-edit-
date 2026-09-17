package com.bqsqwad.proedit.editor

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Versioned local project persistence with migration-safe defaults. */
class BqProjectStore(context: Context) {
    private val prefs = context.getSharedPreferences("bq_pro_edit_projects", Context.MODE_PRIVATE)
    private val key = "project.v3"

    fun save(project: BqProject) {
        val root = JSONObject().apply {
            put("schema", 3)
            put("id", project.id)
            put("name", project.name)
            put("version", project.version)
            put("selectedClipId", project.selectedClipId ?: JSONObject.NULL)
            put("playheadMs", project.playheadMs)
            put("aspectRatio", project.aspectRatio)
            put("fps", project.fps)
            put("backgroundArgb", project.backgroundArgb)
            put("lastSavedAt", System.currentTimeMillis())
            put("clips", JSONArray(project.clips.map(::clipJson)))
            put("assets", JSONArray(project.assets.map(::assetJson)))
            put("captions", JSONArray(project.captions.map(::captionJson)))
            put("markersMs", JSONArray(project.markersMs))
        }
        prefs.edit().putString(key, root.toString()).apply()
    }

    fun load(): BqProject? = runCatching {
        val raw = prefs.getString(key, null) ?: prefs.getString("project", null) ?: return null
        val root = JSONObject(raw)
        val clips = mutableListOf<BqClip>()
        val clipsJson = root.optJSONArray("clips") ?: JSONArray()
        for (i in 0 until clipsJson.length()) clips += clipFromJson(clipsJson.getJSONObject(i))
        val assets = mutableListOf<BqMediaAsset>()
        val assetsJson = root.optJSONArray("assets") ?: JSONArray()
        for (i in 0 until assetsJson.length()) assets += assetFromJson(assetsJson.getJSONObject(i))
        val captions = mutableListOf<BqCaption>()
        val captionsJson = root.optJSONArray("captions") ?: JSONArray()
        for (i in 0 until captionsJson.length()) captions += captionFromJson(captionsJson.getJSONObject(i))
        val markers = mutableListOf<Long>()
        val markersJson = root.optJSONArray("markersMs") ?: JSONArray()
        for (i in 0 until markersJson.length()) markers += markersJson.optLong(i)
        BqProject(
            id = root.optString("id", java.util.UUID.randomUUID().toString()),
            name = root.optString("name", "Untitled Project"),
            version = root.optInt("schema", 3),
            clips = clips,
            assets = assets,
            captions = captions,
            selectedClipId = if (root.isNull("selectedClipId")) null else root.optString("selectedClipId"),
            playheadMs = root.optLong("playheadMs", 0L),
            aspectRatio = root.optString("aspectRatio", "16:9"),
            fps = root.optInt("fps", 30),
            backgroundArgb = root.optLong("backgroundArgb", 0xFF000000),
            markersMs = markers,
            lastSavedAt = root.optLong("lastSavedAt", System.currentTimeMillis())
        )
    }.getOrNull()

    fun clear() = prefs.edit().remove(key).remove("project").apply()

    private fun assetJson(a: BqMediaAsset) = JSONObject().apply {
        put("id", a.id); put("uri", a.uri.toString()); put("name", a.name); put("type", a.type.name)
        put("durationMs", a.durationMs); put("width", a.width); put("height", a.height); put("rotation", a.rotation)
        put("sizeBytes", a.sizeBytes); put("missing", a.missing)
    }

    private fun assetFromJson(o: JSONObject) = BqMediaAsset(
        id = o.optString("id", java.util.UUID.randomUUID().toString()), uri = Uri.parse(o.optString("uri")),
        name = o.optString("name", "Media"), type = runCatching { BqMediaType.valueOf(o.optString("type")) }.getOrDefault(BqMediaType.VIDEO),
        durationMs = o.optLong("durationMs"), width = o.optInt("width"), height = o.optInt("height"), rotation = o.optInt("rotation"),
        sizeBytes = o.optLong("sizeBytes"), missing = o.optBoolean("missing")
    )

    private fun clipJson(c: BqClip) = JSONObject().apply {
        put("id", c.id); put("uri", c.uri.toString()); put("name", c.name); put("startMs", c.startMs); put("endMs", c.endMs ?: JSONObject.NULL)
        put("speed", c.speed); put("rotation", c.rotationDegrees); put("scale", c.scale); put("x", c.x); put("y", c.y); put("volume", c.volume)
        put("trackId", c.trackId); put("mediaType", c.mediaType.name)
        put("transform", JSONObject().apply { put("x", c.transform.x); put("y", c.transform.y); put("sx", c.transform.scaleX); put("sy", c.transform.scaleY); put("r", c.transform.rotation); put("opacity", c.transform.opacity) })
        put("audio", JSONObject().apply { put("volume", c.audio.volume); put("fadeIn", c.audio.fadeInMs); put("fadeOut", c.audio.fadeOutMs); put("muted", c.audio.muted); put("solo", c.audio.solo); put("ducking", c.audio.ducking) })
        put("chroma", JSONObject().apply { put("enabled", c.chromaKey.enabled); put("color", c.chromaKey.colorArgb); put("similarity", c.chromaKey.similarity); put("softness", c.chromaKey.softness); put("spill", c.chromaKey.spillSuppression) })
        put("effects", JSONArray(c.effects.map { e -> JSONObject().apply { put("id", e.id); put("name", e.name); put("kind", e.kind.name); put("enabled", e.enabled); put("parameters", parameterJson(e.parameters)) } }))
        put("fx", JSONArray(c.fxLayers.map { f -> JSONObject().apply { put("id", f.id); put("kind", f.kind.name); put("enabled", f.enabled); put("start", f.startMs); put("end", f.endMs ?: JSONObject.NULL); put("person", f.tracking.selectedPersonId ?: JSONObject.NULL); put("object", f.tracking.selectedObjectId ?: JSONObject.NULL); put("surface", f.tracking.selectedSurfaceId ?: JSONObject.NULL); put("confidence", f.tracking.confidenceThreshold); put("parameters", parameterJson(f.parameters)) } }))
    }

    private fun clipFromJson(o: JSONObject): BqClip {
        val t = o.optJSONObject("transform")
        val a = o.optJSONObject("audio")
        val ch = o.optJSONObject("chroma")
        return BqClip(
            id = o.optString("id", java.util.UUID.randomUUID().toString()), uri = Uri.parse(o.optString("uri")), name = o.optString("name", "Clip"),
            startMs = o.optLong("startMs"), endMs = if (o.isNull("endMs")) null else o.optLong("endMs"), speed = o.optDouble("speed", 1.0).toFloat(),
            rotationDegrees = o.optDouble("rotation", 0.0).toFloat(), scale = o.optDouble("scale", 1.0).toFloat(), x = o.optDouble("x").toFloat(), y = o.optDouble("y").toFloat(), volume = o.optDouble("volume", 1.0).toFloat(),
            trackId = o.optString("trackId", "video-1"), mediaType = runCatching { BqMediaType.valueOf(o.optString("mediaType")) }.getOrDefault(BqMediaType.VIDEO),
            transform = BqTransform(t?.optDouble("x", 0.0)?.toFloat() ?: 0f, t?.optDouble("y", 0.0)?.toFloat() ?: 0f, t?.optDouble("sx", 1.0)?.toFloat() ?: 1f, t?.optDouble("sy", 1.0)?.toFloat() ?: 1f, t?.optDouble("r", 0.0)?.toFloat() ?: 0f, t?.optDouble("opacity", 1.0)?.toFloat() ?: 1f),
            audio = BqAudioSettings(a?.optDouble("volume", 1.0)?.toFloat() ?: 1f, a?.optLong("fadeIn") ?: 0L, a?.optLong("fadeOut") ?: 0L, a?.optBoolean("muted") ?: false, a?.optBoolean("solo") ?: false, a?.optDouble("ducking")?.toFloat() ?: 0f),
            chromaKey = BqChromaKey(ch?.optBoolean("enabled") ?: false, ch?.optLong("color") ?: 0xFF00FF00, ch?.optDouble("similarity", .4)?.toFloat() ?: .4f, ch?.optDouble("softness", .1)?.toFloat() ?: .1f, ch?.optDouble("spill")?.toFloat() ?: 0f)
        )
    }

    private fun parameterJson(map: Map<String, BqFloatParameter>) = JSONArray(map.map { (key, p) -> JSONObject().apply { put("key", key); put("value", p.value); put("keys", JSONArray(p.keyframes.map { k -> JSONObject().apply { put("time", k.timeMs); put("value", k.value); put("interp", k.interpolation.name) } })) } })

    private fun captionJson(c: BqCaption) = JSONObject().apply { put("id", c.id); put("text", c.text); put("start", c.startMs); put("end", c.endMs); put("font", c.fontFamily); put("size", c.sizeSp); put("color", c.colorArgb); put("stroke", c.strokeArgb); put("strokeWidth", c.strokeWidth); put("x", c.x); put("y", c.y) }
    private fun captionFromJson(o: JSONObject) = BqCaption(o.optString("id"), o.optString("text"), o.optLong("start"), o.optLong("end"), o.optString("font", "sans-serif"), o.optDouble("size", 24.0).toFloat(), o.optLong("color", 0xFFFFFFFF), o.optLong("stroke", 0xFF000000), o.optDouble("strokeWidth", 2.0).toFloat(), o.optDouble("x", .5).toFloat(), o.optDouble("y", .85).toFloat())
}

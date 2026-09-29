package com.bqsqwad.proedit.editor

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Versioned local project persistence with migration-safe defaults. */
class BqProjectStore(context: Context) {
    private val prefs = context.getSharedPreferences("bq_pro_edit_projects", Context.MODE_PRIVATE)
    private val key = "project.v4"

    fun save(project: BqProject) {
        val root = JSONObject().apply {
            put("schema", 4)
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
            put("tracks", JSONArray(project.tracks.map(::trackJson)))
            put("walls", JSONArray(project.walls.map(::wallJson)))
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
        val tracks = mutableListOf<BqTimelineTrack>()
        val tracksJson = root.optJSONArray("tracks") ?: JSONArray()
        for (i in 0 until tracksJson.length()) tracks += trackFromJson(tracksJson.getJSONObject(i))
        val walls = mutableListOf<BqWallSurface>()
        val wallsJson = root.optJSONArray("walls") ?: JSONArray()
        for (i in 0 until wallsJson.length()) walls += wallFromJson(wallsJson.getJSONObject(i))
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

    private fun keyframesJson(keys: List<BqKeyframe<Float>>) = JSONArray(keys.map { k -> JSONObject().apply { put("time", k.timeMs); put("value", k.value); put("interp", k.interpolation.name) } })
    private fun floatKeys(o: JSONObject, key: String): List<BqKeyframe<Float>> {
        val array = o.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val k = array.optJSONObject(i) ?: continue
                add(BqKeyframe(k.optLong("time"), k.optDouble("value").toFloat(), runCatching { BqInterpolation.valueOf(k.optString("interp")) }.getOrDefault(BqInterpolation.LINEAR)))
            }
        }
    }
    private fun pointJson(p: BqPoint) = JSONObject().apply { put("x", p.x); put("y", p.y) }
    private fun pointFromJson(o: JSONObject) = BqPoint(o.optDouble("x").toFloat(), o.optDouble("y").toFloat())
    private fun trackJson(t: BqTimelineTrack) = JSONObject().apply {
        put("id", t.id); put("type", t.type.name); put("name", t.name); put("muted", t.muted); put("locked", t.locked); put("visible", t.visible)
        put("items", JSONArray(t.items.map { item -> JSONObject().apply {
            put("id", item.id); put("trackId", item.trackId); put("sourceUri", item.sourceUri?.toString() ?: JSONObject.NULL)
            put("start", item.startMs); put("duration", item.durationMs); put("effects", JSONArray(item.effectIds))
            put("transform", JSONObject().apply {
                put("px", keyframesJson(item.transform.positionX)); put("py", keyframesJson(item.transform.positionY))
                put("scale", keyframesJson(item.transform.scale)); put("rotation", keyframesJson(item.transform.rotation)); put("opacity", keyframesJson(item.transform.opacity))
            })
        } }))
    }
    private fun trackFromJson(o: JSONObject): BqTimelineTrack {
        val items = buildList {
            val a = o.optJSONArray("items") ?: JSONArray()
            for (i in 0 until a.length()) {
                val x = a.optJSONObject(i) ?: continue
                val t = x.optJSONObject("transform")
                add(BqTimelineItem(
                    x.optString("id"), x.optString("trackId"), if (x.isNull("sourceUri")) null else Uri.parse(x.optString("sourceUri")),
                    x.optLong("start"), x.optLong("duration"),
                    BqTransformKeyframes(floatKeys(t ?: JSONObject(), "px"), floatKeys(t ?: JSONObject(), "py"), floatKeys(t ?: JSONObject(), "scale"), floatKeys(t ?: JSONObject(), "rotation"), floatKeys(t ?: JSONObject(), "opacity")),
                    buildList { val e=x.optJSONArray("effects") ?: JSONArray(); for(j in 0 until e.length()) add(e.optString(j)) }
                ))
            }
        }
        return BqTimelineTrack(o.optString("id"), runCatching { BqTrackType.valueOf(o.optString("type")) }.getOrDefault(BqTrackType.VIDEO), o.optString("name"), o.optBoolean("muted"), o.optBoolean("locked"), o.optBoolean("visible", true), items)
    }
    private fun wallJson(w: BqWallSurface) = JSONObject().apply {
        put("id", w.id); put("assetUri", w.assetUri?.toString() ?: JSONObject.NULL); put("feather", w.feather); put("opacity", w.opacity); put("blendMode", w.blendMode.name)
        put("corners", JSONArray(w.corners.map(::pointJson)))
        put("tracking", JSONArray(w.tracking.map { t -> JSONObject().apply { put("time", t.frameTimeUs); put("confidence", t.confidence); put("corners", JSONArray(t.corners.map(::pointJson))) } }))
    }
    private fun wallFromJson(o: JSONObject) = BqWallSurface(
        o.optString("id"), if (o.isNull("assetUri")) null else Uri.parse(o.optString("assetUri")),
        buildList { val a=o.optJSONArray("corners") ?: JSONArray(); for(i in 0 until a.length()) a.optJSONObject(i)?.let { add(pointFromJson(it)) } },
        buildList { val a=o.optJSONArray("tracking") ?: JSONArray(); for(i in 0 until a.length()) { val t=a.optJSONObject(i) ?: continue; val p=t.optJSONArray("corners") ?: JSONArray(); val corners=buildList { for(j in 0 until p.length()) p.optJSONObject(j)?.let { add(pointFromJson(it)) } }; if(corners.size==4) add(BqSurfaceTrack(t.optLong("time"), corners, t.optDouble("confidence").toFloat())) } },
        o.optDouble("feather").toFloat(), o.optDouble("opacity",1.0).toFloat(), runCatching { BqBlendMode.valueOf(o.optString("blendMode")) }.getOrDefault(BqBlendMode.NORMAL)
    )

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
        put("transform", JSONObject().apply { put("x", c.transform.x); put("y", c.transform.y); put("sx", c.transform.scaleX); put("sy", c.transform.scaleY); put("r", c.transform.rotation); put("opacity", c.transform.opacity); put("cropL", c.transform.cropLeft); put("cropT", c.transform.cropTop); put("cropR", c.transform.cropRight); put("cropB", c.transform.cropBottom); put("keyframes", JSONObject().apply { put("px", keyframesJson(c.transform.keyframes.positionX)); put("py", keyframesJson(c.transform.keyframes.positionY)); put("scale", keyframesJson(c.transform.keyframes.scale)); put("rotation", keyframesJson(c.transform.keyframes.rotation)); put("opacity", keyframesJson(c.transform.keyframes.opacity)) }) })
        put("colorGrade", JSONObject().apply { put("exposure", c.colorGrade.exposure); put("contrast", c.colorGrade.contrast); put("highlights", c.colorGrade.highlights); put("shadows", c.colorGrade.shadows); put("whites", c.colorGrade.whites); put("blacks", c.colorGrade.blacks); put("saturation", c.colorGrade.saturation); put("temperature", c.colorGrade.temperature); put("tint", c.colorGrade.tint); put("lutUri", c.colorGrade.lutUri?.toString() ?: JSONObject.NULL) })
        put("masks", JSONArray(c.masks.map { m -> JSONObject().apply { put("id", m.id); put("shape", m.shape.name); put("feather", m.feather); put("invert", m.invert); put("opacity", m.opacity); put("blend", m.blendMode.name); put("points", JSONArray(m.points.map(::pointJson))); put("keyframes", JSONArray(m.keyframes.map { k -> JSONObject().apply { put("time", k.timeMs); put("interp", k.interpolation.name); put("points", JSONArray(k.value.map(::pointJson)) ) } })) } }))
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
            transform = BqTransform(t?.optDouble("x", 0.0)?.toFloat() ?: 0f, t?.optDouble("y", 0.0)?.toFloat() ?: 0f, t?.optDouble("sx", 1.0)?.toFloat() ?: 1f, t?.optDouble("sy", 1.0)?.toFloat() ?: 1f, t?.optDouble("r", 0.0)?.toFloat() ?: 0f, t?.optDouble("opacity", 1.0)?.toFloat() ?: 1f, t?.optDouble("cropL", 0.0)?.toFloat() ?: 0f, t?.optDouble("cropT", 0.0)?.toFloat() ?: 0f, t?.optDouble("cropR", 0.0)?.toFloat() ?: 0f, t?.optDouble("cropB", 0.0)?.toFloat() ?: 0f, BqTransformKeyframes(floatKeys(t?.optJSONObject("keyframes") ?: JSONObject(), "px"), floatKeys(t?.optJSONObject("keyframes") ?: JSONObject(), "py"), floatKeys(t?.optJSONObject("keyframes") ?: JSONObject(), "scale"), floatKeys(t?.optJSONObject("keyframes") ?: JSONObject(), "rotation"), floatKeys(t?.optJSONObject("keyframes") ?: JSONObject(), "opacity"))),
            audio = BqAudioSettings(a?.optDouble("volume", 1.0)?.toFloat() ?: 1f, a?.optLong("fadeIn") ?: 0L, a?.optLong("fadeOut") ?: 0L, a?.optBoolean("muted") ?: false, a?.optBoolean("solo") ?: false, a?.optDouble("ducking")?.toFloat() ?: 0f),
            chromaKey = BqChromaKey(ch?.optBoolean("enabled") ?: false, ch?.optLong("color") ?: 0xFF00FF00, ch?.optDouble("similarity", .4)?.toFloat() ?: .4f, ch?.optDouble("softness", .1)?.toFloat() ?: .1f, ch?.optDouble("spill")?.toFloat() ?: 0f),
            effects = o.optJSONArray("effects").toEffectList(),
            fxLayers = o.optJSONArray("fx").toFxList()
        )
    }

    private fun effectFromJson(o: JSONObject) = BqEffectLayer(
        id = o.optString("id", java.util.UUID.randomUUID().toString()),
        name = o.optString("name", "Effect"),
        kind = runCatching { BqEffectKind.valueOf(o.optString("kind")) }.getOrDefault(BqEffectKind.BRIGHTNESS),
        enabled = o.optBoolean("enabled", true),
        parameters = parameterFromJson(o.optJSONArray("parameters"))
    )

    private fun fxFromJson(o: JSONObject) = BqFxLayer(
        id = o.optString("id", java.util.UUID.randomUUID().toString()),
        kind = runCatching { BqSuperpowerFxKind.valueOf(o.optString("kind")) }.getOrDefault(BqSuperpowerFxKind.GLOW_AURA),
        enabled = o.optBoolean("enabled", true),
        startMs = o.optLong("start"),
        endMs = if (o.isNull("end")) null else o.optLong("end"),
        tracking = BqTrackingBinding(
            selectedPersonId = if (o.isNull("person")) null else o.optInt("person"),
            selectedObjectId = if (o.isNull("object")) null else o.optInt("object"),
            selectedSurfaceId = if (o.isNull("surface")) null else o.optInt("surface"),
            confidenceThreshold = o.optDouble("confidence", .35).toFloat()
        ),
        parameters = parameterFromJson(o.optJSONArray("parameters"))
    )

    private fun parameterFromJson(array: JSONArray?): Map<String, BqFloatParameter> {
        if (array == null) return emptyMap()
        val result = linkedMapOf<String, BqFloatParameter>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val keys = mutableListOf<BqKeyframe<Float>>()
            val keyArray = o.optJSONArray("keys")
            if (keyArray != null) for (j in 0 until keyArray.length()) {
                val k = keyArray.optJSONObject(j) ?: continue
                keys += BqKeyframe(
                    k.optLong("time"),
                    k.optDouble("value").toFloat(),
                    runCatching { BqInterpolation.valueOf(k.optString("interp")) }.getOrDefault(BqInterpolation.LINEAR)
                )
            }
            result[o.optString("key")] = BqFloatParameter(o.optDouble("value").toFloat(), keys)
        }
        return result
    }

    private fun parameterJson(map: Map<String, BqFloatParameter>) = JSONArray(map.map { (key, p) -> JSONObject().apply { put("key", key); put("value", p.value); put("keys", JSONArray(p.keyframes.map { k -> JSONObject().apply { put("time", k.timeMs); put("value", k.value); put("interp", k.interpolation.name) } })) } })

    private fun captionJson(c: BqCaption) = JSONObject().apply { put("id", c.id); put("text", c.text); put("start", c.startMs); put("end", c.endMs); put("font", c.fontFamily); put("size", c.sizeSp); put("color", c.colorArgb); put("stroke", c.strokeArgb); put("strokeWidth", c.strokeWidth); put("x", c.x); put("y", c.y) }
    private fun captionFromJson(o: JSONObject) = BqCaption(o.optString("id"), o.optString("text"), o.optLong("start"), o.optLong("end"), o.optString("font", "sans-serif"), o.optDouble("size", 24.0).toFloat(), o.optLong("color", 0xFFFFFFFF), o.optLong("stroke", 0xFF000000), o.optDouble("strokeWidth", 2.0).toFloat(), o.optDouble("x", .5).toFloat(), o.optDouble("y", .85).toFloat())
}

private fun JSONArray?.toEffectList(): List<BqEffectLayer> {
    if (this == null) return emptyList()
    return buildList { for (i in 0 until length()) optJSONObject(i)?.let { add(effectFromJsonForStore(it)) } }
}

private fun JSONArray?.toFxList(): List<BqFxLayer> {
    if (this == null) return emptyList()
    return buildList { for (i in 0 until length()) optJSONObject(i)?.let { add(fxFromJsonForStore(it)) } }
}

private fun effectFromJsonForStore(o: JSONObject) = BqEffectLayer(
    id = o.optString("id", java.util.UUID.randomUUID().toString()),
    name = o.optString("name", "Effect"),
    kind = runCatching { BqEffectKind.valueOf(o.optString("kind")) }.getOrDefault(BqEffectKind.BRIGHTNESS),
    enabled = o.optBoolean("enabled", true),
    parameters = parameterFromJsonForStore(o.optJSONArray("parameters"))
)

private fun fxFromJsonForStore(o: JSONObject) = BqFxLayer(
    id = o.optString("id", java.util.UUID.randomUUID().toString()),
    kind = runCatching { BqSuperpowerFxKind.valueOf(o.optString("kind")) }.getOrDefault(BqSuperpowerFxKind.GLOW_AURA),
    enabled = o.optBoolean("enabled", true),
    startMs = o.optLong("start"),
    endMs = if (o.isNull("end")) null else o.optLong("end"),
    tracking = BqTrackingBinding(
        selectedPersonId = if (o.isNull("person")) null else o.optInt("person"),
        selectedObjectId = if (o.isNull("object")) null else o.optInt("object"),
        selectedSurfaceId = if (o.isNull("surface")) null else o.optInt("surface"),
        confidenceThreshold = o.optDouble("confidence", .35).toFloat()
    ),
    parameters = parameterFromJsonForStore(o.optJSONArray("parameters"))
)

private fun parameterFromJsonForStore(array: JSONArray?): Map<String, BqFloatParameter> {
    if (array == null) return emptyMap()
    val result = linkedMapOf<String, BqFloatParameter>()
    for (i in 0 until array.length()) {
        val o = array.optJSONObject(i) ?: continue
        val keys = mutableListOf<BqKeyframe<Float>>()
        val keyArray = o.optJSONArray("keys")
        if (keyArray != null) for (j in 0 until keyArray.length()) {
            val k = keyArray.optJSONObject(j) ?: continue
            keys += BqKeyframe(
                k.optLong("time"),
                k.optDouble("value").toFloat(),
                runCatching { BqInterpolation.valueOf(k.optString("interp")) }.getOrDefault(BqInterpolation.LINEAR)
            )
        }
        result[o.optString("key")] = BqFloatParameter(o.optDouble("value").toFloat(), keys)
    }
    return result
}
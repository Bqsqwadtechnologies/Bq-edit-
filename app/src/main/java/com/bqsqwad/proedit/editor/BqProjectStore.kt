package com.bqsqwad.proedit.editor

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight local project persistence. Media stays in its original location. */
class BqProjectStore(context: Context) {
    private val prefs = context.getSharedPreferences("bq_pro_edit_projects", Context.MODE_PRIVATE)

    fun save(project: BqProject) {
        val clips = JSONArray()
        project.clips.forEach { clip ->
            clips.put(JSONObject().apply {
                put("id", clip.id)
                put("uri", clip.uri.toString())
                put("name", clip.name)
                put("startMs", clip.startMs)
                put("endMs", clip.endMs ?: JSONObject.NULL)
                put("speed", clip.speed.toDouble())
                put("rotation", clip.rotationDegrees.toDouble())
                put("scale", clip.scale.toDouble())
                put("x", clip.x.toDouble())
                put("y", clip.y.toDouble())
                put("volume", clip.volume.toDouble())
            })
        }
        prefs.edit().putString("project", JSONObject().apply {
            put("name", project.name)
            put("selectedClipId", project.selectedClipId ?: JSONObject.NULL)
            put("playheadMs", project.playheadMs)
            put("clips", clips)
        }.toString()).apply()
    }

    fun load(): BqProject? {
        val raw = prefs.getString("project", null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val array = root.getJSONArray("clips")
            val clips = buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(BqClip(
                        id = o.getString("id"),
                        uri = Uri.parse(o.getString("uri")),
                        name = o.getString("name"),
                        startMs = o.optLong("startMs", 0L),
                        endMs = if (o.isNull("endMs")) null else o.optLong("endMs"),
                        speed = o.optDouble("speed", 1.0).toFloat(),
                        rotationDegrees = o.optDouble("rotation", 0.0).toFloat(),
                        scale = o.optDouble("scale", 1.0).toFloat(),
                        x = o.optDouble("x", 0.0).toFloat(),
                        y = o.optDouble("y", 0.0).toFloat(),
                        volume = o.optDouble("volume", 1.0).toFloat()
                    ))
                }
            }
            BqProject(
                name = root.optString("name", "Untitled Project"),
                clips = clips,
                selectedClipId = if (root.isNull("selectedClipId")) null else root.optString("selectedClipId"),
                playheadMs = root.optLong("playheadMs", 0L)
            )
        }.getOrNull()
    }
}

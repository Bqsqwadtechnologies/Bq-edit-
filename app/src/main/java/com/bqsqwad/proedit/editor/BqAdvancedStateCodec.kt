package com.bqsqwad.proedit.editor

import org.json.JSONArray
import org.json.JSONObject

/** Version-tolerant persistence for advanced editor state. */
object BqAdvancedStateCodec {
    fun toJson(s: BqAdvancedEditorState) = JSONObject().apply {
        put("transitions", JSONObject(s.transitions.mapValues { (_,v)-> JSONObject().apply { put("kind",v.kind.name);put("duration",v.durationMs);put("easing",v.easing.name);put("amount",v.amount) } }))
        put("blend", JSONArray(s.blendLayers.map { JSONObject().apply { put("id",it.id);put("source",it.sourceClipId);put("mode",it.mode.name);put("opacity",it.opacity);put("enabled",it.enabled) } }))
        put("pins", JSONObject(s.cornerPins.mapValues { (_,v)->JSONObject().apply{put("enabled",v.enabled);put("tl",p(v.topLeft));put("tr",p(v.topRight));put("br",p(v.bottomRight));put("bl",p(v.bottomLeft))} }))
        put("roto", JSONObject(s.rotoscopes.mapValues { (_,v)->JSONObject().apply{put("enabled",v.enabled);put("points",JSONArray(v.points.map(::p)));put("feather",v.feather);put("expansion",v.expansion);put("invert",v.invert)} }))
        put("blur", JSONObject(s.motionBlur.mapValues { (_,v)->JSONObject().apply{put("enabled",v.enabled);put("shutter",v.shutterAngle);put("samples",v.samples)} }))
        put("particles",JSONArray(s.particleEmitters.map{JSONObject().apply{put("id",it.id);put("enabled",it.enabled);put("rate",it.rate);put("lifetime",it.lifetimeMs);put("size",it.size);put("speed",it.speed);put("turbulence",it.turbulence);put("gravity",it.gravity)}}))
        put("presets",JSONArray(s.powerPresets.map{JSONObject().apply{put("id",it.id);put("name",it.name);put("kind",it.kind.name);put("parameters",JSONObject(it.parameters))}}))
        put("compositions",JSONArray(s.compositions.map{JSONObject().apply{put("id",it.id);put("name",it.name);put("layers",JSONArray(it.layers));put("enabled",it.enabled)}}))
        put("proxy",JSONObject().apply{put("enabled",s.proxy.enabled);put("width",s.proxy.width);put("height",s.proxy.height);put("mime",s.proxy.videoMime);put("bitrate",s.proxy.bitrate)})
        put("exports",JSONArray(s.exportPresets.map{JSONObject().apply{put("id",it.id);put("name",it.name);put("width",it.width);put("height",it.height);put("fps",it.fps);put("bitrate",it.bitrate)}}))
        put("beats",JSONArray(s.audioMarkers.map{JSONObject().apply{put("time",it.timeMs);put("label",it.label)}}))
        put("versions",JSONArray(s.versions.map{JSONObject().apply{put("id",it.id);put("created",it.createdAt);put("label",it.label);put("project",it.projectId)}}))
    }
    fun fromJson(o:JSONObject?):BqAdvancedEditorState{
        if(o==null)return BqAdvancedEditorState()
        val tr=linkedMapOf<String,BqTransitionSpec>();o.optJSONObject("transitions")?.let{x->x.keys().forEach{k->x.optJSONObject(k)?.let{v->tr[k]=BqTransitionSpec(k,ev(v.optString("kind"),BqTransitionKind.CUT),v.optLong("duration"),ee(v.optString("easing"),BqCurveEasing.LINEAR),v.optDouble("amount",1.0).toFloat())}}}
        val bl=buildList{val a=o.optJSONArray("blend")?:JSONArray();for(i in 0 until a.length()){val v=a.optJSONObject(i)?:continue;add(BqBlendLayer(v.optString("id"),v.optString("source"),ev(v.optString("mode"),BqBlendMode.NORMAL),v.optDouble("opacity",1.0).toFloat(),v.optBoolean("enabled",true)))}}
        val pins=linkedMapOf<String,BqCornerPin>();o.optJSONObject("pins")?.let{x->x.keys().forEach{k->x.optJSONObject(k)?.let{v->pins[k]=BqCornerPin(v.optBoolean("enabled"),rp(v.optJSONObject("tl")),rp(v.optJSONObject("tr"),BqPoint(1f,0f)),rp(v.optJSONObject("br"),BqPoint(1f,1f)),rp(v.optJSONObject("bl"),BqPoint(0f,1f)))}}}
        val roto=linkedMapOf<String,BqRotoscopeRefinement>();o.optJSONObject("roto")?.let{x->x.keys().forEach{k->x.optJSONObject(k)?.let{v->roto[k]=BqRotoscopeRefinement(v.optBoolean("enabled"),rps(v.optJSONArray("points")),v.optDouble("feather").toFloat(),v.optDouble("expansion").toFloat(),v.optBoolean("invert"))}}}
        val blur=linkedMapOf<String,BqMotionBlur>();o.optJSONObject("blur")?.let{x->x.keys().forEach{k->x.optJSONObject(k)?.let{v->blur[k]=BqMotionBlur(v.optBoolean("enabled"),v.optDouble("shutter",180.0).toFloat(),v.optInt("samples",8))}}}
        val parts=buildList{val a=o.optJSONArray("particles")?:JSONArray();for(i in 0 until a.length()){val v=a.optJSONObject(i)?:continue;add(BqParticleEmitter(v.optString("id"),v.optBoolean("enabled",true),v.optDouble("rate",40.0).toFloat(),v.optLong("lifetime",800),v.optDouble("size",1.0).toFloat(),v.optDouble("speed",1.0).toFloat(),v.optDouble("turbulence").toFloat(),v.optDouble("gravity").toFloat()))}}
        val presets=buildList{val a=o.optJSONArray("presets")?:JSONArray();for(i in 0 until a.length()){val v=a.optJSONObject(i)?:continue;val p=linkedMapOf<String,Float>();v.optJSONObject("parameters")?.let{q->q.keys().forEach{k->p[k]=q.optDouble(k).toFloat()}};add(BqPowerPreset(v.optString("id"),v.optString("name"),ev(v.optString("kind"),BqSuperpowerFxKind.GLOW_AURA),p))}}
        val comps=buildList{val a=o.optJSONArray("compositions")?:JSONArray();for(i in 0 until a.length()){val v=a.optJSONObject(i)?:continue;add(BqFxComposition(v.optString("id"),v.optString("name"),rs(v.optJSONArray("layers")),v.optBoolean("enabled",true)))}}
        val px=o.optJSONObject("proxy")?.let{BqProxySettings(it.optBoolean("enabled"),it.optInt("width",960),it.optInt("height",540),it.optString("mime","video/avc"),it.optInt("bitrate",2500000))}?:BqProxySettings()
        val ex=buildList{val a=o.optJSONArray("exports")?:JSONArray();for(i in 0 until a.length()){val v=a.optJSONObject(i)?:continue;add(BqExportPreset(v.optString("id"),v.optString("name"),v.optInt("width"),v.optInt("height"),v.optInt("fps",30),v.optInt("bitrate")))}}
        val beats=buildList{val a=o.optJSONArray("beats")?:JSONArray();for(i in 0 until a.length()){val v=a.optJSONObject(i)?:continue;add(BqAudioMarker(v.optLong("time"),v.optString("label","Beat")))}}
        val vers=buildList{val a=o.optJSONArray("versions")?:JSONArray();for(i in 0 until a.length()){val v=a.optJSONObject(i)?:continue;add(BqProjectVersion(v.optString("id"),v.optLong("created"),v.optString("label","Version"),v.optString("project")))}}
        return BqAdvancedEditorState(tr,bl,pins,roto,blur,parts,presets,comps,px,ex,beats,vers)
    }
    private fun p(v:BqPoint)=JSONObject().put("x",v.x).put("y",v.y)
    private fun rp(o:JSONObject?,d:BqPoint=BqPoint(0f,0f))=o?.let{BqPoint(it.optDouble("x",d.x.toDouble()).toFloat(),it.optDouble("y",d.y.toDouble()).toFloat())}?:d
    private fun rps(a:JSONArray?)=buildList{if(a!=null)for(i in 0 until a.length())add(rp(a.optJSONObject(i)))}
    private fun rs(a:JSONArray?)=buildList{if(a!=null)for(i in 0 until a.length())add(a.optString(i))}
    private inline fun <reified T:Enum<T>> ev(v:String,d:T)=runCatching{enumValueOf<T>(v)}.getOrDefault(d)
    private fun ee(v:String,d:BqCurveEasing)=runCatching{BqCurveEasing.valueOf(v)}.getOrDefault(d)
}
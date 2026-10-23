package com.zoomzhao.racingclub

import org.json.JSONArray
import org.json.JSONObject

// Platform-free parsing: JVM tests replay the shipped game-data.json with real org.json.
private fun JSONArray.points()=(0 until length()).map { getJSONArray(it).let { p->Point(p.getDouble(0).toFloat(),p.getDouble(1).toFloat()) } }
private fun placement(o:JSONObject)=Placement(o.getInt("id"),o.getInt("x"),o.getInt("y"),o.getInt("r"))
private fun placements(a:JSONArray)=(0 until a.length()).map { placement(a.getJSONObject(it)) }
private fun encode(p:Placement)=JSONObject().put("id",p.id).put("x",p.x).put("y",p.y).put("r",p.r)
internal fun parseRules(json:String):Rules {
 val o=JSONObject(json);val ps=o.getJSONArray("pieces");val ls=o.getJSONArray("levels");val gs=o.getJSONObject("gates");val inv=o.getJSONArray("inventory");val ds=o.getJSONArray("difficulties")
 fun string(o:JSONObject,k:String)=if(o.isNull(k))"" else o.optString(k,"")
 return Rules((0 until ps.length()).associate {
  val p=ps.getJSONObject(it);val id=p.getInt("id");val paths=p.getJSONArray("paths")
  id to Piece(id,p.getString("color"),p.getJSONArray("ports").points(),p.getJSONArray("samples").points(),(0..2).map{n->p.getJSONArray("gate").getDouble(n).toFloat()},(0 until paths.length()).map{n->
   val path=paths.getJSONObject(n);RoadPath(path.getInt("from"),path.getInt("to"),if(path.isNull("jump"))null else path.getInt("jump"),path.getJSONArray("samples").points(),if(path.isNull("jumpAt"))null else path.getDouble("jumpAt").toFloat(),path.getDouble("length").toFloat())
  })
 },gs.keys().asSequence().associateWith{k->gs.getJSONArray(k).let{Point(it.getDouble(0).toFloat(),it.getDouble(1).toFloat())}},(0 until ls.length()).map {
  val l=ls.getJSONObject(it);val seq=l.getJSONArray("sequence")
  Level(l.getInt("id"),l.getString("title"),string(l,"start"),string(l,"finish"),(0 until seq.length()).map{n->seq.getInt(n)},placements(l.getJSONArray("solution")),l.getString("group"),string(l,"solutionStart"),string(l,"solutionFinish"),l.getString("difficultyId"))
 },(0 until inv.length()).associate{inv.getJSONObject(it).let{p->p.getInt("id") to p.getString("color")}},(0 until ds.length()).map{n->ds.getJSONObject(n).let{d->Difficulty(d.getString("id"),d.getString("label"),d.getString("originalLabel"),d.getString("color"),d.getInt("firstLevel"),d.getInt("lastLevel"))}})
}

/** Same JSON shape as progress-v1 has always stored, so existing saves keep loading. */
internal fun encodeProgress(p:SavedProgress):String {
 val a=JSONArray();p.placed.forEach{a.put(encode(it))}
 return JSONObject().put("placed",a).put("moves",p.moves).put("hints",p.hints).put("elapsed",p.elapsed).put("start",p.start).put("finish",p.finish).put("finished",p.finished).toString()
}
internal fun decodeProgress(json:String):SavedProgress?=runCatching {
 val o=JSONObject(json)
 SavedProgress(placements(o.optJSONArray("placed")?:JSONArray()),o.optInt("moves"),o.optInt("hints"),o.optLong("elapsed"),o.optString("start"),o.optString("finish"),o.optBoolean("finished"))
}.getOrNull()

internal fun encodeScores(records:List<Score>):String {
 val a=JSONArray();records.forEach{a.put(JSONObject().put("level",it.level).put("millis",it.millis).put("moves",it.moves).put("hints",it.hints).put("timestamp",it.timestamp))}
 return a.toString()
}
internal fun decodeScores(json:String):List<Score> =runCatching {
 val a=JSONArray(json)
 (0 until a.length()).map{val o=a.getJSONObject(it);Score(o.getInt("level"),o.getLong("millis"),o.getInt("moves"),o.getInt("hints"),o.getLong("timestamp"))}
}.getOrDefault(emptyList())

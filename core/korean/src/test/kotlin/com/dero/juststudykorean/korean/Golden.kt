package com.dero.juststudykorean.korean

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Reads ../../golden/ (generated from the web by tools/golden). */
internal object Golden {
    fun obj(path: String) = JSONObject(File("../../golden/$path").readText())
    fun arr(path: String) = JSONArray(File("../../golden/$path").readText())
    fun files(dir: String): List<File> = File("../../golden/$dir").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
}

internal fun JSONArray.objects() = List(length()) { getJSONObject(it) }
internal fun JSONArray.strings() = List(length()) { getString(it) }

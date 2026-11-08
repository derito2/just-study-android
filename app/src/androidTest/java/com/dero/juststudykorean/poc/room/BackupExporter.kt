package com.dero.juststudykorean.poc.room

import androidx.room3.useReaderConnection
import java.io.File

object BackupExporter {
    /** Writes tables/<table>.jsonl in the backup's exact format into [out]. */
    suspend fun export(db: PocDatabase, out: File) {
        File(out, "tables").mkdirs()
        db.useReaderConnection { c ->
            for ((table, spec) in SCHEMA) {
                val cols = spec.columns
                val sorted = cols.withIndex().sortedBy { it.value.name } // Python sort_keys
                val sql = "SELECT ${cols.joinToString { RoomSql.quote(it.name) }} FROM ${RoomSql.quote(table)} " +
                    "ORDER BY ${spec.primaryKey.joinToString { RoomSql.quote(it) }}"
                File(out, "tables/$table.jsonl").bufferedWriter().use { w ->
                    c.usePrepared(sql) { st ->
                        while (st.step()) {
                            w.write(sorted.joinToString(",", "{", "}\n") { (i, col) ->
                                RoomSql.pyString(col.name) + ":" + if (st.isNull(i)) "null" else when (col.kind) {
                                    Kind.INT -> st.getLong(i).toString()
                                    Kind.REAL -> RoomSql.pyFloat(st.getDouble(i))
                                    Kind.TEXT -> RoomSql.pyString(st.getText(i))
                                    Kind.BOOL -> if (st.getLong(i) != 0L) "true" else "false"
                                    Kind.DATETIME -> "\"" + RoomSql.formatUtc(st.getLong(i)) + "\""
                                    Kind.JSON -> st.getText(i)
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

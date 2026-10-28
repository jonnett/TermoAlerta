package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Una lectura guardada en el historial. */
data class StoredReading(
    val id: Long,
    val timestamp: Long,
    val celsius: Float,
    val limit: Float,
    val isAlert: Boolean,
    val source: String,
)

data class ReadingStats(
    val count: Int,
    val min: Float?,
    val max: Float?,
    val average: Float?,
    val alerts: Int,
)

/**
 * Base de datos SQLite local con el historial de lecturas que recibe el monitor.
 * Todas las consultas usan parámetros (?) para evitar inyección SQL.
 */
class ReadingsDatabase(context: Context) :
    SQLiteOpenHelper(context, "termoalerta.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE lecturas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha INTEGER NOT NULL,
                celsius REAL NOT NULL,
                limite REAL NOT NULL,
                alerta INTEGER NOT NULL,
                origen TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_lecturas_fecha ON lecturas(fecha)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS lecturas")
        onCreate(db)
    }

    private var insertsSincePrune = 0

    @Synchronized
    fun insert(celsius: Float, limit: Float, isAlert: Boolean, source: String, timestamp: Long) {
        val values = ContentValues().apply {
            put("fecha", timestamp)
            put("celsius", celsius)
            put("limite", limit)
            put("alerta", if (isAlert) 1 else 0)
            put("origen", source)
        }
        writableDatabase.insert("lecturas", null, values)
        if (++insertsSincePrune >= 200) {
            insertsSincePrune = 0
            // Conserva solo las últimas MAX_ROWS lecturas
            writableDatabase.execSQL(
                "DELETE FROM lecturas WHERE id <= (SELECT MAX(id) FROM lecturas) - ?",
                arrayOf<Any>(MAX_ROWS)
            )
        }
    }

    @Synchronized
    fun recent(limit: Int = 300, onlyAlerts: Boolean = false): List<StoredReading> {
        val where = if (onlyAlerts) "alerta = ?" else null
        val args = if (onlyAlerts) arrayOf("1") else null
        readableDatabase.query(
            "lecturas", null, where, args, null, null, "id DESC", limit.toString()
        ).use { c ->
            val list = ArrayList<StoredReading>(c.count)
            while (c.moveToNext()) {
                list += StoredReading(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    timestamp = c.getLong(c.getColumnIndexOrThrow("fecha")),
                    celsius = c.getFloat(c.getColumnIndexOrThrow("celsius")),
                    limit = c.getFloat(c.getColumnIndexOrThrow("limite")),
                    isAlert = c.getInt(c.getColumnIndexOrThrow("alerta")) == 1,
                    source = c.getString(c.getColumnIndexOrThrow("origen")),
                )
            }
            return list
        }
    }

    @Synchronized
    fun stats(): ReadingStats {
        readableDatabase.rawQuery(
            "SELECT COUNT(*), MIN(celsius), MAX(celsius), AVG(celsius), SUM(alerta) FROM lecturas",
            null
        ).use { c ->
            if (!c.moveToFirst() || c.getInt(0) == 0) return ReadingStats(0, null, null, null, 0)
            return ReadingStats(
                count = c.getInt(0),
                min = c.getFloat(1),
                max = c.getFloat(2),
                average = c.getFloat(3),
                alerts = c.getInt(4),
            )
        }
    }

    @Synchronized
    fun clear() {
        writableDatabase.delete("lecturas", null, null)
    }

    private companion object {
        const val MAX_ROWS = 5000
    }
}

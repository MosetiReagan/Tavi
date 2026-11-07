package com.example.tavi.routines

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.tavi.data.TaviDatabase
import java.time.*
import java.util.UUID

data class PersonalRoutine(val id: String = UUID.randomUUID().toString(), val title: String, val prompt: String,
    val time: String = "", val weekdaysOnly: Boolean = false, val enabled: Boolean = true)

object RoutineTiming {
    fun next(time: String, weekdaysOnly: Boolean, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val clock = LocalTime.parse(time)
        var date = now.atZone(zone).toLocalDate()
        while (true) {
            val candidate = date.atTime(clock).atZone(zone).toInstant()
            if (candidate > now && (!weekdaysOnly || date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))) return candidate.toEpochMilli()
            date = date.plusDays(1)
        }
    }
}

class RoutineStore(context: Context) {
    private val db = TaviDatabase.getInstance(context)
    fun list(): List<PersonalRoutine> = db.readableDatabase.rawQuery("SELECT * FROM routines ORDER BY title", null).use { c ->
        buildList { while (c.moveToNext()) add(PersonalRoutine(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4) == 1, c.getInt(5) == 1)) }
    }
    fun get(id: String) = list().firstOrNull { it.id == id }
    fun save(routine: PersonalRoutine) {
        require(routine.title.isNotBlank() && routine.title.length <= 100 && routine.prompt.isNotBlank() && routine.prompt.length <= 1000)
        if (routine.time.isNotBlank()) LocalTime.parse(routine.time)
        val result = db.writableDatabase.insertWithOnConflict("routines", null, ContentValues().apply {
            put("id", routine.id); put("title", routine.title.trim()); put("prompt", routine.prompt.trim())
            put("time", routine.time); put("weekdays", if (routine.weekdaysOnly) 1 else 0); put("enabled", if (routine.enabled) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        check(result != -1L) { "Routine could not be saved." }
    }
    fun setEnabled(id: String, enabled: Boolean) { db.writableDatabase.execSQL("UPDATE routines SET enabled = ? WHERE id = ?", arrayOf<Any>(if (enabled) 1 else 0, id)) }
    fun delete(id: String) { db.writableDatabase.delete("routines", "id = ?", arrayOf(id)) }
    companion object {
        fun createSchema(db: SQLiteDatabase) { db.execSQL("""CREATE TABLE IF NOT EXISTS routines (
            id TEXT PRIMARY KEY, title TEXT NOT NULL, prompt TEXT NOT NULL, time TEXT NOT NULL,
            weekdays INTEGER NOT NULL, enabled INTEGER NOT NULL)""") }
    }
}

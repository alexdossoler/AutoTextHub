package com.charlotteservicehub.autotext.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite database helper for storing message logs.
 */
class MessageLogDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        private const val DATABASE_NAME = "autotext_logs.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_LOGS = "message_logs"
        private const val COLUMN_ID = "id"
        private const val COLUMN_PHONE_NUMBER = "phone_number"
        private const val COLUMN_MESSAGE = "message"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_STATUS = "status"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_LOGS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PHONE_NUMBER TEXT NOT NULL,
                $COLUMN_MESSAGE TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_STATUS TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
        
        // Create index for faster queries
        db.execSQL("CREATE INDEX idx_timestamp ON $TABLE_LOGS($COLUMN_TIMESTAMP DESC)")
        db.execSQL("CREATE INDEX idx_phone ON $TABLE_LOGS($COLUMN_PHONE_NUMBER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOGS")
        onCreate(db)
    }

    /**
     * Insert a new message log entry.
     */
    fun insertLog(log: MessageLog): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PHONE_NUMBER, log.phoneNumber)
            put(COLUMN_MESSAGE, log.message)
            put(COLUMN_TIMESTAMP, log.timestamp)
            put(COLUMN_STATUS, log.status)
        }
        return db.insert(TABLE_LOGS, null, values)
    }

    /**
     * Update the status of a message log entry.
     */
    fun updateStatus(id: Long, status: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_STATUS, status)
        }
        db.update(TABLE_LOGS, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    /**
     * Get recent message logs.
     */
    fun getRecentLogs(limit: Int = 50): List<MessageLog> {
        val logs = mutableListOf<MessageLog>()
        val db = readableDatabase
        
        val cursor = db.query(
            TABLE_LOGS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC",
            limit.toString()
        )

        cursor.use {
            while (it.moveToNext()) {
                logs.add(
                    MessageLog(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                        phoneNumber = it.getString(it.getColumnIndexOrThrow(COLUMN_PHONE_NUMBER)),
                        message = it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                        status = it.getString(it.getColumnIndexOrThrow(COLUMN_STATUS))
                    )
                )
            }
        }
        
        return logs
    }

    /**
     * Get logs for a specific phone number.
     */
    fun getLogsForNumber(phoneNumber: String): List<MessageLog> {
        val logs = mutableListOf<MessageLog>()
        val db = readableDatabase
        
        val cursor = db.query(
            TABLE_LOGS,
            null,
            "$COLUMN_PHONE_NUMBER = ?",
            arrayOf(phoneNumber),
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                logs.add(
                    MessageLog(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                        phoneNumber = it.getString(it.getColumnIndexOrThrow(COLUMN_PHONE_NUMBER)),
                        message = it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                        status = it.getString(it.getColumnIndexOrThrow(COLUMN_STATUS))
                    )
                )
            }
        }
        
        return logs
    }

    /**
     * Get the total count of sent messages.
     */
    fun getMessageCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_LOGS", null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    /**
     * Check if a message was recently sent to a number.
     */
    fun wasRecentlySent(phoneNumber: String, windowMs: Long): Boolean {
        val db = readableDatabase
        val cutoff = System.currentTimeMillis() - windowMs
        
        val cursor = db.query(
            TABLE_LOGS,
            arrayOf(COLUMN_ID),
            "$COLUMN_PHONE_NUMBER = ? AND $COLUMN_TIMESTAMP > ?",
            arrayOf(phoneNumber, cutoff.toString()),
            null,
            null,
            null,
            "1"
        )
        
        cursor.use {
            return it.count > 0
        }
    }

    /**
     * Clear all logs.
     */
    fun clearLogs() {
        val db = writableDatabase
        db.delete(TABLE_LOGS, null, null)
    }

    /**
     * Delete logs older than a certain time.
     */
    fun deleteOldLogs(olderThanMs: Long) {
        val db = writableDatabase
        val cutoff = System.currentTimeMillis() - olderThanMs
        db.delete(TABLE_LOGS, "$COLUMN_TIMESTAMP < ?", arrayOf(cutoff.toString()))
    }
}

package com.lifetracker.app.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Notes every change to a copied table in the change log ([SyncOutboxEntity]) using database triggers, so no screen or
 * collector has to know about syncing. Off until sync is switched on, and switched off again by removing the triggers.
 *
 * The SQL is built here as plain text so it can be checked by tests.
 */
object SyncCapture {
    const val OUTBOX = "sync_outbox"
    private const val NOW_MS = "CAST(strftime('%s', 'now') AS INTEGER) * 1000"

    fun triggerName(table: String, suffix: String) = "sync_${table}_$suffix"

    /** The row's key as text; a composite key is its parts joined by [SyncTables.KEY_SEPARATOR] (character 31). */
    fun keyExpr(table: SyncTables.Table, prefix: String): String =
        table.keys.joinToString(" || char(31) || ") { "CAST($prefix`$it` AS TEXT)" }

    private fun note(table: SyncTables.Table, prefix: String) =
        "INSERT OR REPLACE INTO $OUTBOX (tableName, rowKey, changedMs) VALUES ('${table.name}', ${keyExpr(table, prefix)}, $NOW_MS);"

    /** The three triggers (after insert, update, delete) for one table. Safe to run again. */
    fun createSql(table: SyncTables.Table): List<String> {
        val name = table.name
        // An update that changes the key leaves the old row behind: send it as gone, and the new one as there.
        val oldKey = "INSERT OR REPLACE INTO $OUTBOX (tableName, rowKey, changedMs) SELECT '$name', ${keyExpr(table, "OLD.")}, $NOW_MS " +
            "WHERE ${keyExpr(table, "OLD.")} <> ${keyExpr(table, "NEW.")};"
        return listOf(
            "CREATE TRIGGER IF NOT EXISTS ${triggerName(name, "ai")} AFTER INSERT ON `$name` BEGIN ${note(table, "NEW.")} END",
            "CREATE TRIGGER IF NOT EXISTS ${triggerName(name, "au")} AFTER UPDATE ON `$name` BEGIN ${note(table, "NEW.")} $oldKey END",
            "CREATE TRIGGER IF NOT EXISTS ${triggerName(name, "ad")} AFTER DELETE ON `$name` BEGIN ${note(table, "OLD.")} END",
        )
    }

    fun dropSql(table: SyncTables.Table): List<String> =
        listOf("ai", "au", "ad").map { "DROP TRIGGER IF EXISTS ${triggerName(table.name, it)}" }

    /**
     * Puts every row of a table in the change log (for the first full copy). [where] narrows it, for example to one day of
     * readings, so a huge table can be queued in pieces.
     */
    fun queueAllSql(table: SyncTables.Table, where: String = ""): String =
        "INSERT OR REPLACE INTO $OUTBOX (tableName, rowKey, changedMs) SELECT '${table.name}', ${keyExpr(table, "")}, $NOW_MS FROM `${table.name}`" +
            if (where.isBlank()) "" else " WHERE $where"

    /** Starts noting changes to every copied table. Safe to call on every start. */
    fun install(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            SyncTables.synced.forEach { t -> createSql(t).forEach { db.execSQL(it) } }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Stops noting changes. What is already in the change log stays until it is sent or cleared. */
    fun uninstall(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            SyncTables.synced.forEach { t -> dropSql(t).forEach { db.execSQL(it) } }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

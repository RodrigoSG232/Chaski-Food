package com.chaskifood.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AppConfigEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ChaskiDatabase : RoomDatabase() {
    abstract fun appConfigDao(): AppConfigDao
}
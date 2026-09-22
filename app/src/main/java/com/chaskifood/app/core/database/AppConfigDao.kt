package com.chaskifood.app.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AppConfigDao {
    @Query("SELECT value FROM app_config WHERE key = :key")
    suspend fun getValue(key: String): String?

    @Upsert
    suspend fun upsert(config: AppConfigEntity)
}
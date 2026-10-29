package com.chaskifood.app.feature.checkout.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
internal interface CheckoutIntentDao {
    @Query("SELECT * FROM checkout_intents WHERE ownerUid = :uid AND intentId = :intentId")
    suspend fun get(uid: String, intentId: String): CheckoutIntentEntity?

    @Query("SELECT * FROM checkout_intents WHERE ownerUid = :uid AND (state IN ('PREPARED','IN_FLIGHT','UNKNOWN') OR (state = 'SUCCEEDED' AND receiptApplied = 0)) LIMIT 1")
    suspend fun unresolved(uid: String): CheckoutIntentEntity?

    @Insert
    suspend fun insert(intent: CheckoutIntentEntity)

    @Update
    suspend fun update(intent: CheckoutIntentEntity)
}

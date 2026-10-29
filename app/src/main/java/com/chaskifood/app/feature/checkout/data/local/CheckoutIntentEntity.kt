package com.chaskifood.app.feature.checkout.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "checkout_intents", primaryKeys = ["ownerUid", "intentId"],
    indices = [Index(value = ["ownerUid", "receiptOrderId"], unique = true)])
internal data class CheckoutIntentEntity(
    val ownerUid: String,
    val intentId: String,
    val payloadVersion: Int,
    val payload: String,
    val requestHash: String,
    val quoteId: String,
    val baseCartRevision: Long,
    val state: String,
    val rejection: String?,
    val uncertainty: String?,
    val receiptOrderId: String?,
    val receiptCreatedAt: Long?,
    val receiptApplied: Boolean,
    val cartConsumed: Boolean,
)

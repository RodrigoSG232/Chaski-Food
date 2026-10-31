package com.chaskifood.app.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Añade historia durable del carrito e intenciones sin alterar app_config o líneas existentes. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `carts` ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `checkout_intents` (`ownerUid` TEXT NOT NULL, `intentId` TEXT NOT NULL, " +
            "`payloadVersion` INTEGER NOT NULL, `payload` TEXT NOT NULL, `requestHash` TEXT NOT NULL, `quoteId` TEXT NOT NULL, " +
            "`baseCartRevision` INTEGER NOT NULL, `state` TEXT NOT NULL, `rejection` TEXT, `uncertainty` TEXT, " +
            "`receiptOrderId` TEXT, `receiptCreatedAt` INTEGER, `receiptApplied` INTEGER NOT NULL, `cartConsumed` INTEGER NOT NULL, " +
            "PRIMARY KEY(`ownerUid`, `intentId`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_checkout_intents_ownerUid_receiptOrderId` " +
            "ON `checkout_intents` (`ownerUid`, `receiptOrderId`)")
    }
}

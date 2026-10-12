package com.chaskifood.app.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Conserva app_config y agrega las tablas Cart tal como las exporta Room 2.7.1. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `carts` (`ownerUid` TEXT NOT NULL, " +
                "`businessId` TEXT, `storeId` TEXT, `currencyCode` TEXT NOT NULL, " +
                "PRIMARY KEY(`ownerUid`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cart_items` (`ownerUid` TEXT NOT NULL, " +
                "`productId` TEXT NOT NULL, `productName` TEXT NOT NULL, " +
                "`unitPriceMinor` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, PRIMARY KEY(`ownerUid`, `productId`), " +
                "FOREIGN KEY(`ownerUid`) REFERENCES `carts`(`ownerUid`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_cart_items_ownerUid_position` " +
                "ON `cart_items` (`ownerUid`, `position`)",
        )
    }
}

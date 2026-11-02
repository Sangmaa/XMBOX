package com.fongmi.android.tv.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class Migrations {

    public static final Migration MIGRATION_30_31 = new Migration(30, 31) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE Track");
            database.execSQL("CREATE TABLE Track (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `group` INTEGER NOT NULL, `track` INTEGER NOT NULL, `key` TEXT, `name` TEXT, `selected` INTEGER NOT NULL, `adaptive` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Track_key_type` ON `Track` (`key`, `type`)");
        }
    };

    public static final Migration MIGRATION_31_32 = new Migration(31, 32) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE History_Backup (`key` TEXT NOT NULL, `vodPic` TEXT, `vodName` TEXT, `vodFlag` TEXT, `vodRemarks` TEXT, `episodeUrl` TEXT, `revSort` INTEGER NOT NULL, `revPlay` INTEGER NOT NULL, `createTime` INTEGER NOT NULL, `opening` INTEGER NOT NULL, `ending` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `speed` REAL NOT NULL, `scale` INTEGER NOT NULL, `cid` INTEGER NOT NULL, PRIMARY KEY(`key`))");
            database.execSQL("INSERT INTO History_Backup SELECT `key`, `vodPic`, `vodName`, `vodFlag`, `vodRemarks`, `episodeUrl`, `revSort`, `revPlay`, `createTime`, `opening`, `ending`, `position`, `duration`, `speed`, `scale`, `cid` FROM History");
            database.execSQL("DROP TABLE History");
            database.execSQL("ALTER TABLE History_Backup RENAME to History");
        }
    };

    public static final Migration MIGRATION_32_33 = new Migration(32, 33) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Live ADD COLUMN keep TEXT DEFAULT NULL");
        }
    };

    public static final Migration MIGRATION_33_34 = new Migration(33, 34) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS Download (`id` TEXT NOT NULL PRIMARY KEY, `vodPic` TEXT, `vodName` TEXT, `vodId` TEXT, `url` TEXT, `header` TEXT, `createTime` INTEGER NOT NULL, `progress` INTEGER NOT NULL, `status` TEXT, `duration` INTEGER NOT NULL, `speed` INTEGER NOT NULL)");
        }
    };

    /** 离线下载：补全 Download 表字段（旧 34 版表结构可能缺少 statusInt）。 */
    public static final Migration MIGRATION_34_35 = new Migration(34, 35) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            addColumnIfMissing(database, "Download", "statusInt", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(database, "Download", "localPath", "TEXT");
            addColumnIfMissing(database, "Download", "episodeName", "TEXT");
            addColumnIfMissing(database, "Download", "totalBytes", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(database, "Download", "error", "TEXT");
        }
    };

    /** 离线下载：记录已下载字节数，进度页显示“已下载/总大小/速度”。 */
    public static final Migration MIGRATION_35_36 = new Migration(35, 36) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            addColumnIfMissing(database, "Download", "downloaded", "INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static void addColumnIfMissing(SupportSQLiteDatabase database, String table, String column, String definition) {
        boolean exists = false;
        android.database.Cursor cursor = database.query("PRAGMA table_info(`" + table + "`)");
        try {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (column.equalsIgnoreCase(cursor.getString(nameIndex))) {
                    exists = true;
                    break;
                }
            }
        } finally {
            cursor.close();
        }
        if (!exists) database.execSQL("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
    }
}

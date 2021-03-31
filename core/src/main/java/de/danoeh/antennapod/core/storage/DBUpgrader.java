package de.danoeh.antennapod.core.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import de.danoeh.antennapod.core.feed.FeedItem;

import static de.danoeh.antennapod.core.feed.FeedPreferences.SPEED_USE_GLOBAL;

class DBUpgrader {
    /**
     * Upgrades the given database to a new schema version
     */
    static void upgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        if (oldVersion <= 1) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS + " ADD COLUMN "
                    + PodDBAdapter.KEY_TYPE + " TEXT");
        }
        if (oldVersion <= 2) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_SIMPLECHAPTERS
                    + " ADD COLUMN " + PodDBAdapter.KEY_LINK + " TEXT");
        }
        if (oldVersion <= 3) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                    + " ADD COLUMN " + PodDBAdapter.KEY_ITEM_IDENTIFIER + " TEXT");
        }
        if (oldVersion <= 4) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS + " ADD COLUMN "
                    + PodDBAdapter.KEY_FEED_IDENTIFIER + " TEXT");
        }
        if (oldVersion <= 5) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_DOWNLOAD_LOG
                    + " ADD COLUMN " + PodDBAdapter.KEY_REASON_DETAILED + " TEXT");
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_DOWNLOAD_LOG
                    + " ADD COLUMN " + PodDBAdapter.KEY_DOWNLOADSTATUS_TITLE + " TEXT");
        }
        if (oldVersion <= 6) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_SIMPLECHAPTERS
                    + " ADD COLUMN " + PodDBAdapter.KEY_CHAPTER_TYPE + " INTEGER");
        }
        if (oldVersion <= 7) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                    + " ADD COLUMN " + PodDBAdapter.KEY_PLAYBACK_COMPLETION_DATE
                    + " INTEGER");
        }
        if (oldVersion <= 8) {
            final int KEY_ID_POSITION = 0;
            final int KEY_MEDIA_POSITION = 1;

            // Add feeditem column to feedmedia table
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                    + " ADD COLUMN " + PodDBAdapter.KEY_FEEDITEM
                    + " INTEGER");
            Cursor feeditemCursor = db.query(PodDBAdapter.TABLE_NAME_FEED_ITEMS,
                    new String[]{PodDBAdapter.KEY_ID, PodDBAdapter.KEY_MEDIA}, "? > 0",
                    new String[]{PodDBAdapter.KEY_MEDIA}, null, null, null);
            if (feeditemCursor.moveToFirst()) {
                db.beginTransaction();
                ContentValues contentValues = new ContentValues();
                do {
                    long mediaId = feeditemCursor.getLong(KEY_MEDIA_POSITION);
                    contentValues.put(PodDBAdapter.KEY_FEEDITEM, feeditemCursor.getLong(KEY_ID_POSITION));
                    db.update(PodDBAdapter.TABLE_NAME_FEED_MEDIA, contentValues, PodDBAdapter.KEY_ID + "=?", new String[]{String.valueOf(mediaId)});
                    contentValues.clear();
                } while (feeditemCursor.moveToNext());
                db.setTransactionSuccessful();
                db.endTransaction();
            }
            feeditemCursor.close();
        }
        if (oldVersion <= 9) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_AUTO_DOWNLOAD
                    + " INTEGER DEFAULT 1");
        }
        if (oldVersion <= 10) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN flattr_status"
                    + " INTEGER");
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                    + " ADD COLUMN flattr_status"
                    + " INTEGER");
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                    + " ADD COLUMN " + PodDBAdapter.KEY_PLAYED_DURATION
                    + " INTEGER");
        }
        if (oldVersion <= 11) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_USERNAME
                    + " TEXT");
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_PASSWORD
                    + " TEXT");
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                    + " ADD COLUMN image"
                    + " INTEGER");
        }
        if (oldVersion <= 12) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_IS_PAGED + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_NEXT_PAGE_LINK + " TEXT");
        }
        if (oldVersion <= 13) {
            // remove duplicate rows in "Chapters" table that were created because of a bug.
            db.execSQL(String.format("DELETE FROM %s WHERE %s NOT IN " +
                            "(SELECT MIN(%s) as %s FROM %s GROUP BY %s,%s,%s,%s,%s)",
                    PodDBAdapter.TABLE_NAME_SIMPLECHAPTERS,
                    PodDBAdapter.KEY_ID,
                    PodDBAdapter.KEY_ID,
                    PodDBAdapter.KEY_ID,
                    PodDBAdapter.TABLE_NAME_SIMPLECHAPTERS,
                    PodDBAdapter.KEY_TITLE,
                    PodDBAdapter.KEY_START,
                    PodDBAdapter.KEY_FEEDITEM,
                    PodDBAdapter.KEY_LINK,
                    PodDBAdapter.KEY_CHAPTER_TYPE));
        }
        if (oldVersion <= 14) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                    + " ADD COLUMN " + PodDBAdapter.KEY_AUTO_DOWNLOAD + " INTEGER");
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                    + " SET " + PodDBAdapter.KEY_AUTO_DOWNLOAD + " = "
                    + "(SELECT " + PodDBAdapter.KEY_AUTO_DOWNLOAD
                    + " FROM " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " WHERE " + PodDBAdapter.TABLE_NAME_FEEDS + "." + PodDBAdapter.KEY_ID
                    + " = " + PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_FEED + ")");

            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_HIDE + " TEXT");

            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_LAST_UPDATE_FAILED + " INTEGER DEFAULT 0");

            // create indexes
            db.execSQL(PodDBAdapter.CREATE_INDEX_FEEDITEMS_FEED);
            db.execSQL(PodDBAdapter.CREATE_INDEX_FEEDMEDIA_FEEDITEM);
            db.execSQL(PodDBAdapter.CREATE_INDEX_QUEUE_FEEDITEM);
            db.execSQL(PodDBAdapter.CREATE_INDEX_SIMPLECHAPTERS_FEEDITEM);
        }
        if (oldVersion <= 15) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                    + " ADD COLUMN " + PodDBAdapter.KEY_HAS_EMBEDDED_PICTURE + " INTEGER DEFAULT -1");
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                    + " SET " + PodDBAdapter.KEY_HAS_EMBEDDED_PICTURE + "=0"
                    + " WHERE " + PodDBAdapter.KEY_DOWNLOADED + "=0");
            Cursor c = db.rawQuery("SELECT " + PodDBAdapter.KEY_FILE_URL
                    + " FROM " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                    + " WHERE " + PodDBAdapter.KEY_DOWNLOADED + "=1 "
                    + " AND " + PodDBAdapter.KEY_HAS_EMBEDDED_PICTURE + "=-1", null);
            if (c.moveToFirst()) {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                do {
                    String fileUrl = c.getString(0);
                    try {
                        mmr.setDataSource(fileUrl);
                        byte[] image = mmr.getEmbeddedPicture();
                        if (image != null) {
                            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                                    + " SET " + PodDBAdapter.KEY_HAS_EMBEDDED_PICTURE + "=1"
                                    + " WHERE " + PodDBAdapter.KEY_FILE_URL + "='" + fileUrl + "'");
                        } else {
                            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                                    + " SET " + PodDBAdapter.KEY_HAS_EMBEDDED_PICTURE + "=0"
                                    + " WHERE " + PodDBAdapter.KEY_FILE_URL + "='" + fileUrl + "'");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } while (c.moveToNext());
            }
            c.close();
        }
        if (oldVersion <= 16) {
            String selectNew = "SELECT " + PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_ID
                    + " FROM " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                    + " INNER JOIN " + PodDBAdapter.TABLE_NAME_FEED_MEDIA + " ON "
                    + PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_ID + "="
                    + PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_FEEDITEM
                    + " LEFT OUTER JOIN " + PodDBAdapter.TABLE_NAME_QUEUE + " ON "
                    + PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_ID + "="
                    + PodDBAdapter.TABLE_NAME_QUEUE + "." + PodDBAdapter.KEY_FEEDITEM
                    + " WHERE "
                    + PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_READ + " = 0 AND " // unplayed
                    + PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_DOWNLOADED + " = 0 AND " // undownloaded
                    + PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_POSITION + " = 0 AND " // not partially played
                    + PodDBAdapter.TABLE_NAME_QUEUE + "." + PodDBAdapter.KEY_ID + " IS NULL"; // not in queue
            String sql = "UPDATE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                    + " SET " + PodDBAdapter.KEY_READ + "=" + FeedItem.NEW
                    + " WHERE " + PodDBAdapter.KEY_ID + " IN (" + selectNew + ")";
            Log.d("Migration", "SQL: " + sql);
            db.execSQL(sql);
        }
        if (oldVersion <= 17) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_AUTO_DELETE_ACTION + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 1030005) {
            db.execSQL("UPDATE FeedItems SET auto_download=0 WHERE " +
                    "(read=1 OR id IN (SELECT feeditem FROM FeedMedia WHERE position>0 OR downloaded=1)) " +
                    "AND id NOT IN (SELECT feeditem FROM Queue)");
        }
        if (oldVersion < 1040001) {
            db.execSQL(PodDBAdapter.CREATE_TABLE_FAVORITES);
        }
        if (oldVersion < 1040002) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                    + " ADD COLUMN " + PodDBAdapter.KEY_LAST_PLAYED_TIME + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 1040013) {
            db.execSQL(PodDBAdapter.CREATE_INDEX_FEEDITEMS_PUBDATE);
            db.execSQL(PodDBAdapter.CREATE_INDEX_FEEDITEMS_READ);
        }
        if (oldVersion < 1050003) {
            // Migrates feed list filter data

            db.beginTransaction();

            // Change to intermediate values to avoid overwriting in the following find/replace
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEEDS + "\n" +
                    "SET " + PodDBAdapter.KEY_HIDE + " = replace(" + PodDBAdapter.KEY_HIDE + ", 'unplayed', 'noplay')");
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEEDS + "\n" +
                    "SET " + PodDBAdapter.KEY_HIDE + " = replace(" + PodDBAdapter.KEY_HIDE + ", 'not_queued', 'noqueue')");
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEEDS + "\n" +
                    "SET " + PodDBAdapter.KEY_HIDE + " = replace(" + PodDBAdapter.KEY_HIDE + ", 'not_downloaded', 'nodl')");

            // Replace played, queued, and downloaded with their opposites
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEEDS + "\n" +
                    "SET " + PodDBAdapter.KEY_HIDE + " = replace(" + PodDBAdapter.KEY_HIDE + ", 'played', 'unplayed')");
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEEDS + "\n" +
                    "SET " + PodDBAdapter.KEY_HIDE + " = replace(" + PodDBAdapter.KEY_HIDE + ", 'queued', 'not_queued')");
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEEDS + "\n" +
                    "SET " + PodDBAdapter.KEY_HIDE + " = replace(" + PodDBAdapter.KEY_HIDE + ", 'downloaded', 'not_downloaded')");

            // Now replace intermediates for unplayed, not queued, etc. with their opposites
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEEDS + "\n" +
                    "SET " + PodDBAdapter.KEY_HIDE + " = replace(" + PodDBAdapter.KEY_HIDE + ", 'noplay', 'played')");
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEEDS + "\n" +
                    "SET " + PodDBAdapter.KEY_HIDE + " = replace(" + PodDBAdapter.KEY_HIDE + ", 'noqueue', 'queued')");
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEEDS + "\n" +
                    "SET " + PodDBAdapter.KEY_HIDE + " = replace(" + PodDBAdapter.KEY_HIDE + ", 'nodl', 'downloaded')");

            // Paused doesn't have an opposite, so unplayed is the next best option
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEEDS + "\n" +
                    "SET " + PodDBAdapter.KEY_HIDE + " = replace(" + PodDBAdapter.KEY_HIDE + ", 'paused', 'unplayed')");

            db.setTransactionSuccessful();
            db.endTransaction();

            // and now get ready for autodownload filters
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_INCLUDE_FILTER + " TEXT DEFAULT ''");

            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_EXCLUDE_FILTER + " TEXT DEFAULT ''");

            // and now auto refresh
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_KEEP_UPDATED + " INTEGER DEFAULT 1");
        }
        if (oldVersion < 1050004) {
            // prevent old timestamps to be misinterpreted as ETags
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " SET " + PodDBAdapter.KEY_LASTUPDATE + "=NULL");
        }
        if (oldVersion < 1060200) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_CUSTOM_TITLE + " TEXT");
        }
        if (oldVersion < 1060596) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_IMAGE_URL + " TEXT");
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                    + " ADD COLUMN " + PodDBAdapter.KEY_IMAGE_URL + " TEXT");

            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " SET " + PodDBAdapter.KEY_IMAGE_URL + "  = ("
                    + " SELECT " + PodDBAdapter.KEY_DOWNLOAD_URL
                    + " FROM " + PodDBAdapter.TABLE_NAME_FEED_IMAGES
                    + " WHERE " + PodDBAdapter.TABLE_NAME_FEED_IMAGES + "." + PodDBAdapter.KEY_ID
                    + " = " + PodDBAdapter.TABLE_NAME_FEED_ITEMS + ".image)");

            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEEDS + " SET " + PodDBAdapter.KEY_IMAGE_URL + " = ("
                    + " SELECT " + PodDBAdapter.KEY_DOWNLOAD_URL
                    + " FROM " + PodDBAdapter.TABLE_NAME_FEED_IMAGES
                    + " WHERE " + PodDBAdapter.TABLE_NAME_FEED_IMAGES + "." + PodDBAdapter.KEY_ID
                    + " = " + PodDBAdapter.TABLE_NAME_FEEDS + ".image)");

            db.execSQL("DROP TABLE " + PodDBAdapter.TABLE_NAME_FEED_IMAGES);
        }
        if (oldVersion < 1070400) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_FEED_PLAYBACK_SPEED + " REAL DEFAULT " + SPEED_USE_GLOBAL);
        }
        if (oldVersion < 1070401) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_SORT_ORDER + " TEXT");
        }
        if (oldVersion < 1090000) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_FEED_VOLUME_ADAPTION + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_SIMPLECHAPTERS
                    + " ADD COLUMN " + PodDBAdapter.KEY_IMAGE_URL + " TEXT DEFAULT NULL");
        }
        if (oldVersion < 1090001) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_FEED_SKIP_INTRO + " INTEGER DEFAULT 0;");
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_FEED_SKIP_ENDING + " INTEGER DEFAULT 0;");
        }
        if (oldVersion < 2020000) {
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_EPISODE_NOTIFICATION + " INTEGER DEFAULT 0;");
        }
        if (oldVersion < 2030000) {
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                    + " SET " + PodDBAdapter.KEY_DESCRIPTION + " = content_encoded, content_encoded = NULL "
                    + "WHERE length(" + PodDBAdapter.KEY_DESCRIPTION + ") < length(content_encoded)");
            db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " SET content_encoded = NULL");
            db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                    + " ADD COLUMN " + PodDBAdapter.KEY_FEED_TAGS + " TEXT;");
        }
        if (oldVersion < 2020001) {
            db.execSQL(PodDBAdapter.CREATE_TABLE_NOTES);
        }
    }

}

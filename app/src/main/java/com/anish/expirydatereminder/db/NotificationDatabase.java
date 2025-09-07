package com.anish.expirydatereminder.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class NotificationDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "itemsDatabase";
    private static final int DB_VERSION = 8;
    private static final String TABLE_NAME = "notificationTable";
    private static final String FORMAT_COL = "setting";

    public NotificationDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        try {
            onCreate(getWritableDatabase());
        } catch (SQLException e) {
            String query = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + FORMAT_COL + " INTEGER)";
            getWritableDatabase().execSQL(query);

            ContentValues cv = new ContentValues();
            cv.put(FORMAT_COL, 1);
            getWritableDatabase().insert(TABLE_NAME, null, cv);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String query = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + FORMAT_COL + " INTEGER)";
        sqLiteDatabase.execSQL(query);
        try {
            getCurrentSetting();
        } catch (Exception e) {
            ContentValues cv = new ContentValues();
            cv.put(FORMAT_COL, 1);
            sqLiteDatabase.insert(TABLE_NAME, null, cv);
        }
    }

    //1 = enabled
    //2 = disabled

    public void updateSetting(int a) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, "setting=?", new String[]{"1"});
        db.delete(TABLE_NAME, "setting=?", new String[]{"2"});

        ContentValues cv = new ContentValues();
        cv.put(FORMAT_COL, a);
        db.insert(TABLE_NAME, null, cv);
    }

    public int getCurrentSetting() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor crs = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);
        ArrayList<Integer> categories = new ArrayList<>();
        if (crs.moveToFirst()) {
            do {
                categories.add(crs.getInt(0));
            } while (crs.moveToNext());
        }
        crs.close();
        return categories.get(0);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(sqLiteDatabase);
    }
}

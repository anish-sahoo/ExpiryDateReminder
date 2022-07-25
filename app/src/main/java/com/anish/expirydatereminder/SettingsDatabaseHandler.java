package com.anish.expirydatereminder;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.List;

public class SettingsDatabaseHandler extends SQLiteOpenHelper {

    private static final String DB_NAME = "itemsDatabase";
    private static final int DB_VERSION = 1;
    private static final String TABLE_NAME = "categoriesTable";
    private static final String ID_COL = "id";
    private static final String CATEGORY_COL = "category";
    private static final String TYPE_COL = "type";

    public SettingsDatabaseHandler(Context context){
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String query = "CREATE TABLE " + TABLE_NAME + " ("
                + ID_COL + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + CATEGORY_COL + " TEXT,"
                + TYPE_COL + " INTEGER)";
        sqLiteDatabase.execSQL(query);
    }

    public void addCategory(String categoryName) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();

        values.put(CATEGORY_COL, categoryName);
        values.put(TYPE_COL,1);

        db.insert(TABLE_NAME, null, values);
        db.close();
    }

    public int deleteCategory(String category_name) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_NAME, "category=? and type=?", new String[]{category_name,"1"});
    }

    public void restoreDefault() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, "type=?", new String[]{"1"});
    }

    public List<String> getCategories() {
        return null;
    }

    public void clearDatabase() {
        SQLiteDatabase db = getWritableDatabase();
        String clearDBQuery = "DELETE FROM " + TABLE_NAME;
        db.execSQL(clearDBQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(sqLiteDatabase);
    }
}

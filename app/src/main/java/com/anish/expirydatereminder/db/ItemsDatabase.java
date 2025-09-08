package com.anish.expirydatereminder.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.anish.expirydatereminder.model.ItemModel;

import java.util.ArrayList;

public class ItemsDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "itemsDatabase";
    private static final int DB_VERSION = 8;
    private static final String TABLE_NAME = "itemsTable";
    private static final String ID_COL = "id";
    private static final String NAME_COL = "itemName";
    private static final String MONTH_COL = "month";
    private static final String YEAR_COL = "year";
    private static final String CATEGORY_COL = "category";
    private static final String DATE_COL = "date";

    public ItemsDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        try {
            onCreate(getWritableDatabase());
        } catch (SQLException e) {
            String query = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + ID_COL + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + NAME_COL + " TEXT,"
                    + MONTH_COL + " INTEGER,"
                    + YEAR_COL + " INTEGER,"
                    + DATE_COL + " INTEGER,"
                    + CATEGORY_COL + " TEXT)";

            getWritableDatabase().execSQL(query);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String query = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + ID_COL + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + NAME_COL + " TEXT,"
                + MONTH_COL + " INTEGER,"
                + YEAR_COL + " INTEGER,"
                + DATE_COL + " INTEGER,"
                + CATEGORY_COL + " TEXT)";
        db.execSQL(query);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void clearDatabase() {
        SQLiteDatabase db = getWritableDatabase();
        String clearDBQuery = "DELETE FROM " + TABLE_NAME;
        db.execSQL(clearDBQuery);
    }

    public void addNewItem(ItemModel item) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        String name = item.getItemName();
        int month = item.getMonth();
        int year = item.getYear();
        int date = item.getDate();
        String category = item.getCategory();

        values.put(NAME_COL, name);
        values.put(MONTH_COL, month);
        values.put(YEAR_COL, year);
        values.put(DATE_COL, date);
        values.put(CATEGORY_COL, category);

        db.insert(TABLE_NAME, null, values);
        db.close();
    }

    public ArrayList<ItemModel> getAllItems() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor crs = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);

        ArrayList<ItemModel> items = new ArrayList<>();
        if (crs.moveToFirst()) {
            do {
                items.add(new ItemModel(crs.getString(1), crs.getInt(2), crs.getInt(3), crs.getInt(4), crs.getString(5)));
                // Log.d("Accessing", "ROW ID = " + crs.getString(0));
            } while (crs.moveToNext());
        }
        crs.close();
        return items;
    }

    public ArrayList<ItemModel> getAllItems(String category) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor crs = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);

        ArrayList<ItemModel> items = new ArrayList<>();
        if (crs.moveToFirst()) {
            do {
                if (crs.getString(5).equals(category)) {
                    items.add(new ItemModel(crs.getString(1), crs.getInt(2), crs.getInt(3), crs.getInt(4), crs.getString(5)));
                    // Log.d("Added", "Item with row id =" + crs.getString(0));
                }
                // Log.d("Accessing", "ROW ID = " + crs.getString(0));
            } while (crs.moveToNext());
        }

        crs.close();
        return items;
    }

    public void deleteRow(ItemModel obj) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, "itemName=?", new String[]{obj.getItemName()});
    }
}

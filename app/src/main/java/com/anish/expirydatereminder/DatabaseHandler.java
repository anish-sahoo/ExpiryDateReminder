package com.anish.expirydatereminder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;

public class DatabaseHandler extends SQLiteOpenHelper {

    private static final String DB_NAME = "itemsDatabase";
    private static final int DB_VERSION = 6;
    private static final String TABLE_NAME = "itemsTable";
    private static final String ID_COL = "id";
    private static final String NAME_COL = "itemName";
    private static final String MONTH_COL = "month";
    private static final String YEAR_COL = "year";
    private static final String CATEGORY_COL = "category";
    private static final String DATE_COL = "date";

    public DatabaseHandler(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // on below line we are creating an sqlite query and we are setting our column names along with their data types.
        String query = "CREATE TABLE " + TABLE_NAME + " ("
                + ID_COL + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + NAME_COL + " TEXT,"
                + MONTH_COL + " INTEGER,"
                + YEAR_COL + " INTEGER,"
                + DATE_COL + " INTEGER,"
                + CATEGORY_COL + " TEXT)";

        // at last we are calling a exec sql method to execute above sql query
        db.execSQL(query);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        // Create tables again
        onCreate(db);
    }

    public void clearDatabase() {
        SQLiteDatabase db = getWritableDatabase();
        String clearDBQuery = "DELETE FROM " + TABLE_NAME;
        db.execSQL(clearDBQuery);
    }

    public void addNewItem(ItemModel object) {
        // on below line we are creating a variable for our sqlite database and calling writable method as we are writing data in our database.
        SQLiteDatabase db = this.getWritableDatabase();

        // on below line we are creating a variable for content values.
        ContentValues values = new ContentValues();

        String name = object.getItem();
        int month = object.getMonth();
        int year = object.getYear();
        int date = object.getDate();
        String catg = object.getCategory();

        // on below line we are passing all values along with its key and value pair.
        values.put(NAME_COL, name);
        values.put(MONTH_COL, month);
        values.put(YEAR_COL, year);
        values.put(DATE_COL, date);
        values.put(CATEGORY_COL, catg);

        // after adding all values we are passing content values to our table.
        db.insert(TABLE_NAME, null, values);

        // at last we are closing our database after adding database.
        db.close();
    }

    public ArrayList<ItemModel> getAllItems() {
        // on below line we are creating a database for reading our database.
        SQLiteDatabase db = this.getReadableDatabase();

        // on below line we are creating a cursor with query to read data from database.
        Cursor crs = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);

        // on below line we are creating a new array list.
        ArrayList<ItemModel> items = new ArrayList<>();

        // moving our cursor to first position.
        if (crs.moveToFirst()) {
            do {
                // on below line we are adding the data from cursor to our array list.
                items.add(new ItemModel(crs.getString(1), crs.getInt(2), crs.getInt(3), crs.getInt(4), crs.getString(5)));
                Log.d("Accessing", "ROW ID = " + crs.getString(0));
            } while (crs.moveToNext());
            // moving our cursor to next.
        }

        // at last closing our cursor and returning our array list.
        crs.close();
        return items;
    }

    public ArrayList<ItemModel> getAllItems(String category) {
        // on below line we are creating a database for reading our database.
        SQLiteDatabase db = this.getReadableDatabase();

        // on below line we are creating a cursor with query to read data from database.
        Cursor crs = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);

        // on below line we are creating a new array list.
        ArrayList<ItemModel> items = new ArrayList<>();

        // moving our cursor to first position.
        if (crs.moveToFirst()) {
            do {
                // on below line we are adding the data from cursor to our array list.
                if (crs.getString(5).equals(category)) {
                    items.add(new ItemModel(crs.getString(1), crs.getInt(2), crs.getInt(3), crs.getInt(4), crs.getString(5)));
                    Log.d("Added", "Item with row id =" + crs.getString(0));
                }
                Log.d("Accessing", "ROW ID = " + crs.getString(0));
            } while (crs.moveToNext());
            // moving our cursor to next.
        }

        // at last closing our cursor and returning our array list.
        crs.close();
        return items;
    }

    public void deleteRow(ItemModel obj) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, "itemName=?", new String[]{obj.getItem()});
    }
}

    /*
@Override
public void onCreate(SQLiteDatabase db) {
    // on below line we are creating an sqlite query and we are setting our column names along with their data types.
    String query = "CREATE TABLE " + TABLE_NAME + " ("
            + ID_COL + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + NAME_COL + " TEXT,"
            + MONTH_COL + " INTEGER,"
            + YEAR_COL + " INTEGER)";

    // at last we are calling a exec sql method to execute above sql query
    db.execSQL(query);
}

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                + ID_COL + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + NAME_COL + " TEXT,"
                + MONTH_COL + " INTEGER,"
                + YEAR_COL + " INTEGER,"
                + DATE_COL + "INTEGER,"
                + CATEGORY_COL + "TEXT)");
    }

    public void clearDatabase() {
        SQLiteDatabase db = getWritableDatabase();
        String clearDBQuery = "DELETE FROM "+TABLE_NAME;
        db.execSQL(clearDBQuery);
    }

    public void addNewItem(ItemModel object) {
        // on below line we are creating a variable for our sqlite database and calling writable method as we are writing data in our database.
        SQLiteDatabase db = this.getWritableDatabase();

        // on below line we are creating a variable for content values.
        ContentValues values = new ContentValues();

        String name = object.getItem();
        int month = object.getMonth();
        int year = object.getYear();

        // on below line we are passing all values along with its key and value pair.
        values.put(NAME_COL, name);
        values.put(MONTH_COL, month);
        values.put(YEAR_COL, year);

        // after adding all values we are passing content values to our table.
        db.insert(TABLE_NAME, null, values);

        // at last we are closing our database after adding database.
        db.close();
    }

    public ArrayList<ItemModel> getAllItems() {
        // on below line we are creating a database for reading our database.
        SQLiteDatabase db = this.getReadableDatabase();

        // on below line we are creating a cursor with query to read data from database.
        Cursor crs = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);

        // on below line we are creating a new array list.
        ArrayList<ItemModel> items = new ArrayList<>();

        // moving our cursor to first position.
        if (crs.moveToFirst()) {
            do {
                // on below line we are adding the data from cursor to our array list.
                items.add(new ItemModel(crs.getString(1), crs.getInt(2), crs.getInt(3)));
                Log.d("Accessing","ROW ID = "+crs.getString(0));
            } while (crs.moveToNext());
            // moving our cursor to next.
        }

        // at last closing our cursor and returning our array list.
        crs.close();
        return items;
    }

    public ArrayList<ItemModel> getAllItems(String category) {
        // on below line we are creating a database for reading our database.
        SQLiteDatabase db = this.getReadableDatabase();

        // on below line we are creating a cursor with query to read data from database.
        Cursor crs = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);

        // on below line we are creating a new array list.
        ArrayList<ItemModel> items = new ArrayList<>();

        // moving our cursor to first position.
        if (crs.moveToFirst()) {
            do {
                // on below line we are adding the data from cursor to our array list.
                items.add(new ItemModel(crs.getString(1), crs.getInt(2), crs.getInt(3)));
                Log.d("Accessing","ROW ID = "+crs.getString(0));
            } while (crs.moveToNext());
            // moving our cursor to next.
        }

        // at last closing our cursor and returning our array list.
        crs.close();
        return items;
    }

    public void deleteRow(ItemModel obj){
        SQLiteDatabase db = this.getReadableDatabase();
        db.delete(TABLE_NAME, "itemName=?",new String[]{obj.getItem()});
    }
}*/

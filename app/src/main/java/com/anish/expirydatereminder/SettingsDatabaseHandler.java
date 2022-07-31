package com.anish.expirydatereminder;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsDatabaseHandler extends SQLiteOpenHelper {

    private static final String DB_NAME = "itemsDatabase";
    private static final int DB_VERSION = 8;
    private static final String TABLE_NAME = "categoriesTable";
    private static final String ID_COL = "id";
    private static final String CATEGORY_COL = "category";
    private static final String TYPE_COL = "type";

    public SettingsDatabaseHandler(Context context){
        super(context, DB_NAME, null, DB_VERSION);
        try{
            onCreate(getWritableDatabase());
        }
        catch (SQLException e) {
            String query = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + ID_COL + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + CATEGORY_COL + " TEXT,"
                    + TYPE_COL + " INTEGER)";
            getWritableDatabase().execSQL(query);

            addCategory("All Items",0);
            addCategory("Grocery",0);
            addCategory("Important Dates",0);
            addCategory("Medicine",0);
            addCategory("Snacks",0);
            addCategory("Frozen goods",0);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        String query = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + ID_COL + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + CATEGORY_COL + " TEXT,"
                + TYPE_COL + " INTEGER)";
        sqLiteDatabase.execSQL(query);

        addCategory("All Items",0);
        addCategory("Grocery",0);
        addCategory("Important Dates",0);
        addCategory("Medicine",0);
        addCategory("Snacks",0);
        addCategory("Frozen goods",0);
    }

    public boolean addCategory(String categoryName, int type) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();

        values.put(CATEGORY_COL, categoryName);
        values.put(TYPE_COL,type);

        if(!checkIfCategoryExists(categoryName)){
            db.insert(TABLE_NAME, null, values);
            db.close();
            return true;
        }
        db.close();
        return false;
    }

    public int deleteCategory(String category_name) {
        SQLiteDatabase db = this.getWritableDatabase();
        int i = db.delete(TABLE_NAME, "category=? and type=?", new String[]{category_name,"1"});
        if(i!=0) {
            db.delete("itemsTable", "category=?", new String[]{category_name});
        }
        return i;
    }

    public void restoreDefault() {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor crs = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);
        if (crs.moveToFirst()) {
            do {
                if(crs.getInt(2) == 1) {
                    Log.d("Entered restore default loop in the method -------   ", crs.getString(1)+" is deleting now!");
                    db.delete("itemsTable", "category=?", new String[]{crs.getString(1)});
                }
            } while (crs.moveToNext());
        }
        crs.close();

        db.delete(TABLE_NAME, "type=?", new String[]{"1"});

        Log.d("LOOK AT ME ------- ", "restoreDefault METHOD CALLED ");
        Log.d("ITEMS REMAINING IN DB ------ ", Arrays.toString(getCategories().toArray()));
    }

    public List<String> getCategories() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor crs = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);
        ArrayList<String> categories = new ArrayList<>();
        if (crs.moveToFirst()) {
            do {
                categories.add(crs.getString(1));
            } while (crs.moveToNext());
        }
        crs.close();
        return categories;
    }

    public List<String> getDeletableCategories(){
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor crs = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);
        ArrayList<String> categories = new ArrayList<>();
        if (crs.moveToFirst()) {
            do {
                if(crs.getInt(2) == 1) {
                    categories.add(crs.getString(1));
                }
            } while (crs.moveToNext());
        }
        crs.close();
        return categories;
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

    public boolean checkIfCategoryExists(String category_name){
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor crs = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);
        ArrayList<String> categories = new ArrayList<>();
        if (crs.moveToFirst()) {
            do {
                categories.add(crs.getString(1));
            } while (crs.moveToNext());
        }
        crs.close();

        return categories.contains(category_name);
    }
}

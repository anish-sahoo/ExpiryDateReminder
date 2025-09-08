package com.anish.expirydatereminder.model;

public class ItemModel {
    private final String itemName;
    private final String category;
    private final int month;
    private final int year;
    private final int date;

    public ItemModel(String i, int m, int y, int d, String cat){
        this.itemName = i;
        this.month = m;
        this.year = y;
        this.date = d;
        this.category = cat;
    }

    private ItemModel() {
        this.itemName = null;
        this.month = 0;
        this.year = 0;
        this.category = null;
        this.date = 0;
    }

    public String getCategory() {
        return category;
    }

    public int getDate() {
        return date;
    }

    public String getItemName() {
        return itemName;
    }

    public int getMonth() {
        return month;
    }

    public int getYear() {
        return year;
    }

}

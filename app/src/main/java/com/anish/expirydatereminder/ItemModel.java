package com.anish.expirydatereminder;

public class ItemModel {
    private String item;
    private int month, year, id;

    ItemModel(String i, int m, int y){
        this.item = i;
        this.month = m;
        this.year = y;
    }

    public ItemModel() {
        this.item = null;
        this.month = 0;
        this.year = 0;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }
}

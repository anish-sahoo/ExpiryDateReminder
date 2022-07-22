package com.anish.expirydatereminder;

public class ItemModel {
    private String item, category;
    private int month, year, id, date;

    ItemModel(String i, int m, int y){
        this.item = i;
        this.month = m;
        this.year = y;
    }

    public ItemModel() {
        this.item = null;
        this.month = 0;
        this.year = 0;
        this.category = null;
        this.date = 0;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getDate() {
        return date;
    }

    public void setDate(int date) {
        this.date = date;
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

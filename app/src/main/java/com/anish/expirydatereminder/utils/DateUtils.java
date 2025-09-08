package com.anish.expirydatereminder.utils;

import com.anish.expirydatereminder.model.ItemModel;

public class DateUtils {
    public static String leftPad(int num) {
        if (num < 10) {
            return "0" + num;
        }
        return Integer.toString(num);
    }

    public static String getDateItemStr(ItemModel item, int format) {
        return getDateItemStr(item.getMonth(), item.getDate(), item.getYear(), format, item.getItemName());
    }

    public static String getDateStr(int month, int date, int year, int format) {
        if (format == 1) {
            return leftPad(month) + "/" + leftPad(date) + "/" + year;
        }
        return leftPad(date) + "/" + leftPad(month) + "/" + year;
    }

    public static String getDateItemStr(int month, int date, int year, int format, String itemName) {
        if (format == 1) {
            return getDateStr(month, date, year, format) + " : " + itemName;
        }
        return getDateStr(month, date, year, format) + " : " + itemName;
    }
}

package com.anish.expirydatereminder.utils;

import android.net.Uri;

import com.anish.expirydatereminder.constants.AppConstants;
import com.anish.expirydatereminder.model.ItemModel;

public class FileUtils {

    public static String getImageFileName(String itemName, int date, int month, int year, String categoryName) {
        return "image_" + itemName + "." + date + "." + month + "." + year + "." + categoryName + ".jpg";
    }

    public static String getImageFileName(ItemModel item) {
        return getImageFileName(item.getItemName(), item.getDate(), item.getMonth(), item.getYear(), item.getCategory());
    }

    public static String getContentResolverPath() {
        return "content://" + AppConstants.APP_NAME + ".provider/cache/images/";
    }

    public static Uri getFileURI(String fileName) {
        return Uri.parse(getContentResolverPath() + fileName);
    }
}

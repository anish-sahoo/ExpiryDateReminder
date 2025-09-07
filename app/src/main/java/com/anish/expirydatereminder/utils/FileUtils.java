package com.anish.expirydatereminder.utils;

import android.net.Uri;

import com.anish.expirydatereminder.constants.AppConstants;

public class FileUtils {

    public static String getImageFileName(String itemName, int date, int month, int year, String categoryName) {
        return "image_" + itemName + "." + date + "." + month + "." + year + "." + categoryName + ".jpg";
    }

    public static String getContentResolverPath() {
        return "content://" + AppConstants.APP_NAME + ".provider/cache/images/";
    }

    public static Uri getFileURI(String fileName) {
        return Uri.parse(getContentResolverPath() + fileName);
    }
}

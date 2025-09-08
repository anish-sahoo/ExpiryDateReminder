package com.anish.expirydatereminder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.anish.expirydatereminder.db.ItemsDatabase;
import com.anish.expirydatereminder.db.DateFormatDatabase;
import com.anish.expirydatereminder.db.SettingsDatabase;
import com.anish.expirydatereminder.dialog.AddItemDialog;
import com.anish.expirydatereminder.dialog.HelpDialog;
import com.anish.expirydatereminder.dialog.InsertDialogOptions;
import com.anish.expirydatereminder.dialog.SettingsDialogOptions;
import com.anish.expirydatereminder.dialog.SettingsDialog;
import com.anish.expirydatereminder.messaging.WakeUpReceiver;
import com.anish.expirydatereminder.model.ItemModel;
import com.anish.expirydatereminder.utils.DateUtils;
import com.anish.expirydatereminder.utils.FileUtils;
import com.anish.expirydatereminder.utils.SearchResult;
import com.anish.expirydatereminder.utils.ViewUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements InsertDialogOptions, SettingsDialogOptions, AdapterView.OnItemSelectedListener {

    public static final String SORT_BY_DATE = "Sort By: Date";
    public static final String SORT_BY_NAME = "Sort By: Name";
    private ArrayList<String> items;
    private ArrayAdapter<String> itemsArray, categoriesArray;
    private ListView listView;
    private FloatingActionButton refreshButton, addItemButton, settingsButton, helpButton;
    private Button sortButton;
    private ItemsDatabase itemsDB;
    private List<ItemModel> modelList;
    private List<String> categories;
    SettingsDatabase settingsDatabase;
    DateFormatDatabase dbh;

    private Spinner categorySpinner;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewUtils.applyInsets(findViewById(R.id.main_activity), getWindow());

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }

        Objects.requireNonNull(getSupportActionBar()).hide();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        createNotificationChannel();

        listView = findViewById(R.id.listView);
        refreshButton = findViewById(R.id.refreshButton);
        addItemButton = findViewById(R.id.addItemButton);

        items = new ArrayList<>();
        itemsArray = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        listView.setAdapter(itemsArray);

        setUpListViewListener();

        addItemButton.setOnClickListener(view -> openDialog());

        itemsDB = new ItemsDatabase(MainActivity.this);
        dbh = new DateFormatDatabase(MainActivity.this);

        repopulateListSortedByDate(itemsDB.getAllItems());

        refreshButton.setOnClickListener(view -> {
            modelList.clear();
            itemsArray.clear();
            itemsArray.notifyDataSetChanged();
            String spinnerValue = categorySpinner.getSelectedItem().toString();
            modelList = spinnerValue.equals("All Items") ? itemsDB.getAllItems() : itemsDB.getAllItems(spinnerValue);
            repopulateListSortedByDate(modelList);
        });

        settingsDatabase = new SettingsDatabase(MainActivity.this);
        categories = new ArrayList<>();
        categories = settingsDatabase.getCategories();
        categorySpinner = findViewById(R.id.category_spinner);
        categorySpinner.setOnItemSelectedListener(this);
        categoriesArray = new ArrayAdapter<>(this, R.layout.spinner_item, categories);
        categoriesArray.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoriesArray);

        settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(view -> {
            openSettingsDialog();
            categoriesArray.notifyDataSetChanged();
        });


        sortButton = findViewById(R.id.sortButton);
        sortButton.setOnClickListener(view -> {
            String spinnerValue = categorySpinner.getSelectedItem().toString();
            modelList = spinnerValue.equals("All Items") ? itemsDB.getAllItems() : itemsDB.getAllItems(spinnerValue);
            switch (sortButton.getText().toString()) {
                case SORT_BY_NAME -> {
                    repopulateSortedByName(modelList);
                    sortButton.setText(SORT_BY_DATE);
                }
                case SORT_BY_DATE -> {
                    repopulateListSortedByDate(modelList);
                    sortButton.setText(SORT_BY_NAME);
                }
            }
        });

        helpButton = findViewById(R.id.help_button);
        helpButton.setOnClickListener(view -> {
            HelpDialog hd = new HelpDialog();
            hd.show(getSupportFragmentManager(), "Help");
        });

        AlarmManager alarmMgr = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(MainActivity.this, WakeUpReceiver.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(MainActivity.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 7);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        Log.d("Alarm Set", "interval 15 min");
        alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_HALF_DAY, alarmIntent);
    }

    private void repopulateSortedByName(List<ItemModel> modelList) {
        itemsArray.clear();
        modelList.sort(Comparator.comparing(ItemModel::getItemName));
        populate(modelList);
        itemsArray.notifyDataSetChanged();
    }

    private void repopulateListSortedByDate(List<ItemModel> modelList) {
        itemsArray.clear();
        modelList.sort(Comparator.comparingInt(ItemModel::getDate));
        modelList.sort(Comparator.comparingInt(ItemModel::getMonth));
        modelList.sort(Comparator.comparingInt(ItemModel::getYear));
        populate(modelList);
        itemsArray.notifyDataSetChanged();
    }

    private void populate(List<ItemModel> list) {
        list.forEach(a -> {
            addItem(a, dbh.getCurrentFormat());
        });
    }

    private void setUpListViewListener() {
        listView.setOnItemLongClickListener((adapterView, view, i, l) -> {
            AlertDialog alert = new AlertDialog.Builder(MainActivity.this)
                    .setMessage("Delete this item?")
                    .setCancelable(false)
                    .setPositiveButton("Yes", (dialog, which) -> {
                        Toast.makeText(getApplicationContext(), "Item Removed", Toast.LENGTH_SHORT).show();
                        items.remove(i);
                        itemsDB.deleteRow(modelList.get(i));
                        itemsArray.notifyDataSetChanged();

                        Uri uri = FileUtils.getFileURI(FileUtils.getImageFileName(modelList.get(i)));
                        getContentResolver().delete(uri, null, null);
                    })
                    .setNegativeButton("No", (dialog, which) -> dialog.cancel())
                    .setTitle(items.get(i))
                    .create();

            alert.show();
            return true;
        });

        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            Intent myIntent = new Intent(MainActivity.this, ItemDetailsOnClickActivity.class);
            myIntent.putExtra("item name", modelList.get(i).getItemName());
            myIntent.putExtra("month", modelList.get(i).getMonth());
            myIntent.putExtra("year", modelList.get(i).getYear());
            myIntent.putExtra("date", modelList.get(i).getDate());
            myIntent.putExtra("category", modelList.get(i).getCategory());
            MainActivity.this.startActivity(myIntent);
        });
    }

    private SearchResult checkIfItemExists(String itemName, int month, int year, int date, String category) {
        return itemsDB.getAllItems().stream()
                .filter(obj -> obj.getItemName().equals(itemName))
                .findFirst()
                .map(obj -> {
                    if (obj.getMonth() != month || obj.getYear() != year || obj.getDate() != date) {
                        return SearchResult.ITEM_EXISTS_SAME_NAME_DIFFERENT_DATE;
                    }
                    if (obj.getCategory().equals(category)) {
                        return SearchResult.ITEM_EXISTS;
                    }
                    return SearchResult.ITEM_EXISTS_SAME_NAME_DIFFERENT_CATEGORY;
                })
                .orElse(SearchResult.ITEM_DOES_NOT_EXIST);
    }

    private void addItem(ItemModel obj, int dateFormat) {
        if (obj.getItemName().isEmpty()) {
            Toast.makeText(getApplicationContext(), "Cannot be empty string, enter text", Toast.LENGTH_SHORT).show();
            return;
        }
        itemsArray.add(DateUtils.getDateItemStr(obj, dateFormat));
    }

    private void addItem(String itemName, int date, int month, int year, String category) {
        switch (checkIfItemExists(itemName, month, year, date, category)) {
            case ITEM_DOES_NOT_EXIST -> {
                try (DateFormatDatabase dfd = new DateFormatDatabase(getApplicationContext())) {
                    itemsArray.add(DateUtils.getDateItemStr(month, date, year, dfd.getCurrentFormat(), itemName));
                    itemsDB.addNewItem(new ItemModel(itemName, month, year, date, category));
                    modelList.add(new ItemModel(itemName, month, year, date, category));
                }
            }
            case ITEM_EXISTS ->
                    Toast.makeText(getApplicationContext(), "This item already exists!", Toast.LENGTH_SHORT).show();
            case ITEM_EXISTS_SAME_NAME_DIFFERENT_DATE ->
                    Toast.makeText(getApplicationContext(), "Item with same name exists with different date!", Toast.LENGTH_SHORT).show();
            case ITEM_EXISTS_SAME_NAME_DIFFERENT_CATEGORY ->
                    Toast.makeText(getApplicationContext(), "Item with same name exists in a different category!", Toast.LENGTH_SHORT).show();
        }
    }

    private void createNotificationChannel() {
        CharSequence name = "EDR Channel";
        String description = "Channel for Expiry Date Reminder notifications";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel("edr_channel_1", name, importance);
        channel.setDescription(description);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private void openDialog() {
        AddItemDialog addItemDialog = new AddItemDialog();
        addItemDialog.show(getSupportFragmentManager(), "Add item");
    }

    private void openSettingsDialog() {
        SettingsDialog settingsDialog = new SettingsDialog();
        settingsDialog.show(getSupportFragmentManager(), "Settings");
    }

    @Override
    public void addItemAsNeeded(String item_name, int date, int month, int year, String category_name) {
        addItem(item_name, date, month, year, category_name);
        itemsArray.notifyDataSetChanged();
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        modelList = i == 0 ? itemsDB.getAllItems() : itemsDB.getAllItems(categories.get(i));
        if (sortButton.getText().toString().equals(SORT_BY_DATE)) {
            repopulateListSortedByDate(modelList);
        } else {
            repopulateSortedByName(modelList);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void refresh(int a) {
        categoriesArray.clear();
        categories.clear();
        categoriesArray.notifyDataSetChanged();
        categories = settingsDatabase.getCategories();
        categoriesArray.addAll(categories);
        categoriesArray.notifyDataSetChanged();

        modelList.clear();
        repopulateListSortedByDate(itemsDB.getAllItems());
        sortButton.setText(SORT_BY_DATE);
    }

    private void deleteAllImages(List<ItemModel> items_of_category) {
        items_of_category.forEach(item -> {
            Uri uri = FileUtils.getFileURI(FileUtils.getImageFileName(item));
            ContentResolver contentResolver = getContentResolver();
            contentResolver.delete(uri, null, null);
        });
    }

    @Override
    public void deleteAllImages(String category) {
        deleteAllImages(itemsDB.getAllItems(category));
    }

    @Override
    public void deleteAllImages() {
        deleteAllImages(itemsDB.getAllItems());
    }
}
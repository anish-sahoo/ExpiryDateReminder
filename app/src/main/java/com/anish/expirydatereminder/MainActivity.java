package com.anish.expirydatereminder;

import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;

import android.Manifest;
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
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private ArrayList<String> items;
    private ArrayAdapter<String> itemsAdapter;
    private ListView listView;
    private Button button, refreshButton;
    private DatabaseHandler dbHandler;
    private List<ItemModel> modelList;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }

        Objects.requireNonNull(getSupportActionBar()).hide();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        createNotificationChannel();

        listView = findViewById(R.id.listView);
        button = findViewById(R.id.addItemButton);
        refreshButton = findViewById(R.id.refreshButton);

        items = new ArrayList<>();
        itemsAdapter = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1, items);
        listView.setAdapter(itemsAdapter);

        button.setOnClickListener(view -> addItem());
        setUpListViewListener();

        dbHandler = new DatabaseHandler(MainActivity.this);
        modelList = dbHandler.getAllItems();
        Collections.sort(modelList, Comparator.comparingInt(ItemModel::getMonth));
        Collections.sort(modelList, Comparator.comparingInt(ItemModel::getYear));
        populate(modelList);

        if(modelList.size()>0)
            setNotifications(0);
        else
            setNotifications(1);

        refreshButton.setOnClickListener(view -> {
            Intent myIntent = new Intent(MainActivity.this, MainActivity.class);
            myIntent.addFlags(FLAG_ACTIVITY_NO_ANIMATION);
            MainActivity.this.startActivity(myIntent);
        });

    }

    private void populate(List<ItemModel> list) {
        for(ItemModel a:list){
            addItem(a);
        }
    }

    private void setUpListViewListener() {
        listView.setOnItemLongClickListener((adapterView, view, i, l) -> {
            AlertDialog.Builder altdial = new AlertDialog.Builder(MainActivity.this);
            altdial.setMessage("Do you want to delete "+items.get(i)+" ???").setCancelable(false)
                    .setPositiveButton("Yes", (dialog, which) -> {
                        Context context = getApplicationContext();
                        Toast.makeText(context,"Item Removed",Toast.LENGTH_SHORT).show();
                        items.remove(i);
                        System.out.println(modelList.get(i).getItem());
                        dbHandler.deleteRow(modelList.get(i));
                        itemsAdapter.notifyDataSetChanged();

                        String fileNameText = "anish_" + modelList.get(i).getItem() + "." + modelList.get(i).getMonth() + "." + modelList.get(i).getYear() + ".jpg";
                        Uri uri = Uri.parse("content://com.anish.expirydatereminder.provider/cache/images/"+fileNameText);
                        ContentResolver contentResolver = getContentResolver();
                        contentResolver.delete(uri,null,null);
                    })
                    .setNegativeButton("No", (dialog, which) -> dialog.cancel());

            AlertDialog alert = altdial.create();
            alert.setTitle("Delete this item?");
            alert.show();
            return true;
        });
        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            Intent myIntent = new Intent(MainActivity.this, ItemDetailsOnClick.class);
            myIntent.putExtra("item name",modelList.get(i).getItem());
            myIntent.putExtra("month",modelList.get(i).getMonth());
            myIntent.putExtra("year",modelList.get(i).getYear());
            MainActivity.this.startActivity(myIntent);
        });
    }

    private void addItem() {
        EditText input = findViewById(R.id.itemName);
        EditText m = findViewById(R.id.month);
        EditText y = findViewById(R.id.year);

        String itemName = input.getText().toString();

        if(!itemName.isEmpty() && !m.getText().toString().isEmpty() && !y.getText().toString().isEmpty()){
            int month = Integer.parseInt(m.getText().toString());
            int year  = Integer.parseInt(y.getText().toString());

            if(month > 12 || month<0){
                Toast.makeText(getApplicationContext(),"Incorrect month input!!!",Toast.LENGTH_SHORT).show();
                m.setText("");
                return;
            }

            if(year < 1000 || year > 9999){
                Toast.makeText(getApplicationContext(),"Please enter year in YYYY format",Toast.LENGTH_SHORT).show();
                y.setText("");
                return;
            }

            int checker = checkIfItemExists(itemName,month,year);

            if(checker == 3){
                Toast.makeText(getApplicationContext(),"This item already exists!",Toast.LENGTH_SHORT).show();
                input.setText("");
                y.setText("");
                m.setText("");
                return;
            }
            else if(checker == 2){
                Toast.makeText(getApplicationContext(),"Same Item with different expiry date exists! ",Toast.LENGTH_SHORT).show();
            }


            String text = month + "/" + year + " : " + itemName;

            itemsAdapter.add(text);
            dbHandler.addNewItem(new ItemModel(itemName,month,year));
            modelList.add(new ItemModel(itemName,month,year));

            input.setText("");
            m.setText("");
            y.setText("");
        }
        else{
            Toast.makeText(getApplicationContext(),"Fields are empty!!!",Toast.LENGTH_SHORT).show();
        }
    }

    private int checkIfItemExists(String item, int month, int year) {
        List<ItemModel> list_of_items = dbHandler.getAllItems();
        for (ItemModel obj:list_of_items){
            if(obj.getItem().equals(item)){
                if(obj.getMonth() == month){
                    if(obj.getYear() == year){
                        return 3;
                    }
                }
                if(year >= obj.getYear()) {
                    return 2;
                }
            }
        }
        return 1;
    }

    private void addItem(ItemModel obj) {
        String itemName = obj.getItem();
        int month = obj.getMonth();
        int year  = obj.getYear();
        String totalItem = month+"/"+year + " : " + itemName;
        if(!itemName.isEmpty()){
            itemsAdapter.add(totalItem);
        }
        else{
            Toast.makeText(getApplicationContext(),"Cannot be empty string, enter text",Toast.LENGTH_SHORT).show();
        }
    }

    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            CharSequence name = "EDR Channel";
            String description = "Channel for Expiry Date Reminder notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("edr_channel_1",name,importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    private void setNotifications(int a){
        Intent intent = new Intent(MainActivity.this,NotificationHandler.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0, intent,PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager)getSystemService(ALARM_SERVICE);
        Calendar alarmStartTime = Calendar.getInstance();
        Calendar now = Calendar.getInstance();
        alarmStartTime.set(Calendar.HOUR_OF_DAY, 8);
        alarmStartTime.set(Calendar.MINUTE, 30);
        alarmStartTime.set(Calendar.SECOND, 0);
        if (now.after(alarmStartTime)) {
            Log.d("Hey","Added a day");
            alarmStartTime.add(Calendar.DATE, 1);
        }
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, alarmStartTime.getTimeInMillis(), AlarmManager.INTERVAL_HALF_DAY, pendingIntent);
        Log.d("Alarm","Alarms set for everyday 8:30 am and pm.");
        if(a==1){
            alarmManager.cancel(pendingIntent);
        }
    }
}
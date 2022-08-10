package com.anish.expirydatereminder;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class SettingsDialogHandler extends AppCompatDialogFragment implements AdapterView.OnItemSelectedListener {
    Button restoreButton,add_button, dateformat1, dateformat2, removeEverythingButton, enableNotificationsButton, disableNotificationsButton;
    ArrayAdapter<String> settings_spinner_adapter;
    List<String> categories_list;
    EditText category_input;
    ListView lv;
    SettingsDatabaseHandler sdh;
    SettingsDialog obj;

    @SuppressLint("SetTextI18n")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState){
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.settings_layout,null);
        sdh = new SettingsDatabaseHandler(getContext());

        DateFormatDatabase dfd = new DateFormatDatabase(getContext());

        builder.setView(v)
                .setTitle("Settings")
                .setOnCancelListener(dialogInterface -> obj.refresh(dfd.getCurrentFormat()))
                .setPositiveButton("ok",((dialogInterface, i) -> obj.refresh(dfd.getCurrentFormat())));

        category_input = v.findViewById(R.id.add_category_name);
        restoreButton = v.findViewById(R.id.restore_button);
        add_button = v.findViewById(R.id.add_category_button);
        removeEverythingButton = v.findViewById(R.id.remove_everything_button);

        lv = v.findViewById(R.id.categories_list_view);
        categories_list = new ArrayList<>();
        categories_list = sdh.getCategories();
        settings_spinner_adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1,categories_list);
        lv.setAdapter(settings_spinner_adapter);

        add_button.setOnClickListener(view1 -> {
            System.err.println("Button clicked");
            if(!category_input.getText().toString().trim().equals("")) {
                if (sdh.getCategories().contains(category_input.getText().toString().trim())) {
                    Toast.makeText(getContext(), "Category already exists!", Toast.LENGTH_SHORT).show();
                    category_input.setText("");
                    return;
                }
                sdh.addCategory(category_input.getText().toString().trim(), 1);
                category_input.setText("");
                settings_spinner_adapter.clear();
                categories_list.clear();
                settings_spinner_adapter.notifyDataSetChanged();
                categories_list = sdh.getCategories();
                settings_spinner_adapter.addAll(categories_list);
                settings_spinner_adapter.notifyDataSetChanged();
            }
            else {
                Toast.makeText(getContext(), "Category name cannot be empty!", Toast.LENGTH_SHORT).show();
                category_input.setText("");
            }
        });

        lv.setOnItemLongClickListener((adapterView, view, i, l) -> {
            AlertDialog.Builder altdial = new AlertDialog.Builder(getContext());
            altdial.setMessage("ALL ITEMS UNDER THIS CATEGORY WILL BE REMOVED!\nAre you sure?").setCancelable(false)
                .setPositiveButton("Yes", (dialog, which) -> {
                    if(sdh.deleteCategory(categories_list.get(i)) == 1)
                        Toast.makeText(getContext(),"Item deleted",Toast.LENGTH_SHORT).show();
                    else
                        Toast.makeText(getContext(),"Default item cannot be deleted",Toast.LENGTH_SHORT).show();
                    settings_spinner_adapter.clear();
                    categories_list.clear();
                    settings_spinner_adapter.notifyDataSetChanged();
                    categories_list = sdh.getCategories();
                    settings_spinner_adapter.addAll(categories_list);
                    settings_spinner_adapter.notifyDataSetChanged();
                })
                .setNegativeButton("No", (dialog, which) -> dialog.cancel());

            AlertDialog alert = altdial.create();
            alert.setTitle("Delete this category?");
            alert.show();

            return true;
        });

        restoreButton.setOnClickListener(view -> {
            AlertDialog.Builder altdial = new AlertDialog.Builder(getContext());
            altdial.setMessage("ALL ITEMS UNDER ALL USER-DEFINED CATEGORIES WILL BE REMOVED!\nAre you sure?").setCancelable(false)
                    .setPositiveButton("Yes", (dialog, which) -> {
                        for(String str: sdh.getDeletableCategories()){
                            obj.deleteImages(str);
                        }
                        sdh.restoreDefault();
                        settings_spinner_adapter.clear();
                        categories_list.clear();
                        settings_spinner_adapter.notifyDataSetChanged();
                        categories_list = sdh.getCategories();
                        settings_spinner_adapter.addAll(categories_list);
                        settings_spinner_adapter.notifyDataSetChanged();
                        obj.refresh(dfd.getCurrentFormat());
                    })
                    .setNegativeButton("No", (dialog, which) -> dialog.cancel());

            AlertDialog alert = altdial.create();
            alert.setTitle("Delete all user-defined categories?");
            alert.show();
        });

        removeEverythingButton.setOnClickListener(view -> removeEverythingWipeOutAllItems());

        dateformat1 = v.findViewById(R.id.dd_mm_yyyy_button);
        dateformat2 = v.findViewById(R.id.dd_mm_yyyy_button_2);

        if(dfd.getCurrentFormat() == 1){
            dateformat1.setBackgroundColor(Color.parseColor("#4CAF50"));
            dateformat2.setBackgroundColor(Color.parseColor("#807C7C"));
        }
        else {
            dateformat2.setBackgroundColor(Color.parseColor("#4CAF50"));
            dateformat1.setBackgroundColor(Color.parseColor("#807C7C"));
        }

        dateformat1.setOnClickListener(view -> {
            dateformat1.setBackgroundColor(Color.parseColor("#4CAF50"));
            dateformat2.setBackgroundColor(Color.parseColor("#807C7C"));
            dfd.update(1);
            Toast.makeText(getContext(), "Date format changed to MM/DD/YYYY", Toast.LENGTH_SHORT).show();
        });
        dateformat2.setOnClickListener(view -> {
            dateformat2.setBackgroundColor(Color.parseColor("#4CAF50"));
            dateformat1.setBackgroundColor(Color.parseColor("#807C7C"));
            dfd.update(2);
            Toast.makeText(getContext(), "Date format changed to DD/MM/YYYY", Toast.LENGTH_SHORT).show();
        });

        enableNotificationsButton = v.findViewById(R.id.enableNotificationsButton);
        disableNotificationsButton = v.findViewById(R.id.disableNotificationsButton);
        TextView tv = v.findViewById(R.id.NotificationStatus);

        NotificationDatabase ndb = new NotificationDatabase(getContext());

        if(ndb.getCurrentSetting() == 1)
            tv.setText("Current status: Notifications Enabled");
        else tv.setText("Current status: Notifications Disabled");

        enableNotificationsButton.setOnClickListener(view -> {
            obj.updateNotificationSettings(1);
            ndb.updateSetting(1);
            tv.setText("Current status: Notifications Enabled");
            Toast.makeText(getContext(), "Notifications enabled", Toast.LENGTH_SHORT).show();
        });
        disableNotificationsButton.setOnClickListener(view -> {
            obj.updateNotificationSettings(2);
            ndb.updateSetting(2);
            tv.setText("Current status: Notifications Disabled");
            Toast.makeText(getContext(), "Notifications disabled!", Toast.LENGTH_SHORT).show();
        });
        return builder.create();
    }

    private void removeEverythingWipeOutAllItems() {
        AlertDialog.Builder altdial = new AlertDialog.Builder(getContext());
        altdial.setMessage("ALL ITEMS WILL BE REMOVED!\nAre you sure?").setCancelable(false)
                .setPositiveButton("Yes", (dialog, which) -> {
                    obj.deleteImages();

                    DatabaseHandler dbHandler = new DatabaseHandler(getContext());
                    dbHandler.clearDatabase();

                    DateFormatDatabase dfd = new DateFormatDatabase(getContext());
                    obj.refresh(dfd.getCurrentFormat());
                })
                .setNegativeButton("No", (dialog, which) -> dialog.cancel());

        AlertDialog alert = altdial.create();
        alert.setTitle("Delete all items?");
        alert.show();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            obj = (SettingsDialogHandler.SettingsDialog) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + "must implement ExampleDialogListener");
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

    }
    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    public interface SettingsDialog{
        void refresh(int a);
        void deleteImages(String category);
        void deleteImages();
        void updateNotificationSettings(int a);
    }
}

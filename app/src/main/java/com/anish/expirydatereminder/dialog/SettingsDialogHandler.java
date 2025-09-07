package com.anish.expirydatereminder.dialog;

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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDialogFragment;

import com.anish.expirydatereminder.R;
import com.anish.expirydatereminder.db.ItemsDatabase;
import com.anish.expirydatereminder.db.DateFormatDatabase;
import com.anish.expirydatereminder.db.NotificationDatabase;
import com.anish.expirydatereminder.db.SettingsDatabase;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

public class SettingsDialogHandler extends AppCompatDialogFragment implements AdapterView.OnItemSelectedListener {
    private Button restoreButton, addButton, dateFormat1Btn, dateFormat2Btn, removeEverythingButton;
    private ArrayAdapter<String> settingsSpinnerAdapter;
    private List<String> categoriesList;
    private EditText categoryInput;
    private ListView listView;
    private SettingsDatabase settingsDatabase;
    private SettingsDialogOptions settingsDialogOptions;
    private SwitchMaterial notificationsSwitch;

    @SuppressLint("SetTextI18n")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.settings_layout, null);
        settingsDatabase = new SettingsDatabase(getContext());

        DateFormatDatabase dfd = new DateFormatDatabase(getContext());

        builder.setView(v)
                .setTitle("Settings")
                .setOnCancelListener(dialogInterface -> settingsDialogOptions.refresh(dfd.getCurrentFormat()))
                .setPositiveButton("ok", ((dialogInterface, i) -> settingsDialogOptions.refresh(dfd.getCurrentFormat())));

        categoryInput = v.findViewById(R.id.add_category_name);
        restoreButton = v.findViewById(R.id.restore_button);
        addButton = v.findViewById(R.id.add_category_button);
        removeEverythingButton = v.findViewById(R.id.remove_everything_button);

        listView = v.findViewById(R.id.categories_list_view);
        categoriesList = new ArrayList<>();
        categoriesList = settingsDatabase.getCategories();
        settingsSpinnerAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, categoriesList);
        listView.setAdapter(settingsSpinnerAdapter);

        addButton.setOnClickListener(view1 -> {
            System.err.println("Button clicked");
            if (!categoryInput.getText().toString().trim().equals("")) {
                if (settingsDatabase.getCategories().contains(categoryInput.getText().toString().trim())) {
                    Toast.makeText(getContext(), "Category already exists!", Toast.LENGTH_SHORT).show();
                    categoryInput.setText("");
                    return;
                }
                settingsDatabase.addCategory(categoryInput.getText().toString().trim(), 1);
                categoryInput.setText("");
                settingsSpinnerAdapter.clear();
                categoriesList.clear();
                settingsSpinnerAdapter.notifyDataSetChanged();
                categoriesList = settingsDatabase.getCategories();
                settingsSpinnerAdapter.addAll(categoriesList);
                settingsSpinnerAdapter.notifyDataSetChanged();
            } else {
                Toast.makeText(getContext(), "Category name cannot be empty!", Toast.LENGTH_SHORT).show();
                categoryInput.setText("");
            }
        });

        listView.setOnItemLongClickListener((adapterView, view, i, l) -> {
            AlertDialog.Builder altdial = new AlertDialog.Builder(getContext());
            altdial.setMessage("ALL ITEMS UNDER THIS CATEGORY WILL BE REMOVED!\nAre you sure?").setCancelable(false)
                    .setPositiveButton("Yes", (dialog, which) -> {
                        if (settingsDatabase.deleteCategory(categoriesList.get(i)) == 1)
                            Toast.makeText(getContext(), "Item deleted", Toast.LENGTH_SHORT).show();
                        else
                            Toast.makeText(getContext(), "Default item cannot be deleted", Toast.LENGTH_SHORT).show();
                        settingsSpinnerAdapter.clear();
                        categoriesList.clear();
                        settingsSpinnerAdapter.notifyDataSetChanged();
                        categoriesList = settingsDatabase.getCategories();
                        settingsSpinnerAdapter.addAll(categoriesList);
                        settingsSpinnerAdapter.notifyDataSetChanged();
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
                        for (String str : settingsDatabase.getDeletableCategories()) {
                            settingsDialogOptions.deleteImages(str);
                        }
                        settingsDatabase.restoreDefault();
                        settingsSpinnerAdapter.clear();
                        categoriesList.clear();
                        settingsSpinnerAdapter.notifyDataSetChanged();
                        categoriesList = settingsDatabase.getCategories();
                        settingsSpinnerAdapter.addAll(categoriesList);
                        settingsSpinnerAdapter.notifyDataSetChanged();
                        settingsDialogOptions.refresh(dfd.getCurrentFormat());
                    })
                    .setNegativeButton("No", (dialog, which) -> dialog.cancel());

            AlertDialog alert = altdial.create();
            alert.setTitle("Delete all user-defined categories?");
            alert.show();
        });

        removeEverythingButton.setOnClickListener(view -> removeEverythingWipeOutAllItems());

        dateFormat1Btn = v.findViewById(R.id.dd_mm_yyyy_button);
        dateFormat2Btn = v.findViewById(R.id.dd_mm_yyyy_button_2);

        if (dfd.getCurrentFormat() == 1) {
            dateFormat1Btn.setBackgroundColor(Color.parseColor("#4CAF50"));
            dateFormat2Btn.setBackgroundColor(Color.parseColor("#807C7C"));
        } else {
            dateFormat2Btn.setBackgroundColor(Color.parseColor("#4CAF50"));
            dateFormat1Btn.setBackgroundColor(Color.parseColor("#807C7C"));
        }

        dateFormat1Btn.setOnClickListener(view -> {
            dateFormat1Btn.setBackgroundColor(Color.parseColor("#4CAF50"));
            dateFormat2Btn.setBackgroundColor(Color.parseColor("#807C7C"));
            dfd.update(1);
            Toast.makeText(getContext(), "Date format changed to MM/DD/YYYY", Toast.LENGTH_SHORT).show();
        });
        dateFormat2Btn.setOnClickListener(view -> {
            dateFormat2Btn.setBackgroundColor(Color.parseColor("#4CAF50"));
            dateFormat1Btn.setBackgroundColor(Color.parseColor("#807C7C"));
            dfd.update(2);
            Toast.makeText(getContext(), "Date format changed to DD/MM/YYYY", Toast.LENGTH_SHORT).show();
        });

        NotificationDatabase ndb = new NotificationDatabase(getContext());

        notificationsSwitch = v.findViewById(R.id.notifications_switch);
        notificationsSwitch.setChecked(ndb.getCurrentSetting() == 1);

        notificationsSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                ndb.updateSetting(1);
                Toast.makeText(getContext(), "Notifications enabled!", Toast.LENGTH_SHORT).show();
            } else {
                ndb.updateSetting(2);
                Toast.makeText(getContext(), "Notifications disabled!", Toast.LENGTH_SHORT).show();
            }
        });

        return builder.create();
    }

    private void removeEverythingWipeOutAllItems() {
        AlertDialog.Builder altdial = new AlertDialog.Builder(getContext());
        altdial.setMessage("ALL ITEMS WILL BE REMOVED!\nAre you sure?").setCancelable(false)
                .setPositiveButton("Yes", (dialog, which) -> {
                    settingsDialogOptions.deleteImages();

                    ItemsDatabase dbHandler = new ItemsDatabase(getContext());
                    dbHandler.clearDatabase();

                    DateFormatDatabase dateFormatDatabase = new DateFormatDatabase(getContext());
                    settingsDialogOptions.refresh(dateFormatDatabase.getCurrentFormat());
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
            settingsDialogOptions = (SettingsDialogOptions) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + "must implement SettingsDialogOptions");
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }
}

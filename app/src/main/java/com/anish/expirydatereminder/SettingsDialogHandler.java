package com.anish.expirydatereminder;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
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

import java.util.ArrayList;
import java.util.List;

public class SettingsDialogHandler extends AppCompatDialogFragment implements AdapterView.OnItemSelectedListener {
    Button restoreButton,add_button;
    ArrayAdapter<String> settings_spinner_adapter;
    List<String> categories_list;
    EditText category_input;
    ListView lv;
    SettingsDatabaseHandler sdh;
    SettingsDialog obj;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState){
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.settings_layout,null);
        sdh = new SettingsDatabaseHandler(getContext());


        builder.setView(v)
                .setTitle("Settings")
                .setOnCancelListener(dialogInterface -> obj.refresh())
                .setPositiveButton("ok",((dialogInterface, i) -> obj.refresh()));

        category_input = v.findViewById(R.id.add_category_name);
        restoreButton = v.findViewById(R.id.restore_button);
        add_button = v.findViewById(R.id.add_category_button);

        lv = v.findViewById(R.id.categories_list_view);
        categories_list = new ArrayList<>();
        categories_list = sdh.getCategories();
        settings_spinner_adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1,categories_list);
        lv.setAdapter(settings_spinner_adapter);

        add_button.setOnClickListener(view1 -> {
            System.err.println("Button clicked");
            sdh.addCategory(category_input.getText().toString(),1);
            category_input.setText("");
            settings_spinner_adapter.clear();
            categories_list.clear();
            settings_spinner_adapter.notifyDataSetChanged();
            categories_list = sdh.getCategories();
            settings_spinner_adapter.addAll(categories_list);
            settings_spinner_adapter.notifyDataSetChanged();
        });

        lv.setOnItemLongClickListener((adapterView, view, i, l) -> {
            AlertDialog.Builder altdial = new AlertDialog.Builder(getContext());
            altdial.setMessage("Do you want to delete this category? ALL ITEMS OF THIS CATEGORY WILL BE REMOVED FROM THE DATABASE").setCancelable(false)
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
            alert.setTitle("Delete this item?");
            alert.show();

            return true;
        });

        restoreButton.setOnClickListener(view -> {
            AlertDialog.Builder altdial = new AlertDialog.Builder(getContext());
            altdial.setMessage("Do you want restore Defaults? ALL ITEMS FROM CATEGORIES YOU ADDED WILL BE REMOVED").setCancelable(false)
                    .setPositiveButton("Yes", (dialog, which) -> {
                        sdh.restoreDefault();
                        settings_spinner_adapter.clear();
                        categories_list.clear();
                        settings_spinner_adapter.notifyDataSetChanged();
                        categories_list = sdh.getCategories();
                        settings_spinner_adapter.addAll(categories_list);
                        settings_spinner_adapter.notifyDataSetChanged();
                        obj.refresh();
                    })
                    .setNegativeButton("No", (dialog, which) -> dialog.cancel());

            AlertDialog alert = altdial.create();
            alert.setTitle("Delete this item?");
            alert.show();

            /*sdh.restoreDefault();
            settings_spinner_adapter.clear();
            categories_list.clear();
            settings_spinner_adapter.notifyDataSetChanged();
            categories_list = sdh.getCategories();
            settings_spinner_adapter.addAll(categories_list);
            settings_spinner_adapter.notifyDataSetChanged();
            obj.refresh();*/
        });

        return builder.create();
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
        void refresh();
    }
}

package com.anish.expirydatereminder;

import android.app.AlertDialog;
import android.app.Dialog;
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
    ArrayAdapter<String> adapter;
    List<String> categories_list;
    EditText category_input;
    ListView lv;
    SettingsDatabaseHandler sdh;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState){
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.settings_layout,null);
        sdh = new SettingsDatabaseHandler(getContext());


        builder.setView(v)
                .setTitle("Settings")
                .setNegativeButton("cancel",((dialogInterface, i) -> {}))
                .setPositiveButton("ok",((dialogInterface, i) -> adapter.notifyDataSetChanged()));

        category_input = v.findViewById(R.id.add_category_name);
        restoreButton = v.findViewById(R.id.restore_button);
        add_button = v.findViewById(R.id.add_category_button);

        lv = v.findViewById(R.id.categories_list_view);
        categories_list = new ArrayList<>();
        categories_list = sdh.getCategories();
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1,categories_list);
        lv.setAdapter(adapter);

        add_button.setOnClickListener(view1 -> {
            System.err.println("Button clicked");
            sdh.addCategory(category_input.getText().toString(),1);
            category_input.setText("");
            adapter.clear();
            categories_list.clear();
            adapter.notifyDataSetChanged();
            categories_list = sdh.getCategories();
            adapter.addAll(categories_list);
            adapter.notifyDataSetChanged();
        });

        lv.setOnItemLongClickListener((adapterView, view, i, l) -> {
            AlertDialog.Builder altdial = new AlertDialog.Builder(getContext());
            altdial.setMessage("Do you want to delete this category? ALL ITEMS OF THIS CATEGORY WILL BE REMOVED FROM THE DATABASE").setCancelable(false)
                .setPositiveButton("Yes", (dialog, which) -> {
                    if(sdh.deleteCategory(categories_list.get(i)) == 1)
                        Toast.makeText(getContext(),"Item deleted",Toast.LENGTH_SHORT).show();
                    else
                        Toast.makeText(getContext(),"Default item cannot be deleted",Toast.LENGTH_SHORT).show();
                    adapter.clear();
                    categories_list.clear();
                    adapter.notifyDataSetChanged();
                    categories_list = sdh.getCategories();
                    adapter.addAll(categories_list);
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("No", (dialog, which) -> dialog.cancel());

            AlertDialog alert = altdial.create();
            alert.setTitle("Delete this item?");
            alert.show();

            return true;
        });

        restoreButton.setOnClickListener(view -> sdh.restoreDefault());

        return builder.create();
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

    }
    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }
}

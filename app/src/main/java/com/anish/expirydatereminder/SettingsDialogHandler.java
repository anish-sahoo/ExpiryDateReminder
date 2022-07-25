package com.anish.expirydatereminder;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
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
    ExampleDialogListener2 listener;
    Button restoreButton,add_button;
    ArrayAdapter<String> adapter;
    List<String> categories_list;
    EditText category_input;
    ListView lv;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState){
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.settings_layout,null);

        builder.setView(view)
                .setTitle("Settings")
                .setNegativeButton("cancel",((dialogInterface, i) -> {}))
                .setPositiveButton("ok",((dialogInterface, i) -> {
                    add_button.setOnClickListener(view1 -> {
                        listener.addCategory(category_input.getText().toString());
                        adapter.notifyDataSetChanged();
                    });

                    lv.setOnItemLongClickListener(((adapterView, view1, i1, l) -> {
                        AlertDialog.Builder altdial = new AlertDialog.Builder(getContext());
                        altdial.setMessage("Do you want to delete this category? All items of this category will be removed from the database").setCancelable(false)
                                .setPositiveButton("Yes", (dialog, which) -> {
                                    listener.deleteCategory(categories_list.get(i));
                                    categories_list.clear();
                                    adapter.clear();
                                    categories_list = listener.getCategories();
                                    adapter.notifyDataSetChanged();
                                })
                                .setNegativeButton("No", (dialog, which) -> dialog.cancel());

                        return true;
                    }));
                }));

        category_input = view.findViewById(R.id.add_category_name);
        restoreButton = view.findViewById(R.id.restore_button);
        add_button = view.findViewById(R.id.add_category_button);

        lv = view.findViewById(R.id.categories_list_view);
        categories_list = new ArrayList<>();
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1,categories_list);
        lv.setAdapter(adapter);


        return builder.create();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            listener = (ExampleDialogListener2) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + "must implement ExampleDialogListener2");
        }
    }

    public interface ExampleDialogListener2 {
        void addCategory(String categoryName);
        int deleteCategory(String category_name);
        void restoreDefault();
        List<String> getCategories();
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }
}

package com.anish.expirydatereminder;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

import java.time.Year;
import java.time.YearMonth;
import java.util.List;

public class DialogHandler extends AppCompatDialogFragment implements AdapterView.OnItemSelectedListener {
    private EditText name, month, year, date;
    private ExampleDialogListener listener;
    private Spinner spinner;
    private List<String> CATEGORIES;
    SettingsDatabaseHandler settingsDatabaseHandler;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.item_activity_layout, null);

        builder.setView(view)
                .setTitle("Add Item")
                .setNegativeButton("cancel", (dialogInterface, i) -> {})
                .setPositiveButton("ok", (dialogInterface, i) -> {
                    if(!name.getText().toString().isEmpty()) {
                        if (!month.getText().toString().isEmpty()) {
                            String itemName = name.getText().toString();
                            int expiryMonth = Integer.parseInt(month.getText().toString());
                            int expiryYear = 0;
                            if (!year.getText().toString().isEmpty()) {
                                expiryYear = Integer.parseInt(year.getText().toString());
                            }
                            int d;

                            String item_category = spinner.getSelectedItem().toString();

                            if (expiryMonth < 13 && expiryMonth > 0) {
                                if (date.getText().toString().isEmpty()) {
                                    d = YearMonth.of(expiryYear, expiryMonth).lengthOfMonth();
                                }
                                else {
                                    d = Integer.parseInt(date.getText().toString());
                                    if (d < 1 || d > YearMonth.of(expiryYear, expiryMonth).lengthOfMonth()) {
                                        Toast.makeText(getContext(), "Incorrect date!", Toast.LENGTH_SHORT).show();
                                        date.setText("");
                                        return;
                                    }
                                }
                                if (year.getText().toString().isEmpty()) {
                                    listener.addItemAsNeeded(itemName, d, expiryMonth, Year.now().getValue(), item_category);
                                }
                                else {
                                    if (expiryYear > 999 && expiryYear < 10000)
                                        listener.addItemAsNeeded(itemName, d, expiryMonth, expiryYear, item_category);
                                    else {
                                        Toast.makeText(getContext(), "Invalid Year, it should be in YYYY format", Toast.LENGTH_SHORT).show();
                                        year.setText("");
                                    }
                                }
                            }
                            else Toast.makeText(getContext(), "Invalid month, it should be between 1 and 12", Toast.LENGTH_SHORT).show();
                        }
                        else Toast.makeText(getContext(), "Month cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                    else Toast.makeText(getContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                });

        name = view.findViewById(R.id.name_dialog_box_editText);
        month = view.findViewById(R.id.month_dialog_box_editText);
        year = view.findViewById(R.id.year_dialog_box_editText);
        date = view.findViewById(R.id.date_dialog_box_editText);

        settingsDatabaseHandler = new SettingsDatabaseHandler(getContext());
        CATEGORIES = settingsDatabaseHandler.getCategories();

        spinner = view.findViewById(R.id.spinner_category_selector_add_item);
        spinner.setOnItemSelectedListener(this);
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getContext(), R.layout.spinner_layout_2,CATEGORIES);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(arrayAdapter);

        return builder.create();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            listener = (ExampleDialogListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + "must implement ExampleDialogListener");
        }
    }

    public interface ExampleDialogListener {
        void addItemAsNeeded(String item_name, int date, int month, int year, String category_name);
    }


    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if(i!=0) {
            Toast.makeText(getContext(), CATEGORIES.get(i), Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }
}

//spinner in main activity not working
//sort by not working
//when a category is removed the product is not going away
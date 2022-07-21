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

public class DialogHandler extends AppCompatDialogFragment implements AdapterView.OnItemSelectedListener {
    private EditText name, month, year;
    private ExampleDialogListener listener;
    private Spinner spinner;
    private String[] CATEGORIES = new String[]{"All Items","Grocery","Important dates","Medicine","Other Items"};

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.item_activity_layout, null);

        builder.setView(view)
                .setTitle("Add Item")
                .setNegativeButton("cancel", (dialogInterface, i) -> {})
                .setPositiveButton("ok", (dialogInterface, i) -> {
                    if(!name.getText().toString().isEmpty() && !month.getText().toString().isEmpty() && !year.getText().toString().isEmpty()){
                        String itemName = name.getText().toString();
                        int expiryMonth = Integer.parseInt(month.getText().toString());
                        int expiryYear = Integer.parseInt(year.getText().toString());

                        String item_category = spinner.getSelectedItem().toString();
                        Toast.makeText(getContext(), item_category+" was selected from spinner inside dialog box", Toast.LENGTH_SHORT).show();
                        int date = 0;
                        if(expiryMonth < 13 && expiryMonth > 0) {
                            if (expiryYear > 999 && expiryYear < 10000)
                                listener.addItemAsNeeded(itemName, date, expiryMonth, expiryYear, item_category);
                            else
                                Toast.makeText(getContext(), "Incorrect year input, it must be in YYYY format!", Toast.LENGTH_SHORT).show();
                        }
                        else Toast.makeText(getContext(),"Incorrect month input, please try again!",Toast.LENGTH_SHORT).show();
                    }
                    else Toast.makeText(getContext(), "Empty fields not allowed!", Toast.LENGTH_SHORT).show();
                });

        name = view.findViewById(R.id.name_dialog_box_editText);
        month = view.findViewById(R.id.month_dialog_box_editText);
        year = view.findViewById(R.id.year_dialog_box_editText);

        spinner = view.findViewById(R.id.spinner_category_selector_add_item);
        spinner.setOnItemSelectedListener(this);
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item,CATEGORIES);
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
        Toast.makeText(getContext(), CATEGORIES[i], Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }
}
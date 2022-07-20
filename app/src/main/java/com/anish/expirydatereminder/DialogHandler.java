package com.anish.expirydatereminder;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

public class DialogHandler extends AppCompatDialogFragment {
    private EditText name, month, year;
    private ExampleDialogListener listener;


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.item_activity_layout, null);

        builder.setView(view)
                .setTitle("Login")
                .setNegativeButton("cancel", (dialogInterface, i) -> {

                })
                .setPositiveButton("ok", (dialogInterface, i) -> {
                    if(!name.getText().toString().isEmpty() && !month.getText().toString().isEmpty() && !year.getText().toString().isEmpty()){
                        String itemName = name.getText().toString();
                        int expiryMonth = Integer.parseInt(month.getText().toString());
                        int expiryYear = Integer.parseInt(year.getText().toString());

                        if(expiryMonth < 12 && expiryMonth > 0) {
                            if (expiryYear > 999 && expiryYear < 10000)
                                listener.addItemAsNeeded(itemName, expiryMonth, expiryYear);
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

        return builder.create();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            listener = (ExampleDialogListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() +
                    "must implement ExampleDialogListener");
        }
    }

    public interface ExampleDialogListener {
        void addItemAsNeeded(String item_name, int month, int year);
    }
}
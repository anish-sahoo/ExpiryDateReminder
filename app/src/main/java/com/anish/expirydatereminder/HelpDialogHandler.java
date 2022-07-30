package com.anish.expirydatereminder;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatDialogFragment;

public class HelpDialogHandler extends AppCompatDialogFragment {
    public Dialog onCreateDialog(Bundle savedInstanceState){
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.help_layout,null);

        builder.setView(v)
                .setTitle("Help")
                .setPositiveButton("OK", (dialogInterface, i) -> {});
        return builder.create();
    }
}

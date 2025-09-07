package com.anish.expirydatereminder.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatDialogFragment;

import com.anish.expirydatereminder.R;

public class HelpDialogHandler extends AppCompatDialogFragment {
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.help_layout, null);

        return new AlertDialog.Builder(getActivity()).setView(v)
                .setTitle("Help")
                .setPositiveButton("OK", (dialogInterface, i) -> {
                }).create();
    }
}

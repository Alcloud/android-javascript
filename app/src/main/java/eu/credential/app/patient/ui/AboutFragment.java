package eu.credential.app.patient.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

public class AboutFragment extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Forschungsprojekt Teil B")
                .setMessage("Alternativer Iterationsschritt: Web + Native Technologie.")
                .setPositiveButton("Ok", (dialog, id) -> dialog.cancel());
        return builder.create();
    }
}

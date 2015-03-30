package com.ehu.neurgai;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

public class DialogoIdiomas extends DialogFragment {

	public Dialog onCreateDialog(Bundle savedInstanceState){
		
		final String[] idiomas = {getString(R.string.local_language), getString(R.string.euskaraz)};
		
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.app_name);
		
		builder.setIcon(R.drawable.ic_upvehu);
		
		builder.setItems(idiomas, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int item) {
				Log.i("Dialogos","Opción elegida: "+idiomas[item]);
			}
		});

		return builder.create();
	}
}

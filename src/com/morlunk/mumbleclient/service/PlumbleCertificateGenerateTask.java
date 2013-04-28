package com.morlunk.mumbleclient.service;

import java.io.File;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.widget.Toast;

import com.morlunk.mumbleclient.R;

public class PlumbleCertificateGenerateTask extends AsyncTask<Void, Void, File> {
	
	private Context context;
	private ProgressDialog loadingDialog;
	
	public PlumbleCertificateGenerateTask(Context context) {
		this.context = context;
	}
	
	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		
		loadingDialog = new ProgressDialog(context);
		loadingDialog.setIndeterminate(true);
		loadingDialog.setMessage(context.getString(R.string.generateCertProgress));
		loadingDialog.setOnCancelListener(new OnCancelListener() {
			
			@Override
			public void onCancel(DialogInterface arg0) {
				cancel(true);
				
			}
		});
		loadingDialog.show();
	}
	@Override
	protected File doInBackground(Void... params) {
		try {
			File certificate = PlumbleCertificateManager.generateCertificate();
			return certificate;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	protected void onPostExecute(File result) {
		super.onPostExecute(result);
		if(result != null) {
			Toast.makeText(context, context.getString(R.string.generateCertSuccess, result.getName()), Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(context, R.string.generateCertFailure, Toast.LENGTH_SHORT).show();
		}
		
		loadingDialog.dismiss();
	}
}

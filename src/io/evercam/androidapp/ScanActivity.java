package io.evercam.androidapp;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.bugsense.trace.BugSenseHandler;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import io.evercam.androidapp.cambase.CambaseAPI;
import io.evercam.androidapp.cambase.CambaseModel;
import io.evercam.androidapp.cambase.Manufacturer;
import io.evercam.androidapp.custom.CustomedDialog;
import io.evercam.androidapp.tasks.ScanForCameraTask;
import io.evercam.androidapp.utils.Constants;
import io.evercam.androidapp.utils.EvercamApiHelper;
import io.evercam.network.discovery.DiscoveredCamera;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.OnItemClickListener;

public class ScanActivity extends Activity
{
	private final String TAG = "evercamplay-ScanActivity";

	private View scanProgressView;
	private View scanResultListView;
	private View scanResultNoCameraView;

	private ListView cameraListView;

	private ArrayList<HashMap<String, Object>> deviceArrayList;
	private ScanResultAdapter deviceAdapter;
	private ArrayList<DiscoveredCamera> discoveredCameras;
	private SparseArray<Drawable> drawableArray;

	private final String ADAPTER_KEY_LOGO = "camera_logo";
	private final String ADAPTER_KEY_IP = "camera_id";
	private final String ADAPTER_KEY_MODEL = "camera_model";
	
	private ScanForCameraTask scanTask;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_scan);

		if (Constants.isAppTrackingEnabled)
		{
			BugSenseHandler.initAndStartSession(this, Constants.bugsense_ApiKey);
		}
		
		EvercamPlayApplication.sendScreenAnalytics(this, getString(R.string.screen_scan_camera));
		
		scanProgressView = findViewById(R.id.scan_status_layout);
		scanResultListView = findViewById(R.id.scan_result_layout);
		scanResultNoCameraView = findViewById(R.id.scan_result_no_camera_layout);
		Button cancelButton = (Button) findViewById(R.id.button_cancel_scan);

		drawableArray = new SparseArray<Drawable>();

		cameraListView = (ListView) findViewById(R.id.scan_result_list);
		Button addManuallyButton = (Button) findViewById(R.id.button_add_camera_manually);

		deviceArrayList = new ArrayList<HashMap<String, Object>>();
		deviceAdapter = new ScanResultAdapter(this, deviceArrayList, R.layout.scan_list_layout,
				new String[] { ADAPTER_KEY_LOGO, ADAPTER_KEY_IP, ADAPTER_KEY_MODEL }, new int[] {
						R.id.camera_img, R.id.camera_ip, R.id.camera_model });
		cameraListView.setAdapter(deviceAdapter);

		cameraListView.setOnItemClickListener(new OnItemClickListener(){
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3)
			{
				@SuppressWarnings("unchecked")
				HashMap<String, Object> map = (HashMap<String, Object>) cameraListView
						.getItemAtPosition(position);
				String cameraIpText = (String) map.get(ADAPTER_KEY_IP);
				String cameraIp = cameraIpText;
				if (cameraIpText.contains(":"))
				{
					cameraIp = cameraIpText.substring(0, cameraIpText.indexOf(':'));
				}
				for (DiscoveredCamera camera : discoveredCameras)
				{
					if (camera.getIP().equals(cameraIp))
					{
						Intent intentAddCamera = new Intent(ScanActivity.this,
								AddEditCameraActivity.class);
						intentAddCamera.putExtra("camera", camera);
						startActivityForResult(intentAddCamera, Constants.REQUEST_CODE_ADD_CAMERA);
					}
				}
			}
		});
		
		cancelButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v)
			{
				CustomedDialog.getConfirmCancelScanDialog(ScanActivity.this, new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						stopDiscovery();
						finish();
					}
				}).show();
			}
		});

		addManuallyButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v)
			{
				startActivityForResult(new Intent(ScanActivity.this, AddEditCameraActivity.class),
						Constants.REQUEST_CODE_ADD_CAMERA);
			}
		});

		startDiscovery();
	}
	
	@Override
	public void onStart()
	{
		super.onStart();

		if (Constants.isAppTrackingEnabled)
		{
			if (Constants.isAppTrackingEnabled) BugSenseHandler.startSession(this);
		}
	}

	@Override
	public void onStop()
	{
		super.onStop();

		if (Constants.isAppTrackingEnabled)
		{
			if (Constants.isAppTrackingEnabled) BugSenseHandler.closeSession(this);
		}
	}

	// Finish this activity and transfer the result from AddEditCameraActivity to
	// CamerasActivity.
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode == Constants.REQUEST_CODE_ADD_CAMERA)
		{
			Intent returnIntent = new Intent();

			if (resultCode == Constants.RESULT_TRUE)
			{
				setResult(Constants.RESULT_TRUE, returnIntent);
				finish();
			}
			else
			{
				setResult(Constants.RESULT_FALSE, returnIntent);
				// If no camera found, finish this page, otherwise stay in
				// scanning page to let the user choose another.
				if (discoveredCameras == null)
				{
					finish();
				}
			}
		}
	}

	private void startDiscovery()
	{
		EvercamApiHelper.setEvercamDeveloperKeypair(this);
		scanTask = new ScanForCameraTask(ScanActivity.this);
		scanTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
	
	private void stopDiscovery()
	{
		if(scanTask != null && !scanTask.isCancelled())
		{
			scanTask.cancel(true);
		}
	}

	public void showProgress(boolean show)
	{
		scanProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	public void showCameraListView(boolean show)
	{
		scanResultListView.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	public void showNoCameraView(boolean show)
	{
		scanResultNoCameraView.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	public void showScanResults(ArrayList<DiscoveredCamera> discoveredCameras)
	{
		if (discoveredCameras != null)
		{
			showCameraListView(true);
			showNoCameraView(false);
			this.discoveredCameras = discoveredCameras;
			for (int index = 0; index < discoveredCameras.size(); index++)
			{
				DiscoveredCamera camera = discoveredCameras.get(index);
				HashMap<String, Object> deviceMap = new HashMap<String, Object>();
				String ipTextShowing = camera.getIP();
				if (camera.hasHTTP())
				{
					ipTextShowing = ipTextShowing + ":" + camera.getHttp();
				}
				deviceMap.put(ADAPTER_KEY_IP, ipTextShowing);

				String vendor = camera.getVendor().toUpperCase(Locale.UK);

				if (camera.hasModel())
				{
					String model = camera.getModel().toUpperCase(Locale.UK);
					if (model.startsWith(vendor))
					{
						deviceMap.put(ADAPTER_KEY_MODEL, model);
					}
					else
					{
						deviceMap.put(ADAPTER_KEY_MODEL, vendor + " " + model);
					}
				}
				else
				{
					deviceMap.put(ADAPTER_KEY_MODEL, vendor);
				}
				deviceArrayList.add(deviceMap);
				deviceAdapter.notifyDataSetChanged();

				new RetrieveThumbnailTask(vendor, camera.getModel(), index)
						.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
		}
		else
		{
			showCameraListView(false);
			showNoCameraView(true);
		}
	}

	private class ScanResultAdapter extends SimpleAdapter
	{
		public ScanResultAdapter(Context context, List<? extends Map<String, ?>> data,
				int resource, String[] from, int[] to)
		{
			super(context, data, resource, from, to);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			View superView = super.getView(position, convertView, parent);
			ImageView imageView = (ImageView) superView.findViewById(R.id.camera_img);
			ProgressBar progressBar = (ProgressBar) superView.findViewById(R.id.progress);

			Drawable drawable = drawableArray.get(position);
			if (drawable != null)
			{
				imageView.setVisibility(View.VISIBLE);
				progressBar.setVisibility(View.GONE);
				imageView.setImageDrawable(drawable);
			}
			return superView;
		}
	}

	private class RetrieveThumbnailTask extends AsyncTask<Void, Void, Drawable>
	{
		private String vendorId;
		private String modelId;
		private int position;

		public RetrieveThumbnailTask(String vendorId, String modelId, int position)
		{
			this.vendorId = vendorId.toLowerCase(Locale.UK);
			this.modelId = modelId.toLowerCase(Locale.UK);
			this.position = position;
		}

		@Override
		protected Drawable doInBackground(Void... params)
		{
			Drawable drawable = null;
			try
			{
				if (!modelId.isEmpty())
				{
					if (modelId.contains(vendorId))
					{
						modelId = modelId.replace(vendorId + " ", "");
					}
					CambaseModel model = new CambaseModel(modelId);
					ArrayList<String> thumnailUrls = model.getThumnailUrls();
					if (thumnailUrls.size() > 0)
					{
						String thumnailUrl = CambaseAPI.getSmallImageUrl(thumnailUrls.get(0));
						InputStream stream = Unirest.get(thumnailUrl).asBinary().getRawBody();

						drawable = Drawable.createFromStream(stream, "src");
					}
					else
					{
						Log.e(TAG, "Thumnail url size is 0 for model:" + modelId);
					}
				}

				if (drawable == null)
				{
					Manufacturer manufacturer = new Manufacturer(vendorId);
					String logoThumnail = CambaseAPI.getSmallImageUrl(manufacturer.getLogoUrl());
					InputStream stream = Unirest.get(logoThumnail).asBinary().getRawBody();

					drawable = Drawable.createFromStream(stream, "src");
				}
			}
			catch (UnirestException e)
			{
				Log.e(TAG, e.toString());
			}
			return drawable;
		}

		@Override
		protected void onPostExecute(Drawable drawable)
		{
			drawableArray.put(position, drawable);
			deviceAdapter.notifyDataSetChanged();
		}
	}
}

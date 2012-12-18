package com.danga.garagedoor;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;

public class GarageDoorActivity extends Activity {
	private static final String TAG = "GarageDoorActivity";

	private static final int MENU_OPEN = 2;
	private static final int MENU_WIFI_OFF = 3;
	private static final int MENU_JUST_SCAN = 4;
	private static final int MENU_SETTINGS = 5;
	private static final int MENU_HELP = 6;

	private TextView textView;
	private TextView scanResultTextView;

	private IntentFilter intentFilter;

	private Handler logHandler = new Handler() {
		public void handleMessage(Message m) {
			String logMessage = m.getData().getString("logmessage");
			textView.setText(logMessage + "\n" + textView.getText());
		}
	};

	private IGarageScanService scanServiceStub = null;

	private ServiceConnection serviceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			scanServiceStub = IGarageScanService.Stub.asInterface(service);
			log("Service bound");
			checkScanningState();
			try {
				scanServiceStub.registerCallback(garageCallback);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

		public void onServiceDisconnected(ComponentName name) {
			scanServiceStub = null;
		};
	};


	private IGarageScanCallback garageCallback = new IGarageScanCallback.Stub() {
		public void logToClient(String message) throws RemoteException {
			log(message);
		}

		public void onScanResults(String scanResults) throws RemoteException {
			scanResultTextView.setText(scanResults);
		}
	};

	private SharedPreferences getPrefs() {
		return getSharedPreferences(Preferences.NAME, 0);
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);
		scanResultTextView = (TextView) findViewById(R.id.scanresults);

		textView = (TextView) findViewById(R.id.textthing);

		Button startScan = (Button) findViewById(R.id.StartScan);
		startScan.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				startScanningService(false);
			}
		});

		Button stopScan = (Button) findViewById(R.id.StopScan);
		stopScan.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (scanServiceStub == null) {
					textView.setText("service stub is null");
					return;
				}
				try {
					scanServiceStub.setScanning(false);
					scanResultTextView.setText("");
					log("Stopped scanning.");
				} catch (RemoteException e) {
					log("Exception changing state: " + e);
				}
			}
		});

		intentFilter = new IntentFilter();
		intentFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
		intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
	}

	protected void startScanningService(boolean debugMode) {
		if (scanServiceStub == null) {
			log("Not bound to service.");
			return;
		}
		try {
			scanServiceStub.setDebugMode(debugMode);
			scanResultTextView.setText("");
			Intent startScanning = new Intent(this, InRangeService.class);
			startScanning.setAction(InRangeService.ACTION_START_SCANNING);
			startService(startScanning);
			if (debugMode) {
				log("Scan-only mode started.");
			} else {
				log("Scanning mod started.");
			}
		} catch (RemoteException e) {
			log("Exception changing state: " + e);
		}
	}

	/**
	 * Check if we're currently scanning and log a message about it.
	 */
	protected void checkScanningState() {
		if (scanServiceStub == null) {
			textView.setText("service stub is null");
		} else {
			boolean running;
			try {
				running = scanServiceStub.isScanning();
				String state = running ? "Scanning" : "NOT scanning";
				textView.setText(state + "\nURL: " + getPrefs().getString(Preferences.KEY_URL, "<no_url>"));
			} catch (RemoteException e) {
				textView.setText("Exception error checking scanning: " + e);
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		textView.setText("onResume");
		bindService(new Intent(this, InRangeService.class),
				serviceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onPause() {
		super.onPause();

		if (scanServiceStub != null) {
			try {
				scanServiceStub.unregisterCallback(garageCallback);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

		if (serviceConnection != null) {
			unbindService(serviceConnection);
		}
	}

	View.OnClickListener createWifiAPairer(final String ssid) {
		return new OnClickListener() {
			public void onClick(View v) {
				Settings.System.putInt(getContentResolver(), Settings.System.WIFI_USE_STATIC_IP, 0);

				WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
				List<WifiConfiguration> networks = wifiManager.getConfiguredNetworks();
				WifiConfiguration foundConfig = null;
				for (WifiConfiguration config : networks) {
					if (config.SSID.equals("\"" + ssid + "\"")) {
						foundConfig = config;
						break;
					}
					textView.setText(config.SSID + ", " + new Integer(config.SSID.length()));
				}
				if (foundConfig != null) {
					textView.setText(new StringBuilder().append(foundConfig.networkId));
					int n = 0;
					try {
						boolean success = wifiManager.enableNetwork(foundConfig.networkId, true);
						textView.setText(new Boolean(success).toString());
						if (success) {
							textView.setText("Connecting to: " + ssid);
						}
					} catch (Exception e) {
						textView.setText(e.toString() + ", " + n);
					}
				} else {
					textView.setText(ssid + " not found");
				}
			}
		};
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(Menu.NONE, MENU_WIFI_OFF, 0, "Wifi Off");
		menu.add(Menu.NONE, MENU_OPEN, 0, "Open Now");
		menu.add(Menu.NONE, MENU_JUST_SCAN, 0, "Just Scan");
		menu.add(Menu.NONE, MENU_HELP, 0, "Help");
		menu.add(Menu.NONE, MENU_SETTINGS, 0, "Settings");
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_OPEN:
			try {
				scanServiceStub.openGarageNow();
			} catch (RemoteException e) {
				log(e.toString());
			}
			break;
		case MENU_WIFI_OFF:
			wifi().disconnect();
			break;
		case MENU_JUST_SCAN:
			startScanningService(true);
			break;
		case MENU_SETTINGS:
			SettingsActivity.show(this);
			break;
		case MENU_HELP:
			final Intent intent = new Intent(Intent.ACTION_VIEW,
					Uri.parse("http://bradfitz.com/garage-opener/"));
			startActivity(intent);
			break;
		}
		return true;
	}

	private WifiManager wifi() {
		return  (WifiManager) getSystemService(Context.WIFI_SERVICE);
	}

	private void log(String message) {
		Message m = new Message();
		Bundle b = new Bundle();
		b.putString("logmessage", message);
		m.setData(b);
		logHandler.sendMessage(m);
	}
}
package com.danga.garagedoor;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;

public class InRangeService extends Service {

	public static final String EXTRA_KEY_OPEN_TYPE = "open_type";
	public static final String EXTRA_OPEN_TYPE_IF_IN_RANGE = "if_in_range";
	public static final String ACTION_START_SCANNING = "START_SCANNING";

	private static final String TAG = "InRangeService";
	private static final int NOTIFY_ID_SCANNING = 1;
	private static final int NOTIFY_ID_EVENT = 2;

	private AtomicBoolean isScanning = new AtomicBoolean(false);
	private AtomicBoolean scanTimerOutstanding = new AtomicBoolean(false);
	// If we've reached the state that we should open the door.  Set to false once we actually do.
	private AtomicBoolean shouldOpen = new AtomicBoolean(false);
	private AtomicBoolean httpRequestOustanding = new AtomicBoolean(false);

	// openImmediatelyBefore, if non-zero, specifies that we're in open-immediately-if-in-range
	// mode.  The long value is the time (unix millis) at which we should stop scanning.
	private AtomicLong openImmediatelyBefore = new AtomicLong(0);

	// A message loop handler to post runnables to in the future:
	private Handler handler = new Handler();

	private Vibrator vibrator;
	private ConnectivityManager connMan;
	private WifiManager.WifiLock wifiLock;
	private PowerManager.WakeLock cpuLock;

	private final RemoteCallbackList<IGarageScanCallback> callbackList = new RemoteCallbackList<IGarageScanCallback>();

	// We don't trust the first scan result (it seems to be old or cached sometimes), so instead
	// we wait for an out-of-range --> in-range transition.  This bool keeps track of whether or not
	// we've seen an out-of-range scan result since we started scanning.
	private AtomicBoolean outOfRangeScanReceived = new AtomicBoolean(false);

	// For debugging, this can be false to just do a constant scan without actually opening the garage.
	protected boolean debugMode = false;

	// Which SSID we're scanning for.  null if unset in preferences.
	private String mTargetSSID = null;

	// For listening to system updates of network & wifi connectivity state:
	private IntentFilter networkChangeIntents = new IntentFilter();
	private final BroadcastReceiver onNetworkChange = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			if (shouldOpen.get() && !httpRequestOustanding.get()) {
				// Maybe the first HTTP request failed, but now we have connectivity, so
				// we should try again.
				openGarage();
			}
		}
	};

	// For listening to wifi scanning update:
	private IntentFilter scanResultIntentFilter = new IntentFilter();
	private final BroadcastReceiver onScanResult = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			if (!intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
				Log.d("BOGUS ACTION", null);
				return;
			}
			if (!isScanning.get()) {
				// Canceled in the meantime.
				return;
			}

			if (debugMode) {
				vibrator.vibrate(50);
			}

			List<ScanResult> scanResults = wifi().getScanResults();
			Log.v(TAG, "Scan results.");
			int privateLevel = -999;
			int publicLevel = -999;
			StringBuilder sb = new StringBuilder();
			for (ScanResult ap : scanResults) {
				Log.v(TAG,
						"AP: " + ap.SSID + ", " + ap.capabilities + ", freq=" + ap.frequency + ", "
								+ "bssid=" + ap.BSSID + ", lev=" + ap.level);
				sb.append(ap.SSID + " == " + ap.level + "\n");

				if (ap.SSID.equals(mTargetSSID)) {
					publicLevel = ap.level;
					Log.v(TAG, "Public SSID level = " + ap.level);
				}
			}

			boolean inRange = (privateLevel != -999 || publicLevel != -999);
			if (!inRange) {
				outOfRangeScanReceived.set(true);
			}
			sendScanToClients(sb.toString());

			long oib = openImmediatelyBefore.get();
			if (oib != 0) {
				if (System.currentTimeMillis() > oib) {
					openImmediatelyBefore.set(0);
					handler.post(new Runnable() {
						public void run() {
							stopScanning();
						}
					});
					return;
				}
				if (inRange) {
					openImmediatelyBefore.set(0);
					stopScanningAndOpenGarage();
				}
			} else if (!debugMode && inRange && outOfRangeScanReceived.get()) {
				stopScanningAndOpenGarage();
			} else if (isScanning.get()) {
				// start scanning again in a second, if a timer's not already outstanding
				if (scanTimerOutstanding.compareAndSet(false, true)) {
					handler.postDelayed(new Runnable() {
						public void run() {
							scanTimerOutstanding.set(false);
							Log.d(TAG, "Starting a wifi scan...");
							wifi().startScan();
						}
					}, 1000);
				}
			}
		}
	};

	@Override
	public IBinder onBind(Intent arg0) {
		return scanService;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.d(TAG, "onUnbind: " + intent);
		return super.onUnbind(intent);
	}

	public void onDestroy() {
		stopScanning();
		callbackList.kill();  // unregister all callbacks
		super.onDestroy();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		wifiLock = wifi().createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY, "GarageWifiLock");
		vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		connMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

		networkChangeIntents.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		networkChangeIntents.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);

		scanResultIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Garage CPU lock");

		Log.d(TAG, "onCreate");
	}

	protected void stopScanningAndOpenGarage() {
		stopScanning();
		doNotification("Garage In Range", "Starting to open.");
		shouldOpen.set(true);
		openGarage();
		vibrator.vibrate(2000);  // 2 seconds
	}

	private void doNotification(String title, String text) {
		Notification n = new Notification(R.drawable.icon, title, System.currentTimeMillis());
		PendingIntent pIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, GarageDoorActivity.class), 0);
		n.setLatestEventInfo(this, title, text, pIntent);
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.notify(NOTIFY_ID_EVENT, n);		
	}

	private void notifyError(String string) {
		doNotification("Garage Error", string);
		logToClients(string);
	}

	private SharedPreferences getPrefs() {
		return getSharedPreferences(Preferences.NAME, 0);
	}

	// Returns true if the HTTP request was started.
	private boolean openGarage() {
		if (!shouldOpen.get() ) {
			Log.e(TAG, "openGarage() called but shouldOpen isn't true");
			stopMobileData();
			return false;      
		}
		if (!httpRequestOustanding.compareAndSet(false, true)) {
			Log.d(TAG, "Not opening garage door due to other outstanding HTTP request.");
			stopMobileData();
			return false;
		}
		final String urlBase = getPrefs().getString(Preferences.KEY_URL, null);
		if (urlBase == null) {
			Log.e(TAG, "No garage door URL configured.");
			return false;
		}

		final String secretKey = getPrefs().getString(Preferences.KEY_SECRET, "");

		// We already did this before, but it might not have set the set
		// the route, if it was still enabling the 3G connection.  We do it again
		// which is likely a no-op, but might cause the route to be created.
		forceMobileConnection();

		final HttpClient client = new DefaultHttpClient();
		Date now = new Date();
		long epochTime = now.getTime() / 1000;
		String url = urlBase + "?t=" + epochTime + "&key=" +  hmacSha1(""+epochTime, secretKey);

		Log.d(TAG, "Attempting open of: " + urlBase);
		logToClients("Sending HTTP request to " + url);

		final HttpUriRequest request = new HttpGet(url);
		Runnable httpRunnable = new Runnable() {
			public void run() {
				try {
					client.execute(request, new ResponseHandler<HttpResponse>() {
						public HttpResponse handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
							if (response.getStatusLine().getStatusCode() == 200) {
								doNotification("Garage Opened", "The garage door was opened.");
								logToClients("HTTP success.  Opened.");
								shouldOpen.set(false);  // done.
							} else {
								notifyError("HTTP error: " + response.toString());
							}
							httpRequestOustanding.set(false);
							return response;
						}

					});
				} catch (ClientProtocolException e) {
					notifyError("ClientProtocolException = " + e);
					e.printStackTrace();
					retryOpenGarageSoon();
				} catch (IOException e) {
					notifyError("IOException = " + e);
					e.printStackTrace();
					retryOpenGarageSoon();
				} finally {
					httpRequestOustanding.set(false);
					stopMobileData();
				}
			}
		};
		Thread httpThread = new Thread(httpRunnable);
		httpThread.start();
		return true;
	}

	protected void retryOpenGarageSoon() {
		Log.d(TAG, "Retrying garage door open in 1 second...");
		handler.postDelayed(new Runnable() {
			public void run() {
				openGarage();
			}
		}, 1000);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		openImmediatelyBefore.set(0);

		Log.d(TAG, "onStart; due to: intent=" + intent);
		boolean wantScan = false;
		if (intent != null) {
			if (ACTION_START_SCANNING.equals(intent.getAction())) {
				wantScan = true;
			}
			Log.d(TAG, "onStart; intentAction="+ intent.getAction());
	 		Bundle extras = intent.getExtras();
			Log.d(TAG, "onStart; intentExtras=" + extras);
			if (extras != null) {
				if (EXTRA_OPEN_TYPE_IF_IN_RANGE.equals(extras.getString(EXTRA_KEY_OPEN_TYPE))) {
					openImmediatelyBefore.set(System.currentTimeMillis() + 10000);
					wantScan = true;
				}
			}
		}

		if (wantScan) {
			startScanning();	
		} else {
			Log.d(TAG, "bullshit onStart; want to stop this service.");
			stopSelfResult(startId);
		}
		return START_STICKY;
	}

	private String getGarageIP() {
		final String urlBase = getPrefs().getString(Preferences.KEY_URL, null);
		if (urlBase == null) {
			return null;
		}
		if (!urlBase.toLowerCase().startsWith("http://")) {
			return null;
		}
		int slash = urlBase.indexOf("/", "http://".length());
		if (slash == -1) {
			return null;
		}
		String hostName = urlBase.substring("http://".length(), slash);
		Log.d(TAG, "hostname = " + hostName);
		if (hostName.length() == 0) {
			return null;
		}
		for (int i = 0; i < hostName.length(); i++) {
			char c = hostName.charAt(i);
			if (c != '.' && (c < '0' || c > '9')) {
				return null;
			}
		}
		return hostName;
	}

	private void forceMobileConnection() {
		String ipAddress = getGarageIP();
		Log.d(TAG, "IP to force to mobile: " + ipAddress);
		if (ipAddress == null) {
			// Note: just lazy and don't want to block here on a DNS lookup.
			// Assume the user has configured an IP address in the settings,
			// like I have.  (it's faster, avoiding a DNS lookup)
			Log.d(TAG, "Not forcing a mobile connection; URL doesn't have an IP hostname");
			return;
		}
		NetworkInfo.State state = connMan.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_HIPRI).getState();
		Log.d(TAG, "TYPE_MOBILE_HIPRI state = " + state);

		int res = connMan.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, "enableHIPRI");
		Log.d(TAG, "enableHIPRI = " + res);

		InetAddress inetAddress;
		try {
			inetAddress = InetAddress.getByName(ipAddress);
		} catch (UnknownHostException e) {
			return;
		}
		byte[] addrBytes = inetAddress.getAddress();
		int addr =  ((addrBytes[3] & 0xff) << 24)
				| ((addrBytes[2] & 0xff) << 16)
				| ((addrBytes[1] & 0xff) << 8 )
				|  (addrBytes[0] & 0xff);
		boolean reqRes = connMan.requestRouteToHost(ConnectivityManager.TYPE_MOBILE_HIPRI, addr);
		Log.d(TAG, "requestRouteToHost (" + addr + ") = " + reqRes);
	}

	private void startScanning() {
		Log.d(TAG, "startScanning()");
		if (!isScanning.compareAndSet(false, true)) {
			logToClients("Scanning already running.");
			return;
		}

		forceMobileConnection();

		mTargetSSID = getPrefs().getString(Preferences.KEY_SSID, null);
		Log.d(TAG, "Scanning for SSID: " + mTargetSSID);

		cpuLock.acquire();
		logToClients("Garage wifi scan starting.");

		outOfRangeScanReceived.set(false);
		shouldOpen.set(false);

		Toast.makeText(this, "Garage Scan Started", Toast.LENGTH_SHORT).show();

		Notification n = new Notification();
		n.icon = R.drawable.icon;
		n.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
		n.setLatestEventInfo(this, "Garage Scanning", "Garage door wifi scan is in progress.",
				PendingIntent.getActivity(this, 0,
						new Intent(this, GarageDoorActivity.class), 0));
		notificationManager().cancel(NOTIFY_ID_EVENT);
		notificationManager().notify(NOTIFY_ID_SCANNING, n);
		startForeground(NOTIFY_ID_SCANNING, n);
		
		wifiLock.acquire();
		registerReceiver(onScanResult, scanResultIntentFilter);
		wifi().startScan();
	}

	private NotificationManager notificationManager() {
		return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}

	private void stopScanning() {
		Log.d(TAG, "stopScanning()");
		shouldOpen.set(false);
		stopMobileData();
		if (!isScanning.compareAndSet(true, false)) {
			logToClients("Scanning was already stopped.");
			return;
		}
		cpuLock.release();
		logToClients("Stopping scanning.");
		wifiLock.release();
		unregisterReceiver(onScanResult);
		stopForeground(true);
		notificationManager().cancel(NOTIFY_ID_SCANNING);
		stopSelf();
	}

	private void stopMobileData() {
		connMan.stopUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, "enableHIPRI");
	}

	private synchronized void logToClients(String string) {
		// Broadcast to all clients the new value.
		final int N = callbackList.beginBroadcast();
		for (int i = 0; i < N; ++i) {
			try {
				callbackList.getBroadcastItem(i).logToClient(string);
			} catch (RemoteException e) {
				// The RemoteCallbackList will take care of removing
				// the dead object for us.
			}
		}
		callbackList.finishBroadcast();
	}

	private synchronized void sendScanToClients(String string) {
		// Broadcast to all clients the new value.
		final int N = callbackList.beginBroadcast();
		for (int i = 0; i < N; ++i) {
			try {
				callbackList.getBroadcastItem(i).onScanResults(string);
			} catch (RemoteException e) {
				// The RemoteCallbackList will take care of removing
				// the dead object for us.
			}
		}
		callbackList.finishBroadcast();
	}

	private WifiManager wifi() {
		return (WifiManager) getSystemService(Context.WIFI_SERVICE);
	}

	private static final char[] HEX_CHAR = {
		'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
	};

	public static String hmacSha1(String plainText, String keyString)
	{
		try {
			SecretKey key = new SecretKeySpec(keyString.getBytes(), "HmacSHA1");
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(key);
			mac.update(plainText.getBytes());
			byte[] digest = mac.doFinal();
			char[] hexDigest = new char[40];
			for (int i = 0; i < 20; ++i) {
				int byteValue = 0xFF & digest[i];  // signed to unsigned.  Java, man.
				hexDigest[i * 2] = HEX_CHAR[byteValue >> 4];
				hexDigest[i * 2 + 1] = HEX_CHAR[byteValue & 0xf];
			}
			return new String(hexDigest);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private final IGarageScanService.Stub scanService = new IGarageScanService.Stub() {

		public boolean isScanning() throws RemoteException {
			return isScanning.get();
		}

		public void setScanning(boolean isEnabled) throws RemoteException {
			if (isEnabled) {
				startScanning();
			} else {
				stopScanning();
			}
		}

		public void registerCallback(IGarageScanCallback callback)
				throws RemoteException {
			if (callback != null) {
				callbackList.register(callback);
			}
		}

		public void unregisterCallback(IGarageScanCallback callback)
				throws RemoteException {
			if (callback != null) {
				callbackList.unregister(callback);
			}
		}

		public void setDebugMode(boolean debugMode) throws RemoteException {
			InRangeService.this.debugMode = debugMode;
		}

		public void openGarageNow() throws RemoteException {
			shouldOpen.set(true);
			if (openGarage()) {
				logToClients("HTTP request sent to open garage.");
			} else {
				logToClients("Didn't send garage open request.");
			}
		}
	};
}

package com.danga.garagedoor;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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

  private static final String TAG = "InRangeService";
  private static final int NOTIFY_ID_SCANNING = 1;
  private static final int NOTIFY_ID_EVENT = 2;

  private AtomicBoolean isScanning = new AtomicBoolean(false);
  private AtomicBoolean scanTimerOutstanding = new AtomicBoolean(false);
  // If we've reached the state that we should open the door.  Set to false once we actually do.
  private AtomicBoolean shouldOpen = new AtomicBoolean(false);
  private AtomicBoolean httpRequestOustanding = new AtomicBoolean(false);

  // A message loop handler to post runnables to in the future:
  private Handler handler = new Handler();

  private Vibrator vibrator;
  private WifiManager.WifiLock wifiLock;
  private PowerManager.WakeLock cpuLock;
  
  private final RemoteCallbackList<IGarageScanCallback> callbackList = new RemoteCallbackList<IGarageScanCallback>();
 
  // We don't trust the first scan result (it seems to be old or cached sometimes), so instead
  // we wait for an out-of-range --> in-range transition.  This bool keeps track of whether or not
  // we've seen an out-of-range scan result since we started scanning.
  private AtomicBoolean outOfRangeScanReceived = new AtomicBoolean(false);
  
  // For debugging, this can be false to just do a constant scan without actually opening the garage.
  protected boolean debugMode = false;
    
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
        if (ap.SSID.equals("FitzPublic")) {
          publicLevel = ap.level;
        } else if (ap.SSID.equals("FitzPrivate")) {
          privateLevel = ap.level;
        }
      }

      boolean inRange = (privateLevel != -999 || publicLevel != -999);
      if (!inRange) {
        outOfRangeScanReceived.set(true);
      }
      sendScanToClients(sb.toString());

      if (!debugMode && inRange && outOfRangeScanReceived.get()) {
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

  public void onDestroy() {
    stopScanning();
    callbackList.kill();  // unregister all callbacks
    super.onDestroy();
  }
  
  @Override
  public void onCreate() {
    super.onCreate();
    wifiLock = wifi().createWifiLock("GarageWifiLock");
    vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    
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

  // Returns true if the HTTP request was started.
  private boolean openGarage() {
    if (!shouldOpen.get() ) {
      Log.e(TAG, "openGarage() called but shouldOpen isn't true");
      return false;      
    }
    if (!httpRequestOustanding.compareAndSet(false, true)) {
      Log.d(TAG, "Not opening garage door due to other outstanding HTTP request.");
      return false;
    }
    final String urlBase = getString(R.string.garage_url);
    final HttpClient client = new DefaultHttpClient();
    Date now = new Date();
    long epochTime = now.getTime() / 1000;
    String url = urlBase + "?t=" + epochTime + "&key=" +  hmacSha1(""+epochTime, getString(R.string.shared_key)); 
    
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
  public void onStart(Intent intent, int startId) {
    super.onStart(intent, startId);
    Log.d(TAG, "onStart");
    startScanning();
  }

  private void startScanning() {
    Log.d(TAG, "startScanning()");
    if (!isScanning.compareAndSet(false, true)) {
      logToClients("Scanning already running.");
      return;
    }
    
    cpuLock.acquire();
    logToClients("Garage wifi scan starting.");

    outOfRangeScanReceived.set(false);
    shouldOpen.set(false);

    setForeground(true);  // don't swap this out.
    
    Toast.makeText(this, "Garage Scan Started", Toast.LENGTH_SHORT).show();
    
    Notification n = new Notification();
    n.icon = R.drawable.icon;
    n.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
    n.setLatestEventInfo(this, "Garage Scanning", "Garage door wifi scan is in progress.",
        PendingIntent.getActivity(this, 0,
        new Intent(this, GarageDoorActivity.class), 0));
    notificationManager().cancel(NOTIFY_ID_EVENT);
    notificationManager().notify(NOTIFY_ID_SCANNING, n);
  
    wifiLock.acquire();
    registerReceiver(onScanResult, scanResultIntentFilter);
    wifi().startScan();
  }
  
  private NotificationManager notificationManager() {
    return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
  }
  
  private void stopScanning() {
    Log.d(TAG, "stopScanning()");
    if (!isScanning.compareAndSet(true, false)) {
      logToClients("Scanning was already stopped.");
      return;
    }
    cpuLock.release();
    setForeground(false);
    logToClients("Stopping scanning.");
    wifiLock.release();
    unregisterReceiver(onScanResult);
    notificationManager().cancel(NOTIFY_ID_SCANNING);
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

package com.danga.garagedoor;

import com.danga.garagedoor.IGarageScanCallback;

interface IGarageScanService {
	boolean isScanning();
	void setDebugMode(boolean debugMode);  // scan forever, never opening the door
	void setScanning(boolean isEnabled);
	void registerCallback(IGarageScanCallback callback);
	void unregisterCallback(IGarageScanCallback callback);
	void openGarageNow();  // force open mode.
}

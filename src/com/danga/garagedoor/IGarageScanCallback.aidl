package com.danga.garagedoor;

oneway interface IGarageScanCallback {
	void logToClient(String stuff);
	void onScanResults(String scanResults);
}

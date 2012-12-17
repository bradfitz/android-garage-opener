package com.danga.garagedoor;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

public class GarageOpenAppWidgetProvider extends AppWidgetProvider {

	private static final String TAG = "GarageWidget";
	
	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		super.onDeleted(context, appWidgetIds);
		Log.d(TAG, "onDeleted");
	}

	@Override
	public void onEnabled(Context context) {
		super.onEnabled(context);
		Log.d(TAG, "onEnabled");
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		super.onReceive(context, intent);
		Log.d(TAG, "onReceive: " + intent);
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
		super.onUpdate(context, appWidgetManager, appWidgetIds);
		Log.d(TAG, "onUpdate");
		final int n = appWidgetIds.length;
		for (int i = 0; i < n; i++) {
			int appWidgetId = appWidgetIds[i];
			RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
			
			Intent intent = new Intent(context, getClass());
			intent.setAction("OPEN_GARAGE_NOW");
			views.setOnClickPendingIntent(R.id.btn_widget_open, PendingIntent.getBroadcast(context, 0, intent, 0));
			
			appWidgetManager.updateAppWidget(appWidgetId, views);
		}
	}

}

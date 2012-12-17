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
	
	private static final String ACTION_OPEN_NOW = "OPEN_GARAGE_NOW";

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
		if (ACTION_OPEN_NOW.equals(intent.getAction())) {
			Intent in = new Intent(context, InRangeService.class);
			in.putExtra(InRangeService.EXTRA_KEY_OPEN_TYPE, InRangeService.EXTRA_OPEN_TYPE_IF_IN_RANGE);
			context.startService(in);
		}
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
			intent.setAction(ACTION_OPEN_NOW);
			views.setOnClickPendingIntent(R.id.btn_widget_open, PendingIntent.getBroadcast(context, 0, intent, 0));
			
			appWidgetManager.updateAppWidget(appWidgetId, views);
		}
	}

}

package com.example.android.sunshine.app.sync;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.app.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class SunshineSyncService extends Service implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final Object sSyncAdapterLock = new Object();
    private static SunshineSyncAdapter sSunshineSyncAdapter = null;
    private static final String watchDataReq = "/getWeather";
    GoogleApiClient mGoogleApiClient;
    /*GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineSyncService.this)
            .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks(){
                @Override
                public void onConnected(@Nullable Bundle connectionHint) {
                    Log.d("LOG TAG", "onConnected: " + connectionHint);
                    Log.v("LOG TAG", "WE'RE CONNECTED!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    Wearable.DataApi.addListener(mGoogleApiClient, SunshineSyncService.this);
                }

                @Override
                public void onConnectionSuspended(int i) {
                    Log.d("LOG TAG", "onConnectionSuspended: " + i);
                }
            })
            .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener(){
                @Override
                public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                    Log.d("LOG TAG", "onConnectionFailed: " + connectionResult);
                }
            })
            .addApi(Wearable.API)
            .build();*/


    @Override
    public void onCreate() {
        Log.d("SunshineSyncService", "onCreate - SunshineSyncService");
        synchronized (sSyncAdapterLock) {
            if (sSunshineSyncAdapter == null) {
                sSunshineSyncAdapter = new SunshineSyncAdapter(getApplicationContext(), true);
            }
            mGoogleApiClient = new GoogleApiClient.Builder(SunshineSyncService.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();
        }

    }

    @Override
    public void onDestroy(){
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSunshineSyncAdapter.getSyncAdapterBinder();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.v("LOG TAG", "DATA EVENT IN THE SERVICE!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                DataItem item = event.getDataItem();
                Log.v("LOG TAG", "THE PATH IN THE SERVICE IS: " + item.getUri().getPath());
                if (item.getUri().getPath().compareTo(watchDataReq) == 0) {
                    Log.v("LOG TAG", "WE'RE IN BUSINESS!!!!!!!!!!!!!!!!!");
                }
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                // DataItem deleted
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
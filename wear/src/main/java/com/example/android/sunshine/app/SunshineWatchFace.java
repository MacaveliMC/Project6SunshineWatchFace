/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.example.android.sunshine.app.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mDatePaint;
        Paint mHighPaint;
        Paint mLowPaint;
        boolean mAmbient;
        Calendar mCalendar;
        Date mDate;
        Bitmap weatherIcon;

        String highString;
        String lowString;
        int weatherId = 9999;

        private static final String watchDataPath = "/todayWeather";
        private static final String DATA_HIGH_KEY = "com.watchface.key.high";
        private static final String DATA_LOW_KEY = "com.watchface.key.low";
        private static final String DATA_WEATHER_KEY = "com.watchface.key.weather";


        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        Log.v("LOG TAG", "CONNECTED");
                        Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
                    }

                    @Override
                    public void onConnectionSuspended(int i) {

                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

                    }
                })
                .addApi(Wearable.API)
                .build();


        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());

            }
        };
        int mTapCount;

        float mXOffset;
        float mYOffset;
        float mDateXoffset;
        float mDateYoffset;
        float mDateTextSize;
        float mHighXoffset;
        float mHighYoffset;
        float mLowXoffset;
        float mLowYoffset;
        float mHighTextSize;
        float mLowTextSize;
        float mWeatherIdXoffset;
        float mWeatherIdYoffset;


        SimpleDateFormat dateFormat;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mDateYoffset = resources.getDimension(R.dimen.date_y_offset);
            mLowYoffset = resources.getDimension(R.dimen.low_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.date_text));

            mHighPaint = new Paint();
            mHighPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mLowPaint = new Paint();
            mLowPaint = createTextPaint(resources.getColor(R.color.date_text));

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            dateFormat = new SimpleDateFormat("EEE, MMM dd yyyy");

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setStrokeCap(Paint.Cap.ROUND);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                Log.v("LOG TAG", "BECAME VISIBLE");
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mDateXoffset = resources.getDimension(isRound
                    ? R.dimen.date_x_offset_round : R.dimen.date_x_offset);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.date_text_size_round : R.dimen.date_text_size);

            mHighXoffset = resources.getDimension(isRound
                    ? R.dimen.high_x_offset_round : R.dimen.high_x_offset);
            mHighYoffset = resources.getDimension(isRound
                    ? R.dimen.high_y_offset_round: R.dimen.high_y_offset);
            mHighTextSize = resources.getDimension(isRound
                    ? R.dimen.high_text_size_round : R.dimen.high_text_size);

            mLowXoffset = resources.getDimension(isRound
                    ? R.dimen.low_x_offset_round : R.dimen.low_x_offset);
            mLowTextSize = resources.getDimension(isRound
                    ? R.dimen.low_text_size_round : R.dimen.low_text_size);

            mWeatherIdXoffset = resources.getDimension(isRound
                    ? R.dimen.weather_icon_x_offset_round : R.dimen.weather_icon_x_offset);
            mWeatherIdYoffset = resources.getDimension(isRound
                    ? R.dimen.weather_icon_y_offset_round : R.dimen.weather_icon_y_offset);

            mTextPaint.setTextSize(textSize);
            mDatePaint.setTextSize(dateTextSize);
            mHighPaint.setTextSize(mHighTextSize);
            mLowPaint.setTextSize(mLowTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SunshineWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    Log.v("LOG TAG", "USER COMPLETED A TAP!!!!!!!!!!!!!!");
                    /*PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/todayWeather");
                    putDataMapRequest.getDataMap().putString("MYKEY", "MY STRING");
                    putDataMapRequest.setUrgent();
                    PutDataRequest putDataReq = putDataMapRequest.asPutDataRequest();
                    PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);*/

                    mTapCount++;
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            int hourText;
            int minuteText;
            int secondText;
            String timeText;
            String dateText;

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Set all times to now, and check 24hr mode
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            boolean is24Hour = DateFormat.is24HourFormat(SunshineWatchFace.this);

            // Get hour text
            if (is24Hour) {
                hourText = mCalendar.get(Calendar.HOUR_OF_DAY);
            } else {
                hourText = mCalendar.get(Calendar.HOUR);
                if (hourText == 0) {
                    hourText = 12;
                }
            }

            // Get minute text
            minuteText = mCalendar.get(Calendar.MINUTE);

            // Get time string based on ambient mode
            if (mAmbient) {
                timeText = String.format("%d:%02d", hourText, minuteText);
            } else {
                secondText = mCalendar.get(Calendar.SECOND);
                timeText = String.format("%d:%02d:%02d", hourText, minuteText, secondText);
            }

            // Get date text
            dateText = dateFormat.format(mDate);

            // Get x offset for time
            mXOffset = (bounds.width() - mTextPaint.measureText(timeText)) / 2;
            mDateXoffset = (bounds.width() - mDatePaint.measureText(dateText.toUpperCase())) / 2;


            // Draw time and date
            canvas.drawText(dateText.toUpperCase(), mDateXoffset, mDateYoffset, mDatePaint);
            canvas.drawText(timeText, mXOffset, mYOffset, mTextPaint);

            // Draw mid line
            float lineXstart = bounds.width() / 4;
            float lineXstop = lineXstart * 3;
            float lineY = mDateYoffset + 20;

            canvas.drawLine(lineXstart, lineY, lineXstop, lineY, mDatePaint);

            if (highString != null && lowString != null && weatherId != 0) {
                weatherIcon = BitmapFactory.decodeResource(getResources(), weatherId);
                canvas.drawText(highString, mHighXoffset, mHighYoffset, mHighPaint);
                canvas.drawText(lowString, mLowXoffset, mLowYoffset, mLowPaint);
                canvas.drawBitmap(weatherIcon, mWeatherIdXoffset, mWeatherIdYoffset, null);
            } else {
                requestData();
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }


        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.v("LOG TAG", "DATA CHANGED!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    // DataItem changed
                    DataItem item = event.getDataItem();
                    Log.v("LOG TAG", "ITEM PATH: " + item.getUri().getPath());
                    if (item.getUri().getPath().compareTo(watchDataPath) == 0) {
                        Log.v("LOG TAG", "got here!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        highString = dataMap.getString(DATA_HIGH_KEY);
                        lowString = dataMap.getString(DATA_LOW_KEY);
                        weatherId = Utility.getIconResourceForWeatherCondition(dataMap.getInt(DATA_WEATHER_KEY));
                        Log.v("LOG TAG", "WEATHER DATA SENT - HIGH TEMP: " + highString + " LOW TEMP: " + lowString + " WEATHER ID: " + weatherId);
                    }
                } else if (event.getType() == DataEvent.TYPE_DELETED) {
                    // DataItem deleted
                }
            }
        }

        public void requestData() {
            Log.v("LOG TAG", "REQUESTING DATA FROM HANDHELD!!!!!!!!!!!!!!!!");
            PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/getWeather");
            putDataMapReq.setUrgent();
            putDataMapReq.getDataMap().putLong("KEY", System.currentTimeMillis());
            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
            PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
        }


    }
}

package com.greentaperacing.racelog;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

public class HeadingCalibration extends Activity implements SensorEventListener, LocationListener {

	private SensorManager sm;
	private LocationManager lm;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		sm.registerListener(this, sm.getSensorList(Sensor.TYPE_MAGNETIC_FIELD).get(0),
			SensorManager.SENSOR_DELAY_FASTEST);

		lm = (LocationManager) getSystemService(LOCATION_SERVICE);
		lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, Float.MIN_VALUE, this);

	}

	@Override
	public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
	}

	@Override
	public void onSensorChanged(final SensorEvent event) {
	}

	@Override
	public void onLocationChanged(final Location location) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onProviderDisabled(final String provider) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onProviderEnabled(final String provider) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStatusChanged(final String provider, final int status, final Bundle extras) {
		// TODO Auto-generated method stub

	}
}

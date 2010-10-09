package com.greentaperacing.racelog;

import java.util.List;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

public class RaceLog extends Activity implements SensorEventListener, LocationListener {
	private static final int GPS_FORCE_UPDATE_RATE = 2000;
	private final Handler h = new Handler();
	private SensorManager sm;
	private LocationManager lm;

	// variableCamelCase_of_expressedIn
	// veh == vehicle
	// loc == local NE(D)
	private final double[] cbeVelocity_veh_loc = { Double.NaN, Double.NaN };

	private long acctime = 0;

	private final SensorData sd = new SensorData();
	private Location last_fix;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		sm.registerListener(this, sm.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0),
			SensorManager.SENSOR_DELAY_FASTEST);
		List<Sensor> gyros;
		if ((gyros = sm.getSensorList(Sensor.TYPE_GYROSCOPE)).size() != 0) {
			sm.registerListener(this, gyros.get(0), SensorManager.SENSOR_DELAY_FASTEST);
		}
		sm.registerListener(this, sm.getSensorList(Sensor.TYPE_MAGNETIC_FIELD).get(0),
			SensorManager.SENSOR_DELAY_FASTEST);

		lm = (LocationManager) getSystemService(LOCATION_SERVICE);
		lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, Float.MIN_VALUE, this);

		h.removeCallbacks(forceStateUpdate);
		h.post(forceStateUpdate);
	}

	// ~ Assume planar motion
	// ~ Determine "down" vector in sensor frame (from accelerometer calibration)
	// Also need to calibrate nominal deviation of heading from velocity w/no sideslip
	// Use TRIAD to determine R(sensor->vehicle) from two cals above

	// (~ Use GPS position and velocity as baseline from .getBearing() and .getSpeed())

	// ~ Each mag sensor update:
	// Using R(sensor->vehicle), perform math to get heading in vehicle X-Y plane

	// ~ Each accel update:
	// ~ Acquire PV mutex
	// ~ Update velocity vector
	// ~ Update position
	// ~ Release PV mutex

	// ~ Each GPS update:
	// ~ We know the start and end position and velocity vectors
	// ~ Reset PV for accel updates to endpoint PV (for async)
	// ~ Initial velocity and position are pinned ("segment origin")
	// ~ Possible inertial solution repair mechanisms:
	// ~ * Find the scaling of the trajectory about the segment origin which solves a least-squares
	// problem to fit the end conditions?
	// ~ * ???
	// ~ Reprocess/interpolate the data (position, time, velocity, heading = 6 states) with inertial
	// data repaired:
	// ~ * New trajectory evenly spaced in time
	// * New trajectory a spline fit

	public synchronized void onLocationChanged(final Location location) {
		last_fix = location;
		h.removeCallbacks(forceStateUpdate);
		h.post(forceStateUpdate);
	}

	private synchronized void updateState() {
		if (last_fix == null) {
			return;
		}
		((TextView) findViewById(R.id.nmea)).setText("accur:"
			+ Double.toString(last_fix.getAccuracy()));
		((TextView) findViewById(R.id.lat)).setText("lat:"
			+ Double.toString(last_fix.getLatitude()));
		((TextView) findViewById(R.id.lon)).setText("lon:"
			+ Double.toString(last_fix.getLongitude()));
		((TextView) findViewById(R.id.alt)).setText("alt:"
			+ Double.toString(last_fix.getAltitude()));
		sd.gps_lla[0] = last_fix.getLatitude();
		sd.gps_lla[1] = last_fix.getLongitude();
		sd.gps_lla[2] = last_fix.getAltitude();
		final float speed = last_fix.getSpeed();
		Log.d("RaceLog", "Speed: " + Float.toString(speed));
		final double bearing = last_fix.getBearing() * Math.PI / 180;
		acctime = System.nanoTime();
		cbeVelocity_veh_loc[0] = speed * Math.sin(bearing); // North component
		cbeVelocity_veh_loc[1] = speed * Math.cos(bearing); // East component
		// location.getTime();
	}

	private final Runnable forceStateUpdate = new Runnable() {
		@Override
		public void run() {
			updateState();
			h.postDelayed(forceStateUpdate, GPS_FORCE_UPDATE_RATE);
		}
	};

	@Override
	public void onProviderDisabled(final String provider) {
	}

	@Override
	public void onProviderEnabled(final String provider) {
	}

	@Override
	public void onStatusChanged(final String provider, final int status, final Bundle extras) {
	}

	@Override
	public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void onSensorChanged(final SensorEvent event) {
		// TODO Auto-generated method stub
		sd.setSensor(event.sensor.getType(), event.values);
		((TextView) findViewById(R.id.accZ)).setText("velZ:" + Float.toString(sd.accvel[2]));
		((TextView) findViewById(R.id.magX)).setText("magX:" + Float.toString(sd.mag[0]));
		((TextView) findViewById(R.id.magY)).setText("magY:" + Float.toString(sd.mag[1]));
		((TextView) findViewById(R.id.magZ)).setText("magZ:" + Float.toString(sd.mag[2]));
		if (Float.isNaN(sd.gyro[0])) {
			/*
			 * We have no gyro data, so make some assumptions:
			 * Assume the vehicle is upright and operating in a plane with sensor X down; this
			 * means we can just straight up ignore X; Y is "right" and Z out of the phone
			 * Short term test, won't really work right
			 */
			if (Double.isNaN(cbeVelocity_veh_loc[0])) {
				return;
			}
			final long newtime = System.nanoTime();
			for (int i = 0; i < 2; i++) {
				cbeVelocity_veh_loc[i] = (float) (cbeVelocity_veh_loc[i] + sd.accel[i]
					* (newtime - acctime) / 1e9);
			}
			acctime = newtime;
			((TextView) findViewById(R.id.accX)).setText("velX:"
				+ Double.toString(cbeVelocity_veh_loc[0]));
			((TextView) findViewById(R.id.accY)).setText("velY:"
				+ Double.toString(cbeVelocity_veh_loc[1]));
		}
	}

	private class SensorData {
		float[] accel = { Float.NaN, Float.NaN, Float.NaN };
		float[] gyro = { Float.NaN, Float.NaN, Float.NaN };
		float[] mag = { Float.NaN, Float.NaN, Float.NaN };
		double[] gps_lla = { Float.NaN, Float.NaN, Float.NaN };

		float[] accvel = { Float.NaN, Float.NaN, Float.NaN };

		public void setSensor(final int type, final float[] values) {
			if (type == Sensor.TYPE_ACCELEROMETER) {
				accel = values;
			} else if (type == Sensor.TYPE_GYROSCOPE) {
				gyro = values;
			} else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
				mag = values;
			}
		}

	}
}

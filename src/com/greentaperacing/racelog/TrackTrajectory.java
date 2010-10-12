package com.greentaperacing.racelog;

import java.util.List;

import android.app.Activity;
import android.content.SharedPreferences;
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

public class TrackTrajectory extends Activity implements SensorEventListener, LocationListener {

	private static final int GPS_FORCE_UPDATE_RATE = 1000;
	private final Handler h = new Handler();
	private SensorManager sm;
	private LocationManager lm;

	// variableCamelCase_of_wrt_expressedIn
	// veh == vehicle
	// sen == sensor
	// sen3 == raw sensor (3D, appears in expressedIn only)
	// loc == local NE(D); sufficiently inertial for gyro
	// angular vars are variableCamel_Case_from2to
	// Derivatives prefixed by D (like Dpsi for dheading/dt, DDpsi d^2heading/dt^2...)

	private long acctime = 0;

	// Filled in GPS callback; used in the force-update mode
	private Location last_fix;

	private float[] gyro_sen_loc_sen3 = { Float.NaN, Float.NaN, Float.NaN };

	private float[] accel_sen_loc_veh = { Float.NaN, Float.NaN };
	private float[] accel_veh_loc_loc = { Float.NaN, Float.NaN };
	private final float[] accel_veh_loc_veh = { Float.NaN, Float.NaN };
	private float[] mag_sen_loc_veh = { Float.NaN, Float.NaN };
	private final double[] cbeVelocity_veh_loc_loc = { Double.NaN, Double.NaN };

	/*
	 * CALIBRATION CONSTANTS
	 */
	// Calibration lever arm for the sensor
	private final float[] r_sen_veh_veh = { 0.0F, 0.0F };

	// Calibration mount angle for the sensor - get w/straight-line drive
	private float psi_sen2veh = 0.0F;

	// Calibration normal to the ground plane - get by sitting still for a bit
	private final float[] gpNormal_sen_veh_sen3 = { 0.0F, 0.0F, 0.0F };
	// Arbitrary ground plane vectors; will be set in computeGroundPlane
	private final float[] gpX_sen3 = { 1.0F, 0.0F, 0.0F };
	private final float[] gpY_sen3 = { 0.0F, 1.0F, 0.0F };

	double[] gps_lla = { Float.NaN, Float.NaN, Float.NaN };

	private float psi_loc2veh = 0.0F;
	private final float Dpsi_loc2veh = 0.0F;
	private final float DDpsi_loc2veh = 0.0F;

	float[] accvel = { Float.NaN, Float.NaN, Float.NaN };

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.track_trajectory);
		sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		lm = (LocationManager) getSystemService(LOCATION_SERVICE);

		final SharedPreferences sp = getSharedPreferences(RaceLog.CALIBRATION_PREFS, 0);
		gpNormal_sen_veh_sen3[0] = sp.getFloat("gpNormal_sen_veh_sen3/0", Float.NaN);
		gpNormal_sen_veh_sen3[1] = sp.getFloat("gpNormal_sen_veh_sen3/1", Float.NaN);
		gpNormal_sen_veh_sen3[2] = sp.getFloat("gpNormal_sen_veh_sen3/2", Float.NaN);
		gpX_sen3[0] = sp.getFloat("gpX_sen3/0", Float.NaN);
		gpX_sen3[1] = sp.getFloat("gpX_sen3/1", Float.NaN);
		gpX_sen3[2] = sp.getFloat("gpX_sen3/2", Float.NaN);
		gpY_sen3[0] = sp.getFloat("gpY_sen3/0", Float.NaN);
		gpY_sen3[1] = sp.getFloat("gpY_sen3/1", Float.NaN);
		gpY_sen3[2] = sp.getFloat("gpY_sen3/2", Float.NaN);

		// TODO 0.0F is not a good default; once cal in place use Float.NaN
		psi_sen2veh = sp.getFloat("psi_sen2veh", 0.0F);

		propagateState();
	}

	private void propagateState() {
		sm.registerListener(this, sm.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0),
			SensorManager.SENSOR_DELAY_FASTEST);
		List<Sensor> gyros;
		if ((gyros = sm.getSensorList(Sensor.TYPE_GYROSCOPE)).size() != 0) {
			sm.registerListener(this, gyros.get(0), SensorManager.SENSOR_DELAY_FASTEST);
		}
		sm.registerListener(this, sm.getSensorList(Sensor.TYPE_MAGNETIC_FIELD).get(0),
			SensorManager.SENSOR_DELAY_FASTEST);

		lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, Float.MIN_VALUE, this);

		h.removeCallbacks(periodicStateUpdate);
	}

	private float[] projectFromSen3ToSen(final float[] v) {
		final float[] w = { Float.NaN, Float.NaN };
		w[0] = (float) VectorFun.dot(v, gpX_sen3);
		w[1] = (float) VectorFun.dot(v, gpY_sen3);
		return w;
	}

	// ~ Assume planar motion
	// ~ Determine "down" vector in sensor frame (from accelerometer calibration)
	// Also need to calibrate nominal deviation of heading from velocity w/no sideslip
	// Use TRIAD to determine R(sensor->vehicle) from two cals above

	// (~ Use GPS position and velocity as baseline from .getBearing() and .getSpeed())

	// ~ Each mag sensor update:
	// Using R(sensor->vehicle), perform math to get heading in vehicle X-Y plane

	// ~ Each accel_sen_sen update:
	// ~ Update velocity vector
	// ~ Update position

	// ~ Each GPS update:
	// ~ We know the start and end position and velocity vectors
	// ~ Reset PV for accel_sen_sen updates to endpoint PV (for async)
	// ~ Initial velocity and position are pinned ("segment origin")
	// ~ Possible inertial solution repair mechanisms:
	// ~ * Find the scaling of the trajectory about the segment origin which solves a least-squares
	// problem to fit the end conditions?
	// ~ * ???
	// ~ Reprocess/interpolate the data (position, time, velocity, heading = 6 states) with inertial
	// data repaired:
	// ~ * New trajectory evenly spaced in time
	// * New trajectory a spline fit
	@Override
	public synchronized void onLocationChanged(final Location location) {
		last_fix = location;
		h.removeCallbacks(periodicStateUpdate);
		h.post(periodicStateUpdate);
	}

	private synchronized void updateState() {
		if (last_fix == null) {
			return;
		}
		((TextView) findViewById(R.id.accur)).setText("accur:"
			+ Double.toString(last_fix.getAccuracy()));
		((TextView) findViewById(R.id.lat)).setText("lat:"
			+ Double.toString(last_fix.getLatitude()));
		((TextView) findViewById(R.id.lon)).setText("lon:"
			+ Double.toString(last_fix.getLongitude()));
		((TextView) findViewById(R.id.alt)).setText("alt:"
			+ Double.toString(last_fix.getAltitude()));
		gps_lla[0] = last_fix.getLatitude();
		gps_lla[1] = last_fix.getLongitude();
		gps_lla[2] = last_fix.getAltitude();
		final float speed = last_fix.getSpeed();
		Log.d("RaceLog", "Speed: " + Float.toString(speed));
		final double bearing_loc = last_fix.getBearing() * Math.PI / 180;
		acctime = System.nanoTime();
		cbeVelocity_veh_loc_loc[0] = speed * Math.sin(bearing_loc); // North component
		cbeVelocity_veh_loc_loc[1] = speed * Math.cos(bearing_loc); // East component
		// location.getTime();
	}

	private final Runnable periodicStateUpdate = new Runnable() {
		@Override
		public void run() {
			updateState();
			h.postDelayed(periodicStateUpdate, GPS_FORCE_UPDATE_RATE);
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
	}

	@Override
	public synchronized void onSensorChanged(final SensorEvent event) {
		// Dispatch the field update
		switch (event.sensor.getType()) {
		case Sensor.TYPE_ACCELEROMETER:
			accel_sen_loc_veh = VectorFun.rotate2(projectFromSen3ToSen(event.values), psi_sen2veh);
			break;
		case Sensor.TYPE_GYROSCOPE:
			gyro_sen_loc_sen3 = event.values;
			break;
		case Sensor.TYPE_MAGNETIC_FIELD:
			// Project mag readings onto ground plane
			mag_sen_loc_veh = VectorFun.rotate2(projectFromSen3ToSen(event.values), psi_sen2veh);
			// Determine new psi_loc2sen, see Honeywell AN203, "Compass Heading using Magnetometers"
			psi_loc2veh = (float) Math.atan2(mag_sen_loc_veh[0], mag_sen_loc_veh[1]);
			// Update Dpsi_loc2veh and DDpsi_loc2veh
			break;
		default:
			// If it's not a sensor we care about, don't update state
			return;
		}

		final long newtime = System.nanoTime();

		if (Float.isNaN(gyro_sen_loc_sen3[0])) {
			/*
			 * We have no gyro data, so make some assumptions:
			 * Assume the vehicle is upright and operating in a plane
			 * 
			 * Assume Dpsi_veh2sen == Dpsi_sen2vel == 0
			 */

			// Don't start integrating until the first fix is approved by the GPS callback
			if (Double.isNaN(cbeVelocity_veh_loc_loc[0])) {
				return;
			}

			// Transform sensor acceleration to vehicle
			accel_veh_loc_veh[0] = accel_sen_loc_veh[0] - DDpsi_loc2veh * r_sen_veh_veh[1]
				- Dpsi_loc2veh * Dpsi_loc2veh * r_sen_veh_veh[0];
			accel_veh_loc_veh[1] = accel_sen_loc_veh[1] + DDpsi_loc2veh * r_sen_veh_veh[0]
				- Dpsi_loc2veh * Dpsi_loc2veh * r_sen_veh_veh[1];

			// Switch expression from vehicle frame to local frame
			accel_veh_loc_loc = VectorFun.rotate2(accel_veh_loc_veh, -psi_loc2veh);

			// Update velocity
			cbeVelocity_veh_loc_loc[0] = (float) (cbeVelocity_veh_loc_loc[0] + accel_veh_loc_loc[0]
				* (newtime - acctime) / 1e9);
			cbeVelocity_veh_loc_loc[1] = (float) (cbeVelocity_veh_loc_loc[1] + accel_veh_loc_loc[1]
				* (newtime - acctime) / 1e9);
			acctime = newtime;
			((TextView) findViewById(R.id.accX)).setText("velX:"
				+ Double.toString(cbeVelocity_veh_loc_loc[0]));
			((TextView) findViewById(R.id.accY)).setText("velY:"
				+ Double.toString(cbeVelocity_veh_loc_loc[1]));
		}
	}
}

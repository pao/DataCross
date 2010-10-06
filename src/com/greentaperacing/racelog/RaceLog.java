package com.greentaperacing.racelog;

import java.util.List;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.TextView;

public class RaceLog extends Activity implements SensorEventListener {
	private SensorManager sm;
	private LocationManager lm;

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
	}

	@Override
	public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSensorChanged(final SensorEvent event) {
		// TODO Auto-generated method stub
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			((TextView) findViewById(R.id.accX)).setText("acc X:" + event.values[0]);
			((TextView) findViewById(R.id.accY)).setText("acc Y:" + event.values[1]);
			((TextView) findViewById(R.id.accZ)).setText("acc Z:" + event.values[2]);
		} else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
			((TextView) findViewById(R.id.gyrL)).setText("gyr L:" + event.values[0]);
			((TextView) findViewById(R.id.gyrM)).setText("gyr M:" + event.values[1]);
			((TextView) findViewById(R.id.gyrN)).setText("gyr N:" + event.values[2]);
		} else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
			((TextView) findViewById(R.id.magX)).setText("mag X:" + event.values[0]);
			((TextView) findViewById(R.id.magY)).setText("mag Y:" + event.values[1]);
			((TextView) findViewById(R.id.magZ)).setText("mag Z:" + event.values[2]);
		}
	}
}

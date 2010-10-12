package com.greentaperacing.racelog;

import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;

public class DownVectorCalibration extends Activity implements SensorEventListener {

	private static final double STDEV_LIMIT = 0.01; // m/s/s

	float[] accel_accum = { 0.0F, 0.0F, 0.0F };
	float[] accum_sq = { 0.0F, 0.0F, 0.0F };
	int nsamples = 0;

	private SensorManager sm;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		sm.registerListener(this, sm.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0),
			SensorManager.SENSOR_DELAY_FASTEST);
	}

	@Override
	public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
	}

	@Override
	public void onSensorChanged(final SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			final double[] sn = { 0.0F, 0.0F, 0.0F };
			nsamples++;
			for (int i = 0; i < event.values.length; i++) {
				accel_accum[i] += event.values[i];
				accum_sq[i] += event.values[i] * event.values[i];
				sn[i] = Math.sqrt(accum_sq[i] / nsamples
					- (accel_accum[i] * accel_accum[i] / nsamples / nsamples));
			}
			if (sn[0] < STDEV_LIMIT && sn[1] < STDEV_LIMIT && sn[2] < STDEV_LIMIT) {
				float[] gpX_sen3 = { 0.0F, 0.0F, 0.0F };
				float[] gpY_sen3 = { 0.0F, 0.0F, 0.0F };

				sm.unregisterListener(this);
				for (int i = 0; i < accel_accum.length; i++) {
					accel_accum[i] /= nsamples;
				}
				// Normalize gpNormal
				accel_accum = VectorFun.normalize(accel_accum);

				// Perform Gram-Schmidt process
				final float[] prjX = VectorFun.proj_axis(0, accel_accum);
				gpX_sen3[0] = 1 - prjX[0];
				gpX_sen3[1] = 0 - prjX[1];
				gpX_sen3[2] = 0 - prjX[2];
				gpX_sen3 = VectorFun.normalize(gpX_sen3);

				final float[] prjY1 = VectorFun.proj_axis(1, accel_accum);
				final float[] prjY2 = VectorFun.proj_axis(1, gpX_sen3);
				gpY_sen3[0] = 0 - prjY1[0] - prjY2[0];
				gpY_sen3[1] = 1 - prjY1[1] - prjY2[1];
				gpY_sen3[2] = 0 - prjY2[1] - prjY2[2];
				gpY_sen3 = VectorFun.normalize(gpY_sen3);

				final SharedPreferences sp = getSharedPreferences(RaceLog.CALIBRATION_PREFS, 0);
				final SharedPreferences.Editor editor = sp.edit();
				editor.putFloat("gpNormal_sen_veh_sen3/0", accel_accum[0]);
				editor.putFloat("gpNormal_sen_veh_sen3/1", accel_accum[1]);
				editor.putFloat("gpNormal_sen_veh_sen3/2", accel_accum[2]);
				editor.putFloat("gpX_sen3/0", gpX_sen3[0]);
				editor.putFloat("gpX_sen3/1", gpX_sen3[1]);
				editor.putFloat("gpX_sen3/2", gpX_sen3[2]);
				editor.putFloat("gpY_sen3/0", gpY_sen3[0]);
				editor.putFloat("gpY_sen3/1", gpY_sen3[1]);
				editor.putFloat("gpY_sen3/2", gpY_sen3[2]);
				editor.commit();

				finish();
			}
		}
	}
}

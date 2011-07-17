package com.greentaperacing.racelog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import com.greentaperacing.datacross.R;

public class DataCross extends Activity {
	public static final String CALIBRATION_PREFS = "calibration";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	}

	public void startDownVectorCalibration(final View v) {
		startActivity(new Intent("com.greentaperacing.racelog.DOWN_VECTOR_CALIBRATION"));
	}

	public void startHeadingCalibration(final View v) {
		startActivity(new Intent("com.greentaperacing.racelog.HEADING_CALIBRATION"));
	}

	public void startTrackTrajectory(final View v) {
		startActivity(new Intent("com.greentaperacing.racelog.TRACK_TRAJECTORY"));
	}
}

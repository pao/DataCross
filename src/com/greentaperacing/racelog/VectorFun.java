package com.greentaperacing.racelog;

public class VectorFun {
	public static double dot(final float[] u, final float[] v) {
		double accum = 0;
		for (int i = 0; i < u.length; i++) {
			accum = accum + u[i] * v[i];
		}
		return accum;
	}

	public static float[] normalize(final float[] vec) {
		final float[] vout = { Float.NaN, Float.NaN, Float.NaN };
		final double vec_mag = Math.sqrt(dot(vec, vec));
		for (int i = 0; i < vec.length; i++) {
			vout[i] = (float) (vec[i] / vec_mag);
		}
		return vout;
	}

	public static float[] proj_axis(final int axis, final float[] u) {
		final float[] vout = { Float.NaN, Float.NaN, Float.NaN };
		final double sf = u[axis] / dot(u, u);
		for (int i = 0; i < u.length; i++) {
			vout[i] = (float) (u[i] * sf);
		}
		return vout;
	}

	public static float[] rotate2(final float[] v, final float theta) {
		final float[] w = { Float.NaN, Float.NaN };
		w[0] = (float) (Math.cos(theta) * v[0] - Math.sin(theta) * v[1]);
		w[1] = (float) (Math.sin(theta) * v[0] + Math.cos(theta) * v[1]);
		return w;
	}
}

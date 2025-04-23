package org.lqzs.sorene.io;

public class RateCounter {
	private long value;
	private long last;

	public RateCounter() {
		value = 0;
		last = 0;
	}

	public void increase(long delta) {
		value += delta;
	}

	public long rate() {
		long now = value;
		long rate = now - last;
		last = now;
		return rate;
	}
}

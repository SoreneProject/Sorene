package org.lqzs.sorene.io;

public interface ProgressReporter {
	void report(String text, long now, long max);
}

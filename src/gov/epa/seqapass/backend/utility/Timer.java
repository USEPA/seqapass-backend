package gov.epa.seqapass.backend.utility;

import java.util.concurrent.TimeUnit;

public class Timer {

	private long startTime;
	private long stopTime;

	public Timer() {

	}

	public void start() {
		startTime = System.currentTimeMillis();
	}

	public void stop() {
		stopTime = System.currentTimeMillis();
	}

	public double getCurrentTimeElasped() {
		long currentTime = System.currentTimeMillis();
		long currentTimeElapsed = currentTime - startTime;
		long currentTimeElapsedMin = TimeUnit.MILLISECONDS.toMinutes(currentTimeElapsed);
		return currentTimeElapsedMin;
	}

	public double getTotalElapsedTime() {
		long totalTimeElapsed = stopTime - startTime;
		long totalTimeElapsedMin = TimeUnit.MILLISECONDS.toMinutes(totalTimeElapsed);
		return totalTimeElapsedMin;
	}
	
	public long getStartTime() {
		return startTime;
	}
	
	public long getStopTime() {
		return stopTime;
	}
	
	
}

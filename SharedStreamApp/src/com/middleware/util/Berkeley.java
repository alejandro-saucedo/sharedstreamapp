package com.middleware.util;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;

public class Berkeley {

	public static final String CLOCK_DIFFERENCE_PARAM = "CLOCK_DIFFERENCE";
	public  static final String CLOCK_ADJUSTION_PARAM = "GLOBAL_CLOCK";
	private long clockAverage     = 0;
	private HashMap <InetAddress , String> clockDifferences;
	
	
	public Berkeley () {
		clockDifferences = new HashMap <InetAddress , String>  ( );
	}
	

	public long getClockAverage() {
		return clockAverage;
	}

	public void setClockAverage(long clockAverage) {
		this.clockAverage = clockAverage;
	}

	public HashMap<InetAddress, String> getClockDifferences() {
		return clockDifferences;
	}
	
	public void clearClockDifferences () {
		clockDifferences.clear();
	}
}

package com.sstream.middleware.util;

import java.net.InetAddress;

public abstract class VideoInterruption  implements MessageInterruption{

	// do thing for now
	public void doInterruption (long processId, byte [] message , InetAddress origin) {
		
	}
	public void doInterruption (long processId) {
		
	}
	
	// must override this method
	public abstract void doInterruption ( VideoInterface vp);
	
	
}

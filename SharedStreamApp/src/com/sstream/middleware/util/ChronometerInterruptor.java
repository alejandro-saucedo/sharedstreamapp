package com.sstream.middleware.util;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sstream.middleware.Chronometer;
import com.sstream.middleware.Middleware;
import com.sstream.middleware.VideoContext;

public class ChronometerInterruptor implements MessageInterruption {

	
	private Chronometer c;
	private VideoPackage vp;
	private Middleware md;
	
	public ChronometerInterruptor (Chronometer c , VideoPackage vp, Middleware md) {
		this.c = c;
		this.vp = vp;
		this.md = md;
	}
	
	@Override
	public void doInterruption(long processId) {
		
		VideoInterruption vi = md.getVideoInterruption();
		
		
		if ( c.getType() == Chronometer.DELIMITED) {
			
			//System.out.println ("interrupted AYA," + c.getTimesExecuted());
			switch ( vp.getMessageType()) {
			
				
				case MSGTypes.AYA:
					if (c.getTimesExecuted() + 1 < c.getMaxExecutionTimes()) {
						
						try {
							
							md.send(vp.getTargetNode() , vp); // send original packet
							
						} catch (IOException e) {
							c.stopChronometer();
							md.getChronometers().remove(c.getProcessId());
							
							VideoException err = new VideoException ( vp);
							err.setErrorCode( MSGTypes.CANT_CONNECT_ERROR);
							vi.doInterruption( err );
							
						} 
						
					}else {
						
						VideoException ve = new VideoException ( vp );
						ve.setErrorCode(MSGTypes.DISCONNECTED);
						
						md.getChronometers().remove(vp.getMessageId());
						
						// interrupt user, node not available
						vi.doInterruption( ve );
					}
				break;
			
			}
			
		}
		else if ( c.getType() == Chronometer.DEFAULT) {
			
			List<InetAddress> nodes;
			long pending , responses ;
			
			if( md.getChronometers().get(vp.getMessageId()) != null) {
				
				
				switch (vp.getMessageType()) {
				case MSGTypes.REQUEST_GLOBAL_CLOCK : //not all the nodes answered in the time
					
					HashMap<InetAddress, String> aliveNodes = md.getBerkeley().getClockDifferences();
					
					pending 	= md.getPendingMap().get(vp.getMessageId()).getWaitingNodes();
					responses 	= aliveNodes.size();
					
					md.getChronometers().remove(vp.getMessageId());
					md.getPendingMap().remove(vp.getMessageId());
					
					if (responses > 0) { // at least one node responded
						
						VideoPackage vp = md.createGenericPacket(MSGTypes.SET_GLOBAL_CLOCK);
						
						long average , adjustment =0;
						
						for (Map.Entry<InetAddress, String> entry : aliveNodes.entrySet()) {
						    InetAddress key = entry.getKey();
						   
						    try {
								average 	= (md.getBerkeley().getClockAverage() / (pending + responses)) ;
								adjustment = average - Long.parseLong(aliveNodes.get(key));
								vp.getControlParameters().put(Berkeley.CLOCK_ADJUSTION_PARAM, String.valueOf( adjustment ));
								vp.setTargetNode(key);
								md.send(key , vp);
							} catch (IOException e) {
								VideoException ve = new VideoException ( vp );
								ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
								ve.setStackTrace(e.getStackTrace());
								md.getVideoInterruption().doInterruption(ve);
							}
						    
						}
					} 
					
					
					// determine if the missing nodes are still alive
					nodes = md.getVideoContext().getNodes();
					
					//System.out.println ("Interrupted, nodes missing: " + aliveNodes.size());
					
					for( InetAddress node: nodes) {
						
						if ( aliveNodes.get(node) == null) {
							try {
								md.sendAYAMessage(node, true);
							} catch (VideoException e) {
								
								md.getVideoInterruption().doInterruption(e);
							}
						}
					}
						
					break;
					
				case MSGTypes.ACQUIRE:
					// do nothing for now
					pending 	= md.getPendingMap().get(vp.getMessageId()).getWaitingNodes();
					
					md.getChronometers().remove(vp.getMessageId());
					md.getPendingMap().remove(vp.getMessageId());
					
					if ( pending == 1) { //Single node connected / first case
						
						md.assignCoordinator( vp );
						md.getVideoInterruption().doInterruption(0, "Became itself coordinator".getBytes(), null);
					} 
					
					else { // another node is streaming video.
						VideoException ve = new VideoException( vp );
						ve.setErrorCode(MSGTypes.ACQUIRE_VIDEO_NOT_POSIBLE);
						md.setWaitingAcquireResponses( false );
						md.getVideoInterruption().doInterruption( ve );
					}
					
					
				break;
				
				case MSGTypes.RELEASE:
					md.getChronometers().remove(vp.getMessageId());
					md.getPendingMap().remove(vp.getMessageId());
					
					
					try {
						md.release();
						
					} catch (VideoException e) {
						md.getVideoInterruption().doInterruption(e);
					}
					
				break;
				}
				
			}
		}
		
	}

	
	
	@Override
	public void doInterruption(long processId, byte[] message,
			InetAddress origin) {
		
		
	}

	@Override
	public void doInterruption(VideoInterface vp) {
		
	}

}

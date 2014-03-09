package com.sstream.middleware.util;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sstream.middleware.Chronometer;
import com.sstream.middleware.Middleware;
import com.sstream.middleware.MiddlewareServer;
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
		
		MessageInterruption vi = md.getMessageInterruption();
		
		
		if ( c.getType() == Chronometer.DELIMITED) {
			
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
								md.getMessageInterruption().doInterruption(ve);
							}
						    
						}
					} 
						
					// determine if the missing nodes are still alive
					nodes = md.getVideoContext().getNodes();
					
					for( InetAddress node: nodes) {
						
						if ( aliveNodes.get(node) == null) {
							try {
								md.sendAYAMessage(node, true);
							} catch (VideoException e) {
								
								md.getMessageInterruption().doInterruption(e);
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
						md.getMessageInterruption().doInterruption(0, "Became itself coordinator".getBytes(), null);
					} 
					
					else { // another node is streaming video.
						VideoException ve = new VideoException( vp );
						ve.setErrorCode(MSGTypes.ACQUIRE_VIDEO_NOT_POSIBLE);
						
						md.getMessageInterruption().doInterruption( ve );
					}
					
					
				break;
				
				case MSGTypes.RELEASE:
					md.getChronometers().remove(vp.getMessageId());
					md.getPendingMap().remove(vp.getMessageId());
					
					
					try {
						md.release(); // will try to find the next coordinator
						
					} catch (VideoException e) {
						md.getMessageInterruption().doInterruption(e);
					}
					
				break;
				
				case MSGTypes.TIMEOUT_WAITING_NEW_COORDINATOR:
					if ( !md.getVideoContext().hasCoordinator()) {
						md.stopAndCleanTimer( vp.getMessageId() );
						VideoContext vc = md.getVideoContext();
						
						vc.setCoordinator( vc.getWaitQueue().get( vc.getNextCoordinatorIndex() - 1 ));
						this.handleWaitingCoordinatorTimeout();
						
					}
					
				break;
				
				case MSGTypes.SERVER_DOWN_ERROR:
					
					long notifications = pending 	= md.getPendingMap().get(Middleware.SERVER_DOWN_KEY).getWaitingNodes();
					md.stopAndCleanTimer( vp.getMessageId() );
					md.getPendingMap().remove( Middleware.SERVER_DOWN_KEY);
					
					if ( notifications == 0) { // all the nodes responded affirmative server was down
						this.handleWaitingCoordinatorTimeout();
						
					} else {
						
						VideoException ve = new VideoException (vp);
						ve.setErrorCode( MSGTypes.ERROR_TRY_AGAIN);
						md.getMessageInterruption().doInterruption(ve);
						
					}
				break;
				}
				
			}
		}
		
	}

	private void handleWaitingCoordinatorTimeout () {
		VideoContext vc = md.getVideoContext();
		InetAddress nextCoord = vc.getNextCoordinator();
		
		if (nextCoord != null ) {
			
			if ( MiddlewareServer.isLocalAddress( nextCoord )) {
				vc.removeNode( vc.getCoordinator() );
				md.assignCoordinator(vp);
			} else {
				
				int waitNodes = md.getVideoContext().getWaitQueue().size();
							
				//Will wait for the next coordinator to be set, otherwise will start over
				if ( waitNodes > 0) { 
					/*start another timer to wait for the next coordinator to start streaming
					otherwise, all nodes will check which node is the next
					*/
					vc.removeNode( vc.getCoordinator() );
					vc.setCoordinator( null );
					
					Chronometer c = new Chronometer ( Chronometer.DEFAULT , Long.parseLong(vp.getMessageId()) );
					
					vp.setMessageType(MSGTypes.TIMEOUT_WAITING_NEW_COORDINATOR);
					c.setInterruptionListener(new ChronometerInterruptor (c , vp , md ));
					
					c.startChronometer(Middleware.MAX_WAIT * 2 );	
					
				}
			}
		}
		else {
			
			//TO-DO, remove all the context if Discovery Service will decide which node will stream the video.
			vc.setCoordinator( null );
			vp.setMessageType(MSGTypes.COORDINATOR_POSITION_AVAILABLE);
			md.getMessageInterruption().doInterruption(vp);
		}
	}
	@Override
	public void doInterruption(long processId, byte[] message, InetAddress origin) {
		
	}

	@Override
	public void doInterruption(VideoInterface vp) {
		
	}

}

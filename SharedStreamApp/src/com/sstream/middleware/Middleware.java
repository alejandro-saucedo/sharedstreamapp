package com.sstream.middleware;

/**
 * Sistemas Distribuidos
 * Miercoles 5 de Febrero 2014
 * 
 * CINVESTAV
 * 
 * Practica 1.
 * Middleware Distribuido
 * 
 * Autores:
 * 
 * Juan Carlos Reyes Martinez
 * Hector Alejandro Saucedo Briseno.
 * Luis Angel Ramos Cobarruvias
 * 
 * 
 */
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sstream.middleware.util.Berkeley;
import com.sstream.middleware.util.ChronometerInterruptor;
import com.sstream.middleware.util.MSGTypes;
import com.sstream.middleware.util.MessageInterruption;
import com.sstream.middleware.util.PendingVideoPackage;
import com.sstream.middleware.util.VideoException;
import com.sstream.middleware.util.VideoInterface;
import com.sstream.middleware.util.VideoInterruption;
import com.sstream.middleware.util.VideoPackage;


public class Middleware implements MessageInterruption{
	
	
	
	public static final int MAX_WAIT = 3; // seconds
	public static  final String SERVER_DOWN_KEY = "Server.down.key";
	
	
	private static final int CHRONOMETER_UPDATE_TIME = MAX_WAIT * 6; //seconds
	private static final int MAX_ATTEMPTS = 3;
	
	private Chronometer global ;
	private Thread clock ;
	private static long globalTime = System.currentTimeMillis();
	
	////// ------------------------------------- //////////
	
	private VideoContext 					videoContext ;
	private HashMap < String ,Chronometer>  	chronometers ;
	private HashMap <String , PendingVideoPackage> 	pendingMap ;
	private MessageInterruption				messageInterruptor ;
	private MiddlewareServer 				myServer ;
	private Berkeley						berkeley;
	
	private boolean isCoordinator = false;
	private boolean isWaitingForCoordinator = false;
	
	
	

	private class MyClock extends Thread {
		
		public MyClock () {
			
		}
		public void run () {
			
			while (true) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
				globalTime += (1 * 1000);
				if ( (int)(globalTime / 1000 ) % 30 == 0)
					messageInterruptor.doInterruption(0,
						("Global time :" + globalTime).getBytes(), null);
			}
		}
	}
	

	public Middleware (MessageInterruption itr, InetAddress host) {
			
		videoContext	 = new VideoContext ( );
		chronometers  	 = new HashMap<String , Chronometer>();
		pendingMap  	 = new HashMap<String , PendingVideoPackage>();
		berkeley 		 = new Berkeley ();
		myServer 		 = new MiddlewareServer (host);
		clock 			 = new MyClock ( );
		
		messageInterruptor = itr; // external call-backs
		
		clock.start();
		
		//this.setCoordinator( true );	
		//videoContext.setCoordinator( host );	

		videoContext.addNode( host ); // by default all contexts contains itself
		
	}
	
	/***************************************************************
	 * 
	 * Privated Methods
	 * 
	 ***************************************************************/
	
	private void setGlobalTime ( long globalTime ) {
		this.globalTime = globalTime;
	}
	
			
	
	private void createWaitingChronometer (VideoPackage vp , int time , int waitingNodes) {
		
		Chronometer c = new Chronometer ( Chronometer.DEFAULT , Long.parseLong(vp.getMessageId()) );
			
		c.setInterruptionListener(new ChronometerInterruptor (c , vp , this ));
		chronometers.put(vp.getMessageId(), c);
		pendingMap.put(vp.getMessageId(), new PendingVideoPackage (vp , waitingNodes)); // waiting for nodes.size messages
		c.startChronometer(time );
	}
	private void createServerDownChronometer (VideoPackage vp , int time , int waitingNodes) {
		
		Chronometer c = new Chronometer ( Chronometer.DEFAULT , Long.parseLong(vp.getMessageId()) );
			
		c.setInterruptionListener(new ChronometerInterruptor (c , vp , this ));
		chronometers.put(vp.getMessageId(), c);
		pendingMap.put(Middleware.SERVER_DOWN_KEY, new PendingVideoPackage (vp , waitingNodes)); // waiting for nodes.size messages
		c.startChronometer(time );
	}
	
	
	private void processServerDownNotifications (  VideoPackage v ) {
		
		if (  pendingMap.get(Middleware.SERVER_DOWN_KEY) != null) {
			PendingVideoPackage pending = pendingMap.get(Middleware.SERVER_DOWN_KEY);
			pending.setWaitingNodes( pending.getWaitingNodes() - 1);
			pendingMap.put (Middleware.SERVER_DOWN_KEY, pending);
			
		}
	}
	private void processBerkeleyResponses ( VideoPackage v) {
		
	if (  pendingMap.get(v.getMessageId()) != null) {
			
			
			PendingVideoPackage pending = pendingMap.get(v.getMessageId());
			pending.setWaitingNodes( pending.getWaitingNodes() - 1);
			pendingMap.put (v.getMessageId(), pending);
			
			long waitNodes = pending.getWaitingNodes();
			
			berkeley.setClockAverage(berkeley.getClockAverage() + 
					Long.parseLong(v.getControlParameters().get(Berkeley.CLOCK_DIFFERENCE_PARAM) ));
			
			berkeley.getClockDifferences().put(v.getOriginNode(), 
					v.getControlParameters().get(Berkeley.CLOCK_DIFFERENCE_PARAM));
			
			if ( waitNodes <= 0) {
				
				this.stopAndCleanTimer(v.getMessageId());
				
				VideoPackage vp = createGenericPacket(MSGTypes.SET_GLOBAL_CLOCK);
				List<InetAddress> nodes = videoContext.getNodes();
				
				long average, adjustment = 0;
				for (InetAddress node: nodes) {
					try {
						average 	= (berkeley.getClockAverage() / nodes.size()) ;
						// do not need to adjust itself since the message goes to all connected nodes
						adjustment = average - Long.parseLong(berkeley.getClockDifferences().get(node));
						vp.getControlParameters().put(Berkeley.CLOCK_ADJUSTION_PARAM, String.valueOf( adjustment ));
						
						this.send(node , vp);
					} catch (IOException e) {
						VideoException ve = new VideoException ( vp );
						ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
						ve.setStackTrace(e.getStackTrace());
						this.messageInterruptor.doInterruption(vp);
					}
				}
				
				berkeley.setClockAverage(0);
				berkeley.clearClockDifferences();
			} 
			
			
		}
	}
	
	
	private void processAcknowledge ( VideoPackage vp) {
		
		
		if (pendingMap.get(vp.getMessageId()) != null) { // waiting messages for this msg id
		
			PendingVideoPackage pending = pendingMap.get(vp.getMessageId());
			pending.setWaitingNodes( pending.getWaitingNodes() - 1);
			pendingMap.put (vp.getMessageId(), pending);
			
			long waitNodes	 = pending.getWaitingNodes() ; // just received a package 
			VideoPackage original = pending.getVideoPackage();
			
			
			if ( waitNodes <= 0) {
				
				if (original.getMessageType() == MSGTypes.ACQUIRE) {
					this.assignCoordinator(vp);
				}
				else if ( original.getMessageType() == MSGTypes.RELEASE ) {
					
					this.stopAndCleanTimer( vp.getMessageId() );
					videoContext.restartWaitQueue();
					InetAddress nextCoordinator = videoContext.getNextCoordinator();
					
					vp.setMessageType(MSGTypes.COORDINATOR_DESIGNATED);
					vp.setOriginNode(myServer.getHost());
					
					videoContext.setCoordinator( nextCoordinator ); // coordinator will add itself 
					videoContext.getWaitQueue().remove( 0 ); 
					
					
					vp.setVideoContext(this.videoContext);
					this.setCoordinator(false);

					try {
						vp.setTargetNode(nextCoordinator);
						this.send(nextCoordinator, vp);
						
					} catch (IOException e) {
						VideoException ve = new VideoException(vp);
						ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
						ve.setStackTrace(e.getStackTrace());
						this.messageInterruptor.doInterruption(vp);
					}

					vp.setMessageType( MSGTypes.RELEASE);
					this.messageInterruptor.doInterruption( vp );
					
				}
			}
				
		}
	}
	
	private void updateContext ( InetAddress origin , VideoContext vc) {
		
		List<InetAddress> nodes = vc.getNodes();
		List<InetAddress> waitQueue = vc.getWaitQueue();
		InetAddress coord = vc.getCoordinator();
		
		for ( int i=0; i< nodes.size(); i ++) {
			if (nodes.get(i).getHostAddress().equals("127.0.0.1")) {
				nodes.remove(i);
				nodes.add(i, origin);
			}
		}
		for ( int i=0; i< waitQueue.size(); i ++) {
			if (waitQueue.get(i).getHostAddress().equals("127.0.0.1")) {
				waitQueue.remove(i);
				waitQueue.add(i, origin);
			}
		}
		if ( coord.getHostAddress().equals("127.0.0.1")) {
			coord = origin;
		}
		videoContext.setCoordinator( origin );
		videoContext.setNodes( (CopyOnWriteArrayList<InetAddress>)nodes );
		videoContext.setWaitQueue( (CopyOnWriteArrayList<InetAddress>) waitQueue );
		videoContext.setGlobalTIme( vc.getGlobalTime());
		videoContext.restartWaitQueue();
		
		
	}
	
	
	/***************************************************************
	 * 
	 * Public Methods
	 * 
	 ***************************************************************/
	public void stopAndCleanTimer (String id) {
		if ( chronometers.get(id) != null)
			chronometers.get( id ).stopChronometer();
		
		chronometers.remove(id);
		pendingMap.remove(id);
	}
	public VideoPackage createGenericPacket (int msgType) {
		VideoPackage vp = new VideoPackage ();
		vp.setMessageId(globalTime + "");
		vp.setGlobalTime(globalTime);
		vp.setMessageType(msgType);
		vp.setOriginNode(myServer.getHost());
		
		return vp;
	}
	public void createServerListener () {
		
		global = new Chronometer ( Chronometer.PERIODIC);
		global.setInterruptionListener(this);
		global.startChronometer(CHRONOMETER_UPDATE_TIME);
		
		myServer.setInterruptionMessageListener(this);
		startServer ();
	}
	public void startServer () {
		myServer.start();
	}
	
	public void send ( InetAddress ip , VideoPackage vp) throws IOException  {
		byte [] msg = myServer.unwrapMessage (vp);
		myServer.sendMessage(ip, msg);
	}
	
	
	public void sendToAll(  VideoPackage vp ) throws IOException  {
		List <InetAddress> nodes = videoContext.getNodes();
		vp.setOriginNode( myServer.getHost() );
		for( InetAddress a: nodes) {
			vp.setTargetNode( a );
			send(a, vp);
		}
		
	}
	public void assignCoordinator ( VideoPackage vp ) {
		
		videoContext.setCoordinator(myServer.getHost());
		this.stopAndCleanTimer(vp.getMessageId());

		vp.setMessageType(MSGTypes.COORDINATOR_SET);
		vp.setOriginNode(myServer.getHost());
		vp.setVideoContext(this.videoContext);
		this.setCoordinator(true);
		
		List<InetAddress> nodes = videoContext.getNodes();
		
		for (InetAddress node : nodes) {
			try {
				vp.setTargetNode(node);
				
				//not sending this to itself
				if ( !MiddlewareServer.isLocalAddress(node)) {
					this.send(node, vp);
				}
			} catch (IOException e) {
				VideoException ve = new VideoException(vp);
				ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
				ve.setStackTrace(e.getStackTrace());
				this.messageInterruptor.doInterruption(vp);
			}
		}
		
		vp.setMessageType(MSGTypes.OK_START_RECORDING); // update upper tire it is ok to start recording
		this.messageInterruptor.doInterruption( vp );
	}
	public void sendAYAMessage (InetAddress node, boolean firstTime) throws VideoException {
		VideoPackage vp = createGenericPacket(MSGTypes.AYA);
		vp.setTargetNode(node);
		try {
			send ( node , vp);
			
		} catch (IOException e) {
			
			VideoException ve = new VideoException ( vp );
			ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
			ve.setStackTrace(e.getStackTrace());
			throw ve;
		}
		
		if ( firstTime ) {
			Chronometer c = new Chronometer ( Chronometer.DELIMITED , 
				Long.parseLong(vp.getMessageId()) , MAX_ATTEMPTS);
		
			c.setInterruptionListener(new ChronometerInterruptor (c , vp , this ));
			chronometers.put(vp.getMessageId(), c);
			c.startChronometer(MAX_WAIT);
		}
		
	}
	
	public void sendNewContext (InetAddress node) throws VideoException {
		
		VideoPackage vp = createGenericPacket( MSGTypes.NEW_CONTEXT);
		videoContext.setGlobalTIme(globalTime);
		videoContext.addNode( node );
		vp.setVideoContext(videoContext);
		vp.setOriginNode(myServer.getHost());
		
		try {
			
			// new Context for the new connected node
			vp.setTargetNode( node );
			send ( node , vp);
			
			//update the context for the rest of the nodes
			vp.setMessageType(MSGTypes.UPDATE_CONTEXT);
			for (InetAddress n:  videoContext.getNodes()) {
				vp.setTargetNode(n);
				if ( !MiddlewareServer.isLocalAddress( n )) {
					send ( n , vp);
				}
					
			}	
			
		} catch (IOException e) {
			
			VideoException ve = new VideoException ( vp );
			ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
			ve.setStackTrace(e.getStackTrace());
			throw ve;
		}
	}
	
	public void acquire ( ) {
		VideoPackage vp = createGenericPacket(MSGTypes.ACQUIRE);
		
		vp.setOriginNode( myServer.getHost());
		
		try {
			InetAddress node = videoContext.getCoordinator();
			vp.setTargetNode(node);
			send( node , vp);
		} catch (UnknownHostException e1) {
			VideoException ve = new VideoException ( vp );
			ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
			ve.setStackTrace(e1.getStackTrace());
			
			messageInterruptor.doInterruption( ve );
			
		} catch (IOException e1) {
			VideoException ve = new VideoException ( vp );
			ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
			ve.setStackTrace(e1.getStackTrace());
			
			messageInterruptor.doInterruption( ve );
			
		}
	}

	public void release ( ) throws VideoException {
		
		if ( this.isCoordinator()) {
			VideoPackage vp = createGenericPacket(MSGTypes.RELEASE);
			InetAddress next = videoContext.getNextCoordinator();
			//videoContext.restartWaitQueue();
			
			if (next != null) {
				
				try {
					vp.setTargetNode(next);
					send(next, vp);
					this.createWaitingChronometer(vp, MAX_WAIT , 1);

				} catch (IOException e) {

					VideoException ve = new VideoException(vp);
					ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
					ve.setStackTrace(e.getStackTrace());
					throw ve;
				}
			}
			else { 
				// for now if not available node coordinator will stop recording
				//this.setCoordinator( false );
				VideoException warn = new VideoException ( vp );
				warn.setErrorCode(MSGTypes.NO_NEXT_COORDINATOR_WARNING);
				
				messageInterruptor.doInterruption( warn );
				
			}
		}	
	}
	
	
	public void handleServerDownError () {
		VideoPackage vp = createGenericPacket(MSGTypes.SERVER_DOWN_ERROR);	
		
		try {
			sendToAll(vp);
			this.createServerDownChronometer(vp, MAX_WAIT * 2, videoContext.getNodes().size()  - 1 );
			
		} catch (UnknownHostException e1) {
			VideoException ve = new VideoException ( vp );
			ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
			ve.setStackTrace(e1.getStackTrace());
			
			messageInterruptor.doInterruption( ve );
			
		} catch (IOException e1) {
			VideoException ve = new VideoException ( vp );
			ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
			ve.setStackTrace(e1.getStackTrace());
			
			messageInterruptor.doInterruption( ve );
			
		}
	}
	@Override
	public void doInterruption(long processId, byte [] message, InetAddress origin) {
		
		try {
					
			VideoPackage pck = myServer.wrapMessage(message); // wrap the object
			
			switch (pck.getMessageType()) {

			case MSGTypes.NEW_CONTEXT  :
				
					updateContext(origin, pck.getVideoContext() );
					messageInterruptor.doInterruption(processId, 
							("-->New Context set from: " + origin.getHostAddress()).getBytes(), origin);
					messageInterruptor.doInterruption( pck );
				
			break;
			case MSGTypes.NEW_MEMBER :
				// send current Context
				messageInterruptor.doInterruption(processId, 
						("-->New Member petition from: " + origin.getHostAddress()).getBytes(), origin);
				this.sendNewContext( origin );
				
			break;
			case MSGTypes.UPDATE_CONTEXT :
				
					updateContext(origin, pck.getVideoContext() );
					messageInterruptor.doInterruption(processId, 
							("-->Updated Context set from: " + origin.getHostAddress()).getBytes(), origin);
			break;
			
			case MSGTypes.COORDINATOR_POSITION_AVAILABLE :
				
				messageInterruptor.doInterruption(processId, 
						("Coordinator Position Available: " + origin.getHostAddress()).getBytes(), origin);
				messageInterruptor.doInterruption( pck );
			break;
			case MSGTypes.COORDINATOR_SET :
				updateContext(origin, pck.getVideoContext() );
				messageInterruptor.doInterruption(processId, 
						("Coordinator Setted , updating connection socket: " + origin.getHostAddress()).getBytes(), origin);
				messageInterruptor.doInterruption( pck );
			break;
			case MSGTypes.COORDINATOR_RECORD_REQUIRE:
				
				if ( isWaitingForCoordinator ) {  // nobody has come
					messageInterruptor.doInterruption(processId, 
							("Coordinator request petition, sending release coordination to: " + origin.getHostAddress()).getBytes(), origin);
					
					videoContext.addOnWaitQueue( origin ); 
					
					release ();
					isWaitingForCoordinator = false;
				}
				
			break;
			
			case MSGTypes.COORDINATOR_DESIGNATED:
				updateContext(origin, pck.getVideoContext() );
				this.assignCoordinator( pck );
			break;	
			
			case MSGTypes.REQUEST_GLOBAL_CLOCK:
					
					long dif = globalTime -  pck.getGlobalTime();
					pck.setMessageType(MSGTypes.RESPONSE_GLOBAL_CLOCK);
					pck.addControlParameter(Berkeley.CLOCK_DIFFERENCE_PARAM, String.valueOf(dif));
					pck.setOriginNode( pck.getTargetNode() );
					pck.setTargetNode( origin );
					this.send( origin , pck);
					
			break;
				
			case MSGTypes.RESPONSE_GLOBAL_CLOCK:
				pck.setOriginNode(origin);
				processBerkeleyResponses( pck );
				
			break;
			
			case MSGTypes.SET_GLOBAL_CLOCK:
				long adjustment = Long.parseLong(pck.getControlParameters().get(Berkeley.CLOCK_ADJUSTION_PARAM));
				this.setGlobalTime(globalTime + adjustment);
				messageInterruptor.doInterruption(0, ("--> New setted clock: " 
							+ globalTime + ",Adjustment->" + adjustment).getBytes(), null);
				
			break;
			
			case MSGTypes.RELEASE :
				pck.setMessageType(MSGTypes.ACK);
				pck.setOriginNode( myServer.getHost() );
				pck.setTargetNode( origin );
				this.send( origin, pck);
			break;
			case MSGTypes.ACQUIRE:
				messageInterruptor.doInterruption(processId, 
						("-->Acquire petition from: " + origin.getHostAddress()).getBytes(), origin);
				if ( this.isCoordinator()) {
					
					if (videoContext.addOnWaitQueue(origin)) { // was not already there
																
						List<InetAddress> nodes = this.getVideoContext().getNodes();

						VideoPackage aux = this.createGenericPacket(MSGTypes.UPDATE_CONTEXT);
						aux.setVideoContext(videoContext);

						for (InetAddress node : nodes) {

							aux.setTargetNode(node);
							try {
								// updated context is not sent to itself
								if (!MiddlewareServer.isLocalAddress(node)) {
									this.send(node, aux);
								}
							} catch (IOException e) {
								VideoException ve = new VideoException(aux);
								ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
								ve.setStackTrace(e.getStackTrace());
								messageInterruptor.doInterruption(ve);
							}
						}
					}
				}/*else { 
					
					pck.setMessageType(MSGTypes.ACK);
					pck.setOriginNode( myServer.getHost());
					pck.setTargetNode( origin );
					
					if ( !this.isWaitingAcquireResponses || 
							origin.getHostAddress().equals(myServer.getHost().getHostAddress())) {
						messageInterruptor.doInterruption(processId, 
								("-->Sending ACK: " + origin.getHostAddress() +   ", ID=" + pck.getMessageId() ).getBytes(), origin);
						
						this.send( origin, pck);	
					}else {
					
						if ( Middleware.globalTime > pck.getGlobalTime()) {
							this.send( origin, pck);
						}
					}
					
				}*/
			break;
			
			
			case MSGTypes.ACK:
				messageInterruptor.doInterruption(processId, 
						("-->Receving ACK: " + origin.getHostAddress() +  ", ID=" + pck.getMessageId()).getBytes(), origin);
				
				this.processAcknowledge( pck );
			break;

			case MSGTypes.SERVER_DOWN_ERROR:
				messageInterruptor.doInterruption(processId, 
						("-->SERVER DOWN Error Notification: " + origin.getHostAddress() ).getBytes(), origin);
				this.processServerDownNotifications( pck );		
			break;

			
			case MSGTypes.AYA:
				// we are sending the same message since the id comes here
				pck.setMessageType(MSGTypes.IAA);
				pck.setOriginNode( myServer.getHost() );
				pck.setTargetNode( origin );
				this.send(origin, pck); // I am alive

				break;
			case MSGTypes.IAA:
				
				Chronometer tmp = chronometers.get(pck.getMessageId());
				
				if (tmp != null) {
					tmp.stopChronometer();
					chronometers.remove( pck.getMessageId() );
				} 
				break;
			}
		
		}catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	/*
	 * Global Interruption clock
	 */
	@Override
	public void doInterruption(long processId) { 
		
		
		if ( this.isCoordinator()) {
			
			VideoPackage vp = createGenericPacket(MSGTypes.REQUEST_GLOBAL_CLOCK);
			List<InetAddress> nodes = videoContext.getNodes();
			messageInterruptor.doInterruption(0,  ("Coordinator sending clock petitions to: " + nodes.size()).getBytes(), null);
			this.createWaitingChronometer(vp, MAX_WAIT, nodes.size());
			
			for (InetAddress node: nodes) {
				try {
					vp.setTargetNode( node );
					this.send(node , vp);
				} catch (IOException e) {
					VideoException ve = new VideoException ( vp );
					ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
					ve.setStackTrace(e.getStackTrace());
					this.messageInterruptor.doInterruption(ve);
				}
			}
			 
		} else {
			messageInterruptor.doInterruption(0,  "Global Clock interruption... not coordinator".getBytes(), null);
		}
		
	}
	

	@Override
	public void doInterruption(VideoInterface vc) {
		// TODO Auto-generated method stub
		
	}
	
	
	/*
	 * ***********************************************************************
	 * Setters and getters
	 **************************************************************************/
	
	public long getServerId () {
		return myServer.getId();
	}
	
	public VideoContext getVideoContext () {
		return videoContext;
	}
	public void stopServer () throws Exception  {
		myServer.stopServer();
	}
	public MessageInterruption getMessageInterruption () {
		return messageInterruptor;
	}
	
	
	public HashMap < String ,Chronometer>  getChronometers ()  {
		return chronometers;
	}
	public HashMap<String, PendingVideoPackage>  getPendingMap ()  {
		return pendingMap;
	}
	
	public Berkeley getBerkeley () {
		return berkeley;
	}
	
	
	public boolean isCoordinator() {
		return isCoordinator;
	}

	public void setCoordinator(boolean isCoordinator) {
		this.isCoordinator = isCoordinator;
	}
	
	
	public void setWaitingForCoordinator(boolean u) {
		this.isWaitingForCoordinator = u;
	}

	public boolean isWaitingForCoordinator() {
		return isWaitingForCoordinator;
	}


}


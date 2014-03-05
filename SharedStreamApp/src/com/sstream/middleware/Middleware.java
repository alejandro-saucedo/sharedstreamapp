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
	
	
	
	private static final int MAX_WAIT = 3; // seconds
	
	
	private static final int CHRONOMETER_UPDATE_TIME = MAX_WAIT * 6; //seconds
	private static final int MAX_ATTEMPTS = 3;
	
	private Chronometer global ;
	private Thread clock ;
	private static long globalTime = System.currentTimeMillis();
	
	////// ------------------------------------- //////////
	
	private VideoContext 					videoContext ;
	private HashMap < String ,Chronometer>  	chronometers ;
	private HashMap <String , PendingVideoPackage> 	pendingMap ;
	private VideoInterruption 				videoInterruptor ;
	private MiddlewareServer 				myServer ;
	private Berkeley						berkeley;
	
	private boolean isCoordinator = false;
	private boolean isWaitingAcquireResponses = false;
	
	
	

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
					videoInterruptor.doInterruption(0,
						("Global time :" + globalTime).getBytes(), null);
			}
		}
	}
	

	public Middleware (VideoInterruption itr) {
			
		videoContext	 = new VideoContext ( );
		chronometers  	 = new HashMap<String , Chronometer>();
		pendingMap  	 = new HashMap<String , PendingVideoPackage>();
		berkeley 		 = new Berkeley ();
		myServer 		 = new MiddlewareServer ();
		clock 			 = new MyClock ( );
		
		videoInterruptor = itr; // external call-backs
		
		clock.start();
		this.setCoordinator( true );
		
		videoContext.addOnWaitQueue( myServer.getHost() );	
		videoContext.addNode(myServer.getHost()); // by default all contexts contains itself
		
	}
	
	/***************************************************************
	 * 
	 * Privated Methods
	 * 
	 ***************************************************************/
	
	private void setGlobalTime ( long globalTime ) {
		this.globalTime = globalTime;
	}
	
	private byte[] unwrapMessage(VideoPackage vp) throws IOException {
		
		// message structure, first 4 bytes is the length of the message, the rest is the VideoPackage object
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject( vp );
		oos.flush();
		// get the byte array of the object
		byte[] buffer = baos.toByteArray();

		int number = buffer.length;
		
		byte[] data = new byte[4 + number];

		// int -> byte[]
		for (int i = 0; i < 4; ++i) {
			int shift = i << 3; // i * 8
			data[3 - i] = (byte) ((number & (0xff << shift)) >>> shift);
		}
		
		System.arraycopy(buffer, 0, data, 4, number);
		
		return data;
	}
	
	
	private VideoPackage wrapMessage (byte [] data) throws IOException, ClassNotFoundException {
		int msgLength = 0;
		// byte[] -> int
		for (int i = 0; i < 4; ++i) {
			msgLength |= (data[3 - i] & 0xff) << (i << 3);
		}

		// now we know the length of the payload
		byte[] buffer = new byte[msgLength];
		System.arraycopy(data, 4, buffer, 0, msgLength);

		ByteArrayInputStream baos = new ByteArrayInputStream(buffer);
		ObjectInputStream oos = new ObjectInputStream(baos);
		VideoPackage vp = (VideoPackage) oos.readObject();
		
		return vp;
	}
	
		
	
	private void createWaitingChronometer (VideoPackage vp , int time , int waitingNodes) {
		
		Chronometer c = new Chronometer ( Chronometer.DEFAULT , Long.parseLong(vp.getMessageId()) );
			
		c.setInterruptionListener(new ChronometerInterruptor (c , vp , this ));
		chronometers.put(vp.getMessageId(), c);
		pendingMap.put(vp.getMessageId(), new PendingVideoPackage (vp , waitingNodes)); // waiting for nodes.size messages
		c.startChronometer(time );
	}
	
	private void stopAndCleanChonomters (String id) {
		if ( chronometers.get(id) != null)
			chronometers.get( id).stopChronometer();
		
		chronometers.remove(id);
		pendingMap.remove(id);
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
				
				this.stopAndCleanChonomters(v.getMessageId());
				
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
						this.videoInterruptor.doInterruption(vp);
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
					
					this.stopAndCleanChonomters( vp.getMessageId() );
					
					InetAddress nextCoordinator = videoContext.getNextCoordinator();
					
					vp.setMessageType(MSGTypes.COORDINATOR_DESIGNATED);
					vp.setOriginNode(myServer.getHost());
					
					videoContext.removeCurrentCoordinator();
					videoContext.getWaitQueue().remove( 0 ); // designated coordinator will add itself
					
					videoContext.restartWaitQueue();
					
					vp.setVideoContext(this.videoContext);
					this.setCoordinator(false);

					try {
						vp.setTargetNode(nextCoordinator);
						this.send(nextCoordinator, vp);
						
					} catch (IOException e) {
						VideoException ve = new VideoException(vp);
						ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
						ve.setStackTrace(e.getStackTrace());
						this.videoInterruptor.doInterruption(vp);
					}

					vp.setMessageType( MSGTypes.RELEASE);
					this.videoInterruptor.doInterruption( vp );
					
				}
			}
				
		}
	}
	
	private void updateContext ( InetAddress origin , VideoContext vc) {
		
		List<InetAddress> nodes = vc.getNodes();
		List<InetAddress> waitQueue = vc.getWaitQueue();
		
		
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
		videoContext.setNodes( (CopyOnWriteArrayList<InetAddress>)nodes );
		videoContext.setWaitQueue( (CopyOnWriteArrayList<InetAddress>) waitQueue );
		videoContext.setGlobalTIme( vc.getGlobalTime());
		
	}
	
	
	/***************************************************************
	 * 
	 * Public Methods
	 * 
	 ***************************************************************/
	public VideoPackage createGenericPacket (int msgType) {
		VideoPackage vp = new VideoPackage ();
		vp.setMessageId(System.currentTimeMillis() + "");
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
		byte [] msg = unwrapMessage (vp);
		myServer.sendMessage(ip, msg);
	}
	
	public void sendToCoordinator (  VideoPackage vp) throws IOException  {
		byte [] msg = unwrapMessage (vp);
		InetAddress coord = videoContext.getCoordinator();
		if ( coord != null) {
			myServer.sendMessage(coord, msg);
		}else {
			throw new IOException ("No coordinator available.");
		}
		
	}
	public void sendToAll(  VideoPackage vp ) throws IOException  {
		byte [] msg = unwrapMessage (vp);
		List <InetAddress> nodes = videoContext.getNodes();
		
		for( InetAddress a: nodes) {
			myServer.sendMessage(a, msg);
		}
		
	}
	public void assignCoordinator ( VideoPackage vp ) {
		
		videoContext.setNewCoordinator(myServer.getHost());
		this.stopAndCleanChonomters(vp.getMessageId());

		vp.setMessageType(MSGTypes.COORDINATOR_SETTED);
		vp.setOriginNode(myServer.getHost());
		vp.setVideoContext(this.videoContext);
		this.setCoordinator(true);
		this.isWaitingAcquireResponses = false;

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
				this.videoInterruptor.doInterruption(vp);
			}
		}
		
		vp.setMessageType(MSGTypes.OK_START_RECORDING); // update upper tire it is ok to start recording
		this.videoInterruptor.doInterruption( vp );
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
	
	public void acquire ( ) throws VideoException {
		VideoPackage vp = createGenericPacket(MSGTypes.ACQUIRE);
		List<InetAddress> nodes = videoContext.getNodes();
		
		vp.setOriginNode( myServer.getHost());
		try {
			
			this.isWaitingAcquireResponses = true;
			this.createWaitingChronometer(vp,nodes.size() > 1 ? MAX_WAIT * 2 : 1, nodes.size() );
			for (InetAddress node: nodes) {
				vp.setTargetNode(node);
				
				send ( node , vp);	
			}	
		} catch (IOException e) {
			
			VideoException ve = new VideoException ( vp );
			ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
			ve.setStackTrace(e.getStackTrace());
			throw ve;
		}
		
	}

	public void release ( ) throws VideoException {
		
		if ( this.isCoordinator()) {
			VideoPackage vp = createGenericPacket(MSGTypes.RELEASE);
			InetAddress next = videoContext.getNextCoordinator();
			videoContext.restartWaitQueue();
			
			
			if (next != null) {
				//System.out.println ("Next Coordinator: " + next.getHostAddress());
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
				
				videoInterruptor.doInterruption(vp);
				
			}
		}
		
	}
	
	@Override
	public void doInterruption(long processId, byte [] message, InetAddress origin) {
		
		try {
			
			
			VideoPackage pck = wrapMessage(message); // wrap the object
			
			switch (pck.getMessageType()) {

			case MSGTypes.NEW_CONTEXT  :
				
					updateContext(origin, pck.getVideoContext() );
					videoInterruptor.doInterruption(processId, 
							("-->New Context set from: " + origin.getHostAddress()).getBytes(), origin);
					videoInterruptor.doInterruption( pck );
				
			break;
			case MSGTypes.UPDATE_CONTEXT :
				
					updateContext(origin, pck.getVideoContext() );
					videoInterruptor.doInterruption(processId, 
							("-->Updated Context set from: " + origin.getHostAddress()).getBytes(), origin);
			break;
			
			
			case MSGTypes.COORDINATOR_POSITION_AVAILABLE :
				
				videoInterruptor.doInterruption(processId, 
						("Coordinator Position Available: " + origin.getHostAddress()).getBytes(), origin);
				videoInterruptor.doInterruption( pck );
			break;
			case MSGTypes.COORDINATOR_SETTED :
				updateContext(origin, pck.getVideoContext() );
				videoInterruptor.doInterruption(processId, 
						("Coordinator Setted , updating connection socket: " + origin.getHostAddress()).getBytes(), origin);
				videoInterruptor.doInterruption( pck );
			break;

			case MSGTypes.NEW_MEMBER :
				// send current Context
				videoInterruptor.doInterruption(processId, 
						("-->New Member petition from: " + origin.getHostAddress()).getBytes(), origin);
				this.sendNewContext( origin );
				
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
				videoInterruptor.doInterruption(0, ("--> New setted clock: " 
							+ globalTime + ",Adjustment->" + adjustment).getBytes(), null);
				
			break;
			
			case MSGTypes.RELEASE :
				pck.setMessageType(MSGTypes.ACK);
				pck.setOriginNode( myServer.getHost() );
				pck.setTargetNode( origin );
				this.send( origin, pck);
			break;
			case MSGTypes.ACQUIRE:
				videoInterruptor.doInterruption(processId, 
						("-->Acquire petition from: " + origin.getHostAddress()).getBytes(), origin);
				if ( this.isCoordinator()) {
					
					videoContext.addOnWaitQueue( origin );
					List<InetAddress> nodes = this.getVideoContext().getNodes();
					
					VideoPackage aux = this.createGenericPacket( MSGTypes.UPDATE_CONTEXT);
					aux.setVideoContext(videoContext);
					
					for (InetAddress node : nodes) {

						aux.setTargetNode(node);	
						try {
							// updated context is not sent to itself
							if ( !MiddlewareServer.isLocalAddress( node )) {
								this.send(node, aux);
							}
						}catch (IOException e) {
							VideoException ve = new VideoException (aux );
							ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
							ve.setStackTrace( e.getStackTrace());
							videoInterruptor.doInterruption( ve );
						}	
					}
				}/*else {
					
					pck.setMessageType(MSGTypes.ACK);
					pck.setOriginNode( myServer.getHost());
					pck.setTargetNode( origin );
					
					if ( !this.isWaitingAcquireResponses || 
							origin.getHostAddress().equals(myServer.getHost().getHostAddress())) {
						videoInterruptor.doInterruption(processId, 
								("-->Sending ACK: " + origin.getHostAddress() +   ", ID=" + pck.getMessageId() ).getBytes(), origin);
						
						this.send( origin, pck);	
					}else {
					
						if ( Middleware.globalTime > pck.getGlobalTime()) {
							this.send( origin, pck);
						}
					}
					
				}*/
			break;
			
			case MSGTypes.OK_START_PLAYING:
				videoInterruptor.doInterruption(processId, 
						("-->OK start playing !: " + origin.getHostAddress() +  ", ID=" + pck.getMessageId()).getBytes(), origin);
				
				videoInterruptor.doInterruption( pck );
			break;
			case MSGTypes.ACK:
				videoInterruptor.doInterruption(processId, 
						("-->Receving ACK: " + origin.getHostAddress() +  ", ID=" + pck.getMessageId()).getBytes(), origin);
				
				this.processAcknowledge( pck );
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
			videoInterruptor.doInterruption(0,  ("Coordinator sending clock petitions to: " + nodes.size()).getBytes(), null);
			this.createWaitingChronometer(vp, MAX_WAIT, nodes.size());
			
			for (InetAddress node: nodes) {
				try {
					vp.setTargetNode( node );
					this.send(node , vp);
				} catch (IOException e) {
					VideoException ve = new VideoException ( vp );
					ve.setErrorCode(MSGTypes.CANT_CONNECT_ERROR);
					ve.setStackTrace(e.getStackTrace());
					this.videoInterruptor.doInterruption(ve);
				}
			}
			 
		} else {
			videoInterruptor.doInterruption(0,  "Global Clock interruption... not coordinator".getBytes(), null);
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
	public VideoInterruption getVideoInterruption () {
		return videoInterruptor;
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
	
	public void setWaitingAcquireResponses(boolean isWaitingAcquireResponses) {
		this.isWaitingAcquireResponses = isWaitingAcquireResponses;
	}

	public boolean isWaitingAcquireResponses() {
		return isWaitingAcquireResponses;
	}


}


package com.sstream.middleware;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;

public class VideoContext implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private CopyOnWriteArrayList <InetAddress> nodes ; // connected nodes
	private CopyOnWriteArrayList <InetAddress> waitQueue ; // priority queue to get coordinator lock.
	
	private InetAddress coordinator ;
	
	private int nextCoordinator = 0;
	private long globalTime ;
	
	public VideoContext ( ) {
		nodes 		= new CopyOnWriteArrayList <InetAddress> ();
		waitQueue	= new CopyOnWriteArrayList <InetAddress> ();
		coordinator = null;
	}
	
	public long getGlobalTime() {
		return globalTime;
	}

	public CopyOnWriteArrayList<InetAddress> getWaitQueue() {
		return waitQueue;
	}

	public void setGlobalTIme(long globalTime) {
		this.globalTime = globalTime;
	}
	public void addNode (InetAddress nc) {
		nodes.add(nc);
	}
	
	public boolean addOnWaitQueue (InetAddress node) {
		
		for ( int i = 0; i < waitQueue.size(); i++) {
			if( waitQueue.get(i).getHostAddress().equals(node.getHostAddress())) {
				return  false;
			}
		}
		waitQueue.add( node );
		return true;
		
	}
	public void setCoordinator (InetAddress node) {	
		coordinator = node;		
	}
	public InetAddress getCoordinator () {
		return coordinator;
	}
	public InetAddress getNextCoordinator () {
		if (waitQueue != null && waitQueue.size() > nextCoordinator ) return waitQueue.get( nextCoordinator++ ); 
		return null;
	}
	public CopyOnWriteArrayList<InetAddress> getNodes () {
		return nodes;
	}
	public void  setNodes (CopyOnWriteArrayList<InetAddress> nodes) {
		this.nodes = nodes;
	}
	public void  setWaitQueue (CopyOnWriteArrayList<InetAddress> waitQ) {
		this.waitQueue = waitQ;
	}
	public void restartWaitQueue () {
		nextCoordinator = 0;
	}
	
	public boolean removeNode (InetAddress node) {
		int temp = -1;
		for (int i=0 ; i<nodes.size(); i++) {
			if ( node.getHostAddress().equals(nodes.get(i).getHostAddress()) ) {
				temp = i;
			}
		}
		this.removeFromPriorityQueue( node ); // remove from priority queue if needed
		if ( temp > -1) {
			nodes.remove(temp);
			return true;
		}else  {
			return false;
		}
		
	}
	
	public void  removeFromPriorityQueue (  InetAddress node) {
		int temp = -1;
		for (int i=0 ; i<waitQueue.size(); i++) {
			if ( node.getHostAddress().equals(waitQueue.get(i).getHostAddress()) ) {
				temp = i;
			}
		}
		
		if ( temp >-1 ) {
			waitQueue.remove(temp);
		}
	}
	
	
	public boolean hasCoordinator () {
		return coordinator != null;
	}
	
	public int getNextCoordinatorIndex () {
		return nextCoordinator;
	}
}

package com.sstream.middleware;

/**
 * Sistemas Distribuidos
 * Miercoles 5 de Febrero 2014
 * 
 * CINVESTAV
 * 
 * 
 * Autores:
 * 
 * Juan Carlos Reyes Martinez
 * Hector Alejandro Saucedo Briseno.
 * Luis Angel Ramos Cobarruvias
 * 
 * 
 */
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

import com.sstream.middleware.util.MessageInterruption;

public class MiddlewareServer extends Thread{

	private static final int PORT = 5059; // server socket port
	private static final int MAX_LEN = 1024 * 10;
	
	// all nodes  would beehive as server and clients
	private DatagramSocket serverSocket ; 
	private DatagramSocket senderSocket ;
	private MessageInterruption mi;
	
	
	private boolean isThreadAlive ;
	
	public MiddlewareServer () {
		try {
	        serverSocket = new DatagramSocket(PORT);
	        senderSocket = new DatagramSocket ( );
	        // instantiates a datagramsocket for receiving the data  
	        
	      } // end try
	      catch (Exception ex) {
	        ex.printStackTrace( );
	      }
	}
	
	
	public void setInterruptionMessageListener (MessageInterruption mi) {
		this.mi = mi;
	}
	public void run () {
		isThreadAlive = true;
		
		
		while(isThreadAlive) {
			byte[ ] buffer = new byte[MAX_LEN];
			DatagramPacket datagram = new DatagramPacket(buffer, MAX_LEN);
			
	        try {
				serverSocket.receive(datagram);
				//System.out.println("Receiving package ..." + new String (datagram.getData()));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        
	        mi.doInterruption(0 , datagram.getData(), datagram.getAddress());
		}
	}
	
	
	public void stopServer () throws Exception{
		isThreadAlive = false;
		serverSocket.close( );
		senderSocket.close();
	}
	
	public void sendMessage (InetAddress ip , String message) throws IOException {
		
		DatagramPacket datagram =
		          new DatagramPacket(message.getBytes(), message.getBytes().length,
		                             ip, PORT);
		senderSocket.send(datagram);
	}
	public void sendMessage (InetAddress ip , byte [] message) throws IOException {
		
		DatagramPacket datagram =
		          new DatagramPacket(message, message.length, ip, PORT);
		senderSocket.send(datagram);
	}
	
	public InetAddress getHost () {
		
		try {
			
			return InetAddress.getByName("localhost");
		} catch (UnknownHostException e) {
			return null;
		}
	
	}
	
	public static boolean isLocalAddress ( InetAddress i) {
		if (i.isAnyLocalAddress() || i.isLoopbackAddress()) return true;
		
		try {
			return NetworkInterface.getByInetAddress(i) != null;
		} catch (SocketException e) {
			return false;
		}
	}
	
}

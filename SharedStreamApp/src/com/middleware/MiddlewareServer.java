package com.middleware;

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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

import com.middleware.util.MessageInterruption;
import com.middleware.util.VideoPackage;

public class MiddlewareServer extends Thread{

	private static final int PORT = 5059; // server socket port
	private static final int MAX_LEN = 1024 * 10;
	
	// all nodes  would beehive as server and clients
	private DatagramSocket serverSocket ; 
	private DatagramSocket senderSocket ;
	private MessageInterruption mi;
	private boolean isThreadAlive ;
	private InetAddress host = null;
	
	public MiddlewareServer (InetAddress host) {
		this.host = host;
		try {
	        serverSocket = new DatagramSocket(PORT);
	        senderSocket = new DatagramSocket ( );
	        // instantiates a datagramsocket for receiving the data  
	        
	      } // end try
	      catch (Exception ex) {
	        ex.printStackTrace( );
	      }
	}
	
	public InetAddress getHost() {
		return host;
	}
	
	public void setHost(InetAddress host) {
		this.host = host;
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
	
	public static boolean isLocalAddress ( InetAddress i) {
		if (i.isAnyLocalAddress() || i.isLoopbackAddress()) return true;
		
		try {
			return NetworkInterface.getByInetAddress(i) != null;
		} catch (SocketException e) {
			return false;
		}
	}
	
	public byte[] unwrapMessage(VideoPackage vp) throws IOException {
		
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
	
	
	public VideoPackage wrapMessage (byte [] data) throws IOException, ClassNotFoundException {
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
	

}

package com.sstream.tcp;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.LinkedList;

import com.sstream.util.Constants;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

public class VideoServer {
	
	static final String TAG =  VideoServer.class.getName();
	private static final String LOCAL_SOCKET_ADDRESS = VideoServer.class.getName();
	private FileDescriptor fileDesc = null;
	private ServerSocket tcpServerSocket = null;
	private LocalServerSocket localServerSocket = null;
	private boolean receiving = false;
	private boolean streaming = false;
	private byte[] header = null;
	private Collection<ClientSocketHolder> clients = null;
	
	private Runnable localServerProcess = new Runnable() {
		
		@Override
		public void run() {		
			try{
				localServerSocket = new LocalServerSocket(LOCAL_SOCKET_ADDRESS);
				LocalSocket localSocket = localServerSocket.accept();
				streamVideo(localSocket.getInputStream());
			}catch(IOException ex){
				ex.printStackTrace();
			}finally{
				try{
					localServerSocket.close();
				}catch(IOException ex){
					Log.w(TAG, "....");
				}
				try{
					tcpServerSocket.close();
				}catch(IOException ex){
					Log.w(TAG, "....");
				}
			}
		}
	};
	
	private Runnable tcpServerProcess = new Runnable() {
		
		@Override
		public void run() {
			while (receiving) {
				try {
					Socket clientSocket = tcpServerSocket.accept();
					addClient(clientSocket);
				} catch (IOException ex) {
					Log.e(TAG, "TCP Server failed", ex);
				}

			}
			closeTCPServer();
		}
	};
	
	public VideoServer() throws IOException{
		clients = new LinkedList<ClientSocketHolder>();
		tcpServerSocket = new ServerSocket(Constants.PORT);
	}
	
	public void start(){
		if(tcpServerSocket.isClosed()){
			throw new IllegalStateException("The server is closed");
		}
		
		if(!receiving){
			receiving = true;
			Thread tcpThread = new Thread(tcpServerProcess);
			tcpThread.start();
		}
		
		if(!streaming){
			streaming = true;
			Thread localThread = new Thread(localServerProcess);
			localThread.start();
			try{Thread.sleep(100);}catch(InterruptedException ex){}
			connectToLocalServer();
		}else{
			Log.i(TAG, "Server already streaming");
		}
	}
	
	public void pause(){
		streaming = false;
	}
	
	public void close(){
		streaming = false;
		receiving = false;
	}
	
	public int getPort(){
		return Constants.PORT;
	}
	
	public FileDescriptor getFileDescriptor(){
		Log.e(TAG, fileDesc.valid()+"");
		return fileDesc;
	}
	
	private void connectToLocalServer(){
		try{
			LocalSocket localSocket = new LocalSocket();
			localSocket.connect(new LocalSocketAddress(LOCAL_SOCKET_ADDRESS));
			fileDesc = localSocket.getFileDescriptor();
		}catch(Exception ex){
			Log.e(TAG, "Problem connecting to local server", ex);
		}
	}
	

	
	private void addClient(Socket clientSocket){
		try{
			ClientSocketHolder client = new ClientSocketHolder(clientSocket);
			if(header != null){
				client.write(header, 0, header.length);
			}
			synchronized (clients) {
				clients.add(client);
			}
		}catch(IOException ex){
			Log.e(TAG, "Proble processing new client", ex);
		}
	}
	
	private void streamVideo(InputStream videoIn){
		try {
			if (header == null) {
				readHeader(videoIn);
			}
			while (receiving && streaming) {
				byte[] data = new byte[Constants.DATA_BUFFER_SIZE];
				int offset = 0;
				int bytesRead = 0;

				while (streaming && (bytesRead = videoIn.read(data, offset, Constants.DATA_BUFFER_SIZE - offset)) >= 0) {
					bytesRead += offset;
					if(bytesRead > 0 && bytesRead % Constants.PACKET_SIZE == 0){
						sendData(data, bytesRead);
						offset = 0;
					}else{
						offset = bytesRead;
					}
				}
			}
		} catch (Exception ex) {
			Log.e(TAG, "Problem sending video stream", ex);
		}finally{
			for(ClientSocketHolder client : clients){
				client.close();
			
			}
		}
	}
	
	private void readHeader(InputStream videoIn) throws IOException{
		byte[] data  = new byte[Constants.HEADER_SIZE];
		int offset = 0;
		int bytesRead = 0;
		header = null;
		while(header==null && (bytesRead = videoIn.read(data, offset, Constants.HEADER_SIZE-offset)) >= 0){
			bytesRead += offset;
			if(bytesRead == Constants.HEADER_SIZE){
				header = data;
				sendData(header, header.length);
			}else{
				offset = bytesRead;
			}
		}
	}
	
	private void sendData(byte[] data, int lenght){
		Collection<ClientSocketHolder> remClients = null;
		synchronized (clients) {
			for (ClientSocketHolder client : clients) {
				try {
					client.write(data, 0, lenght);
				} catch (IOException ex) {
					Log.w(TAG, "Error sending data to client:"+client.getHost(), ex);
					if(remClients == null){
						remClients = new LinkedList<ClientSocketHolder>();
					}
					remClients.add(client);
				}
			}
			if(remClients != null){
				for(ClientSocketHolder remClient : remClients){
					Log.w(TAG, "Removing client:"+remClient.getHost());
					clients.remove(remClient);
				}
				remClients.clear();
			}
		}
	}
	
	private void closeTCPServer(){
		for(ClientSocketHolder client : clients){
			client.close();
		}
		
		try{
			tcpServerSocket.close();
		}catch(Exception ex){
			Log.w(TAG, "Problem closing tcp server socket", ex);
		}
	}
}

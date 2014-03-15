package com.sstream.tcp;

import java.io.BufferedInputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import com.sstream.camera.StreamRecorder;
import com.sstream.util.Constants;

import android.hardware.Camera;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class VideoServer {
	
	static final String TAG =  VideoServer.class.getName();
	private static final String LOCAL_SOCKET_ADDRESS = VideoServer.class.getName();
	private static final int STATE_CREATED = 0;
	private static final int STATE_SLEEPING = 1;
	private static final int STATE_STREAMING = 2;
	private static final int STATE_WAITING = 3;
	private static final int STATE_CLOSED = 4;
	
	private ServerSocket tcpServerSocket = null;
	private LocalServerSocket localServerSocket = null;
	private volatile int state = 0;
	private List<ClientSocketHolder> clients = null;
	private StreamRecorder recorder = null;
	private byte streamFlag = 0;
	private LocalSocket videoSocket = null;
	private LocalSocket srcVideoSocket = null;
	private Thread senderThread = null;
	
	private Runnable tcpServerProcess = new Runnable() {
		
		@Override
		public void run() {
			while (state != STATE_CLOSED) {
				try {
					Socket clientSocket = tcpServerSocket.accept();
					if(state == STATE_STREAMING || state == STATE_WAITING){
						changeState(STATE_WAITING);
						stopStreaming(STATE_WAITING);
						addClient(clientSocket);
						stream(recorder.getCamera(), recorder.getSurface());
					}else{
						Log.w(TAG, "TCP Server is not streaming. Client conneciton refused.");
						try{
							clientSocket.close();
						}catch(Exception ex){
							
						}
					}
				} catch (IOException ex) {
					if(tcpServerSocket == null || tcpServerSocket.isClosed()){
						Log.w(TAG, "TCP Server closed");
					}else{
						Log.e(TAG, "TCP Server failed", ex);
					}
				}

			}
			closeTCPServer();
		}
	};
	
	private Runnable localServerProcess = new Runnable() {
		
		@Override
		public void run() {		
			try{
				localServerSocket = new LocalServerSocket(LOCAL_SOCKET_ADDRESS);
				while(state != STATE_CLOSED){
					videoSocket = localServerSocket.accept();
					if(state == STATE_STREAMING || state == STATE_WAITING){
						streamFlag = (byte) Math.abs(streamFlag - 1);
						sendVideo();
					}else{
						try{
							Log.w(TAG, "Local socket received but local server is not streaming");
							videoSocket.close();
						}catch(Exception ex){
							Log.w(TAG, "Problem closing local socket", ex);
						}finally{
							videoSocket = null;
						}
					}
				}
			}catch(IOException ex){
				if(localServerSocket == null){
					Log.w(TAG, "Local server closed");
				}else{
					Log.e(TAG, "Problem creating local server", ex);
				}
			}finally{
				if (localServerSocket != null) {
					try {
						localServerSocket.close();
					} catch (IOException ex) {
						Log.w(TAG, "Problem closing local server", ex);
					}
				}
			}
		}
	};
	
	
	public VideoServer(StreamRecorder recorder) throws IOException{
		clients = new ArrayList<ClientSocketHolder>();
		tcpServerSocket = new ServerSocket(Constants.PORT);
		state = STATE_SLEEPING;
		this.recorder = recorder;
		
		//start tcp server process
		Thread tcpThread = new Thread(tcpServerProcess);
		tcpThread.start();
		
		//start local server process
		Thread localThread = new Thread(localServerProcess);
		localThread.start();
	}
	
	public void stream(Camera camera, SurfaceView surface){
		if(state == STATE_SLEEPING || state == STATE_WAITING){
			changeState(STATE_STREAMING);
			recorder.setCamera(camera);
			recorder.setSurface(surface);
			startRecording();
		}
	}
	
	public void pauseRecorder(){
		if(state == STATE_SLEEPING || state == STATE_WAITING){
			stopStreaming(STATE_WAITING);
			changeState(STATE_WAITING);
		}
	}
	
	public void sleep(){
		if(state == STATE_STREAMING || state == STATE_WAITING){
			changeState(STATE_SLEEPING);
			stopStreaming(STATE_SLEEPING);
			disconnectClients();
		}
	}
		
	public void close(){
		if(state != STATE_CLOSED){
			changeState(STATE_CLOSED);
			stopStreaming(STATE_CLOSED);
			closeLocalServer();
			closeTCPServer();
		}
	}
	
	public int getPort(){
		return Constants.PORT;
	}
	

	
	private void changeState(int state){
		synchronized (clients) {
			this.state = state;
		}
	}
	
	private void startRecording(){
		(new Thread() {
			public void run() {
				try {
					srcVideoSocket = new LocalSocket();
					srcVideoSocket.connect(new LocalSocketAddress(LOCAL_SOCKET_ADDRESS));
					recorder.start(srcVideoSocket.getFileDescriptor());
				} catch (Exception ex) {
					Log.e(TAG, "starting to record", ex);
				}
			};
		}).start();
	}
	
	
	private void stopStreaming(int newState){
		if(newState == STATE_STREAMING){
			throw new IllegalArgumentException("Cannot close streaming process changing state to STREAMING");
		}
		if(state == STATE_STREAMING){
			changeState(newState);
		}
		
		int attempt = 1;
		do{
			if(videoSocket != null){
				try{
					Thread.sleep(100);
				}catch(InterruptedException ex){}
				attempt++;
			}
		}while(videoSocket != null);
		
		Log.e(TAG, "attempts:"+attempt);

//		try{
//			recorder.stop();
//		}catch(Error ex){
//			Log.e(TAG, "Problem stopping recorder", ex);
//		}
		
		

//		Log.w(TAG, "srcVideoSocket closed");
//
//		if(videoSocket != null && !videoSocket.isClosed()){
//			Log.e(TAG, "Streaming process does not finish!!");
//			try{
//				videoSocket.close();
//			}catch(Exception ex){
//				Log.w(TAG, "Problem closing videoSocket streams", ex);
//			}
//			try{
//				videoSocket.close();
//			}catch(Exception ex){
//				Log.w(TAG, "Problem closing videoSocket", ex);
//			}
//		}

	}
	
//	private boolean isStreamingFinished(){
//		int attempt = 1;
//		do{
//			if(videoSocket != null){
//				try{
//					Thread.sleep(50);
//				}catch(InterruptedException ex){}
//				attempt++;
//			}
//		}while(attempt<=3 && videoSocket != null);
//		return videoSocket == null;
//	}
	
	private void addClient(Socket clientSocket){
		try{
			ClientSocketHolder client = new ClientSocketHolder(clientSocket);
			synchronized (clients) {
				clients.add(client);
			}
		}catch(IOException ex){
			Log.e(TAG, "Proble processing new client", ex);
		}
	}
	
	private void sendVideo(){
		try {
			InputStream videoIn = videoSocket.getInputStream();
			byte[] data = new byte[Constants.DATA_BUFFER_SIZE];
			int bytesRead = 0;
			int offset = 1;

			while (state == STATE_STREAMING && (bytesRead = videoIn.read(data, offset, Constants.DATA_BUFFER_SIZE - offset)) >= 0) {
				bytesRead += offset;
				if (bytesRead == Constants.DATA_BUFFER_SIZE) {
					offset = 1;
					data[0] = streamFlag;
					writeToClients(data, bytesRead);
				} else {
					offset = bytesRead;
				}
			}
			
		} catch (Exception ex) {
			Log.e(TAG, "Problem sending video stream", ex);
		}finally{
			
			try{
				recorder.stop();
			}catch(Exception ex){
				Log.e(TAG, "Problem stopping recorder", ex);
			
			}
			
			try{
				if(videoSocket != null){
					videoSocket.close();
				}
			}catch(Exception ex){
				Log.w(TAG, "Problem closing video socket", ex);
			}finally{
				
			}

			
			
			videoSocket = null;
		}
	}
	
	private void writeToClients(byte[] data, int lenght){
		Collection<ClientSocketHolder> remClients = null;
		
		for (ClientSocketHolder client : clients) {
			try {
				client.write(data, 0, lenght);
			} catch (IOException ex) {
				Log.w(TAG, "Error sending data to client:" + client.getHost(),
						ex);
				if (remClients == null) {
					remClients = new LinkedList<ClientSocketHolder>();
				}
				remClients.add(client);
			}
			if (remClients != null) {
				synchronized (clients) {
					for (ClientSocketHolder remClient : remClients) {
						Log.w(TAG, "Removing client:" + remClient.getHost());
						clients.remove(remClient);
					}
				}
				remClients.clear();
			}
		}
	}
	
	private void closeTCPServer(){
		disconnectClients();
		if (tcpServerSocket != null) {
			try {
				tcpServerSocket.close();
			} catch (Exception ex) {
				Log.w(TAG, "Problem closing tcp server socket", ex);
			} finally {
				tcpServerSocket = null;
			}
		}
	}
	
	private void disconnectClients(){
		synchronized (clients) {
			while(!clients.isEmpty()){
				ClientSocketHolder client = clients.remove(0);
				client.close();
			}
		}
	}
	
	private void closeLocalServer(){
		if(localServerSocket != null){
			try{
				localServerSocket.close();
			}catch(Exception ex){
				Log.w(TAG, "Problem closing local server", ex);
			}finally{
				localServerSocket = null;
			}
		}
	}
}

package com.sstream.tcp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import android.util.Log;

public class ClientSocketHolder{
	private static final String TAG = ClientSocketHolder.class.getName();
	private Socket socket = null;
	private OutputStream os = null;
	private String host = null;
	
	
	public ClientSocketHolder(Socket socket) throws IOException{
		this.socket = socket;
		os = socket.getOutputStream();
		host = socket.getInetAddress().getHostName();
	}
	
	public void write(byte[] buff, int offset, int count) throws IOException{
		os.write(buff, offset, count);
		os.flush();
	}
	
	public void close(){
		try{
			os.flush();
		}catch(IOException ex){
			Log.w(TAG, "Problem flushing content before closing the socket: "+host, ex);
		}
		try{
			socket.close();
		}catch(IOException ex){
			Log.w(TAG, "Problem closing socket: "+host, ex);
		}
	}
	
	public Socket getSocket() {
		return socket;
	}
	
	public OutputStream getOutputStream(){
		return os;
	}
	
	public String getHost() {
		return host;
	}
}
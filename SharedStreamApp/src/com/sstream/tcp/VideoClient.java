package com.sstream.tcp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import android.util.Log;

import com.sstream.camera.StreamPlayer;
import com.sstream.util.Constants;
import com.sstream.util.FilePathProvider;

public class VideoClient {

	public static final String TAG = VideoClient.class.getName();
	private boolean receiving = false;
	private StreamPlayer player = null;
	private FilePathProvider fileManager = null;
	private String host = null;
	private Socket socket = null;
	private byte[] header = null;
	private File currFile = null;
	private OutputStream currFileOut = null;
	private long currFileLength = 0;

	private Runnable receiveProcess = new Runnable() {

		@Override
		public void run() {
			InputStream videoIn = null;
			try {
				socket = new Socket(host, Constants.PORT);
				videoIn = socket.getInputStream();
				receiving = true;
			} catch (IOException ex) {
				Log.e(TAG, "Problem connecting to host:" + host, ex);
			}
			if (videoIn != null) {
				try {
					readHeader(videoIn);
					if (header != null) {
						while (receiving && socket.isConnected()) {
							receiveStream(videoIn);
						}
					}
				} catch (Exception ex) {
					Log.e(TAG, "Problem receiving stream", ex);
				}finally{
					try{
						socket.close();
					}catch(Exception ex){}
				}
			}
		}
	};

	public VideoClient(FilePathProvider fileManager) {
		this.fileManager = fileManager;
	}
	
	public void setPlayer(StreamPlayer player) {
		this.player = player;
	}
	
	public StreamPlayer getPlayer() {
		return player;
	}

	public void connect(String host) {
		this.host = host;
		Thread receiveThread = new Thread(receiveProcess);
		receiveThread.start();
	}
	
	public void close(){
		receiving = false;
		try{
			socket.close();
		}catch(IOException ex){}
	}
	
	public void filePlayed(File videoFile){
		if(videoFile != null && !videoFile.equals(currFile)){
			fileManager.delete(videoFile);
		}
	}
	
	public File getCurFile(){
		return currFile;
	}
	
	public long getCurrFileLength() {
		return currFileLength;
	}
	
	private void readHeader(InputStream videoIn){
		byte[] data = new byte[Constants.HEADER_SIZE];
		int offset = 0;
		int bytesRead = 0;
		header = null;
		int realHeaderSize = Constants.PACKET_SIZE*5;
		try {
			while (header == null && (bytesRead = videoIn.read(data, offset, Constants.HEADER_SIZE- offset)) >= 0) {
				bytesRead += offset;
				if(bytesRead == Constants.HEADER_SIZE){
					header = new byte[realHeaderSize];
					//header = data;
					System.arraycopy(data, 0, header, 0, realHeaderSize);
				}else{
					offset = bytesRead;
				}
			}
		} catch (IOException ex) {
			Log.e(TAG, "Problem reading header", ex);
		}
	}

	private void receiveStream(InputStream videoIn) {
		byte[] data = new byte[Constants.DATA_BUFFER_SIZE];
		int offset = 0;
		int bytesRead = 0;
		try {
			while (receiving && (bytesRead = videoIn.read(data, offset, Constants.DATA_BUFFER_SIZE - offset)) >= 0) {
				bytesRead += offset;
				if(bytesRead > 0 && bytesRead % Constants.PACKET_SIZE == 0){
					saveStream(data, bytesRead);
					offset = 0;
				}else{
					offset = bytesRead;
				}
			}
		} catch (IOException ex) {
			Log.e(TAG, "Problem receiving stream", ex);
		}
	}
	
	private boolean currFileAdded = false;
	
	private void saveStream(byte[] data, int length) throws IOException{
		if(currFile == null){
			createNewFile();
		}
		try{
			currFileOut.write(data, 0, length);
			currFileOut.flush();
			currFileLength += length;
			if(!currFileAdded && currFileLength >= Constants.MIN_PLAYABLE_FILE_SIZE && player != null){
				player.addVideoFile(currFile);
				currFileAdded = true;
			}
			if(currFileLength >= Constants.MAX_FILE_SIZE){
				closeCurrFileOut();
				createNewFile();
			}
		}catch(IOException ex){
			Log.e(TAG, "Problem writing to file", ex);
		}
	}
	
	private void createNewFile() throws IOException{
		currFile = fileManager.createMediaFile(FilePathProvider.MEDIA_TYPE_VIDEO, Constants.VIDEO_FILE_EXTENSION);
		currFileLength = Constants.HEADER_SIZE;
		currFileAdded = false;
		try{
			currFileOut = new FileOutputStream(currFile);
			currFileOut.write(header, 0, header.length);
			currFileOut.flush();
		}catch(IOException ex){
			Log.e(TAG, "Problem opening new file", ex);
			throw ex;
		}
	}
	
	private void closeCurrFileOut(){
		if(currFileOut != null){
			try{
				currFileOut.flush();
				currFileOut.close();
			}catch(IOException ex){
				Log.w(TAG, "Problem closing current file", ex);
			}finally{
				currFileOut = null;
				currFile = null;
			}
		}		
	}

}

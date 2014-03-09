package com.sstream.tcp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import com.sstream.camera.StreamPlayer;
import com.sstream.util.Constants;
import com.sstream.util.FilePathProvider;

import android.hardware.Camera;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceView;

public class VideoClient {

	public static final String TAG = VideoClient.class.getName();
	private Camera camera = null;
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
				}
			}
		}
	};

	public VideoClient(StreamPlayer player, FilePathProvider fileManager) {
		this.player = player;
		this.fileManager = fileManager;
	}

	public void connect(String host) {
		this.host = host;
		Thread receiveThread = new Thread(receiveProcess);
		receiveThread.start();
	}
	
	public void filePlayed(File videoFile){
		if(videoFile != null && !videoFile.equals(currFile)){
			//TODO delete
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
		try {
			while (header == null && (bytesRead = videoIn.read(data, offset, bytesRead- offset)) >= 0) {
				bytesRead += offset;
				if(bytesRead == Constants.HEADER_SIZE){
					header = data;
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
			while (receiving && (bytesRead = videoIn.read(data, offset, bytesRead- offset)) >= 0) {
				bytesRead += offset;
				if(bytesRead > 0 && bytesRead % Constants.PACKET_SIZE == 0){
					saveStream(data, bytesRead);
				}else{
					offset = bytesRead;
				}
			}
		} catch (IOException ex) {
			Log.e(TAG, "Problem receiving stream", ex);
		}
	}
	
	private void saveStream(byte[] data, int length) throws IOException{
		if(currFile == null || currFileLength + length > Constants.MAX_FILE_SIZE){
			closeCurrFileOut();
			createNewFile();
		}
		try{
			currFileOut.write(data, 0, length);
			currFileLength += length;
		}catch(IOException ex){
			Log.e(TAG, "Problem writing to file", ex);
		}
	}
	
	private void createNewFile() throws IOException{
		currFile = fileManager.createMediaFile(FilePathProvider.MEDIA_TYPE_VIDEO, Constants.VIDEO_FILE_EXTENSION);
		currFileLength = 0;
		try{
			currFileOut = new FileOutputStream(currFile);
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
				currFileOut = null;
			}catch(IOException ex){
				Log.w(TAG, "Problem closing current file", ex);
			}
		}		
	}

}

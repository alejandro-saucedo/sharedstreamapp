package com.sstream.tcp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import android.util.Log;

import com.sstream.MainActivity;
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
	private File currFile = null;
	private OutputStream currFileOut = null;
	private byte streamFlag = 0;

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
					byte[] data = new byte[Constants.DATA_BUFFER_SIZE];
					while (receiving && socket.isConnected()) {
						receiveStream(videoIn,data);
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
	
	private MainActivity mainActivity = null;

	public VideoClient(FilePathProvider fileManager, StreamPlayer player, MainActivity mainActivity) {
		this.fileManager = fileManager;
		this.player = player;
		this.mainActivity = mainActivity;
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
		player.stop();
		receiving = false;
		try{
			socket.close();
		}catch(IOException ex){
			socket = null;
		}
	}

	
	private void receiveStream(InputStream videoIn, byte[] data) {
		int offset = 0;
		int bytesRead = 0;
		try {
			while (receiving && (bytesRead = videoIn.read(data, offset, Constants.DATA_BUFFER_SIZE - offset)) >= 0) {
				bytesRead += offset;
				if(bytesRead == Constants.DATA_BUFFER_SIZE){
					if(data[0] != streamFlag || currFile == null){
						streamFlag = data[0];
						player.stop();
						closeCurrFileOut();
						createNewFile();
						player.start(currFile);
					}
					saveStream(data, 1, data.length-1);
					offset = 0;
				}else{
					offset = bytesRead;
				}
			}
		} catch (IOException ex) {
			if(receiving){
				Log.e(TAG, "Problem receiving stream", ex);
				mainActivity.serverDown(host);
			}
		}
	}
	
	
	private void saveStream(byte[] data, int offset, int length) throws IOException{
		try{
			currFileOut.write(data, offset, length);
			currFileOut.flush();
		}catch(IOException ex){
			Log.e(TAG, "Problem writing to file", ex);
		}
	}
	
	private void createNewFile() throws IOException{
		currFile = fileManager.createMediaFile(FilePathProvider.MEDIA_TYPE_VIDEO, Constants.VIDEO_FILE_EXTENSION);
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
			}catch(IOException ex){
				Log.w(TAG, "Problem closing current file", ex);
			}finally{
				currFileOut = null;
				currFile = null;
			}
		}		
	}

}

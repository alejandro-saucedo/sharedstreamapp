package com.sstream.camera;

import java.io.File;

import com.sstream.tcp.VideoClient;
import com.sstream.util.Constants;

import android.hardware.Camera;
import android.media.MediaPlayer;

public class StreamPlayer implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener{
	
	private static final String TAG = StreamPlayer.class.getName();
	private VideoClient videoSrc = null;
	private Camera camera = null;
	private MediaPlayer player = null;
	
	
	public void addVideoFile(File videoFile){
		
	}
	
	@Override
	public void onCompletion(MediaPlayer mp) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onPrepared(MediaPlayer mp) {
		// TODO Auto-generated method stub
		
	}

}

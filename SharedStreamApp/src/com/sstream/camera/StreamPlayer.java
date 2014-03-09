package com.sstream.camera;

import android.hardware.Camera;
import android.media.MediaPlayer;

public class StreamPlayer implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener{
	
	private static final String TAG = StreamPlayer.class.getName();
	private Camera camera = null;
	private MediaPlayer player = null;
	
	
	@Override
	public void onCompletion(MediaPlayer mp) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onPrepared(MediaPlayer mp) {
		// TODO Auto-generated method stub
		
	}

}

package com.sstream.camera;

import java.io.File;
import java.util.LinkedList;
import java.util.Queue;

import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.MediaController.MediaPlayerControl;

import com.sstream.tcp.VideoClient;
import com.sstream.util.Constants;

public class StreamPlayer implements MediaPlayer.OnPreparedListener,
		MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnVideoSizeChangedListener {

	private static final String TAG = StreamPlayer.class.getName();
	private static long WAIT_TIME = 100;

	private MediaPlayer mediaPlayer = null;
	private SurfaceView surface = null;
	private int seekPosition = 0;
	private File video = null;
	private volatile boolean playback = false;
	private long lastFileLengh = 0;
	private long playbackStartTime = 0;
	private long playbackEndTime = 0;
	
	private boolean prepared = false;
	private boolean sizeSet = false;
	
	public StreamPlayer(SurfaceView surface){
		this.surface = surface;
		mediaPlayer = new MediaPlayer();
	}

	public synchronized void start(File video){
		this.video = video;
		mediaPlayer = new MediaPlayer();
		seekPosition = 0;
		playback = true;
		(new Thread(){ public void run() { prepareMediaPlayer();}}).start();
	}
	
	public void stop(){
		playback = false;
		synchronized (this) {
			if(mediaPlayer != null){
				try{
					mediaPlayer.stop();
				}catch(Exception ex){
					Log.w(TAG, "Problem stopping mediaplayer");
				}
				try{
					mediaPlayer.release();
				}catch(Exception ex){
					Log.w(TAG, "Problem releasing media player");
				}
				
				mediaPlayer = null;
			}
		}
	}
	
	private synchronized void prepareMediaPlayer(){
		long buffTime = buffering();
		if(playback){
			try {
				seekPosition += buffTime;
				prepared = false;
				sizeSet = false;
				mediaPlayer.reset();
				mediaPlayer.setDataSource(video.getAbsolutePath());
				mediaPlayer.setDisplay(surface.getHolder());
				mediaPlayer.setOnCompletionListener(this);
				mediaPlayer.setOnPreparedListener(this);
				mediaPlayer.setOnErrorListener(this);
				mediaPlayer.setOnVideoSizeChangedListener(this);
				mediaPlayer.prepareAsync();
			} catch (Exception ex) {
				Log.e(TAG, "Problem preparing media mediaPlayer", ex);
			}
		}
	}
	
	private long buffering(){
		long buffTime = 0;;
		while (playback && (video.length() - lastFileLengh) < Constants.MIN_PLAYABLE_FILE_SIZE){
			Log.d(TAG, "Buffering....");
			try{
				Thread.sleep(WAIT_TIME);
				buffTime += WAIT_TIME;
			}catch(InterruptedException ex){}
		}
		return buffTime;
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		playbackEndTime = System.currentTimeMillis() - playbackStartTime;
		if(playback){
			int currPos = mp.getCurrentPosition();
			if(currPos > 0){
				seekPosition = currPos;
			}else{
				seekPosition += playbackEndTime - 1000;
			}
			prepareMediaPlayer();
		}
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		prepared = true;
		Log.i(TAG, "MediaPlayer prepared. Duration:"+mp.getDuration()+" CurrPos:"+mp.getCurrentPosition());
		if(sizeSet){
			Log.i(TAG, "Moving MediaPlayer to position:"+seekPosition);
			mediaPlayer.seekTo(seekPosition);
			Log.i(TAG, "MediaPlayer position after moving: Duration:"+mp.getCurrentPosition());
		}
		mediaPlayer.start();
		playbackStartTime = System.currentTimeMillis();
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		String whatStr = null;
		switch(what){
		case MediaPlayer.MEDIA_ERROR_IO:
			whatStr = "MEDIA_ERROR_IO";
			break;
		case MediaPlayer.MEDIA_ERROR_MALFORMED:
			whatStr = "MEDIA_ERROR_MALFORMED";
			break;
		case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
			whatStr = "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK";
			break;
		case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
			whatStr = "MEDIA_ERROR_SERVER_DIED";
			break;
		case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
			whatStr = "MEDIA_ERROR_TIMED_OUT";
			break;
		case MediaPlayer.MEDIA_ERROR_UNKNOWN:
			whatStr = "MEDIA_ERROR_UNKNOWN";
			break;
		case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
			whatStr = "MEDIA_ERROR_UNSUPPORTED";
			break;
		default:
			whatStr = what+"";	
		}
		Log.e(TAG, "MediaPlayer OnError: what="+whatStr+" , extra="+extra);

		return true;
	}

	@Override
	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		if(width > 0 && height > 0){
			sizeSet = true;
			if(prepared){
				mp.seekTo(seekPosition);
			}
		}
		
	}

}

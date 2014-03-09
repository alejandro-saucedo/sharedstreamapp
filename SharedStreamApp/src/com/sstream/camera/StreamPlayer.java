package com.sstream.camera;

import java.io.File;
import java.util.LinkedList;
import java.util.Queue;

import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceHolder;

import com.sstream.tcp.VideoClient;
import com.sstream.util.Constants;

public class StreamPlayer implements MediaPlayer.OnPreparedListener,
		MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

	private static final String TAG = StreamPlayer.class.getName();
	private static final int STATE_READY = 1;
	private static final int STATE_PLAYING = 2;
	private static final int STATE_STOPPED = 3;
	private static final long WAIT_TIME = 50;
	private VideoClient videoSrc = null;
	private MediaPlayer mediaPlayer = null;
	private SurfaceHolder holder = null;
	private int seekPosition = 0;
	private File currVideo = null;
	private Queue<File> videoQueue = null;
	private volatile int state = 0;
	
	public StreamPlayer(VideoClient videoSrc, SurfaceHolder holder){
		this.videoSrc = videoSrc;
		this.holder = holder;
		mediaPlayer = new MediaPlayer();
		state = STATE_READY;
		videoQueue = new LinkedList<File>();
		videoSrc.setPlayer(this);
	}

	public void start(){
		synchronized (videoQueue) {
			state = STATE_READY;
			currVideo = videoQueue.poll();
			if(currVideo != null){
				state = STATE_PLAYING;
				prepareMediaPlayer(currVideo);
			}
		}
	}
	
	public void stop(){
		synchronized (videoQueue) {
			state = STATE_STOPPED;
			mediaPlayer.stop();
		}
	}
	
	public void addVideoFile(File videoFile) {

		synchronized (videoQueue) {
			videoQueue.add(videoFile);
			if(currVideo == null && state == STATE_READY){
				state = STATE_PLAYING;
				currVideo = videoQueue.poll();
				prepareMediaPlayer(currVideo);
			}
		}
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		long fileLength = currVideo.length();
		if(fileLength+Constants.MIN_PLAYABLE_FILE_SIZE >= Constants.MAX_FILE_SIZE){
			//move to next file in queue if any
			synchronized(videoQueue){
				//dispose played file
				videoSrc.filePlayed(currVideo);
				//get next file to play
				currVideo = videoQueue.poll();
				seekPosition = 0;
				if(currVideo == null){
					state = STATE_READY;
				}else{
					prepareMediaPlayer(currVideo);
				}
			}
		}else{
			int delay = 0;
			//wait for the file to load
			while( currVideo.length()-fileLength < Constants.MIN_PLAYABLE_FILE_SIZE ){
				try{
					Thread.sleep(WAIT_TIME);
					delay += WAIT_TIME;
				}catch(InterruptedException ex){
					ex.printStackTrace();
				}
			}
			seekPosition += delay;
			prepareMediaPlayer(currVideo);
		}
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		mediaPlayer.seekTo(seekPosition);
		state = STATE_PLAYING;
		mediaPlayer.start();
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

	private void prepareMediaPlayer(File videoFile) {
		try {
			mediaPlayer.reset();
			mediaPlayer.setDataSource(videoFile.getAbsolutePath());
			mediaPlayer.setDisplay(holder);
			mediaPlayer.setOnCompletionListener(this);
			mediaPlayer.setOnPreparedListener(this);
			mediaPlayer.setOnErrorListener(this);
			mediaPlayer.prepareAsync();
		} catch (Exception ex) {
			Log.e(TAG, "Problem preparing media mediaPlayer", ex);
			currVideo = null;
			state = STATE_READY;
		}
	}
}

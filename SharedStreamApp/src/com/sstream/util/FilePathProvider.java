package com.sstream.util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.os.Environment;
import android.util.Log;

public class FilePathProvider {
	public static final String TAG = FilePathProvider.class.getName();
	public static final int MEDIA_TYPE_VIDEO = 0;
	public static final int MEDIA_TYPE_IMAGE = 1;
	
	File mediaStorageDir = null;
	
	public FilePathProvider(String appName){
		mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), appName);
		if(!mediaStorageDir.exists()){
			if (! mediaStorageDir.mkdirs()){
	            Log.e(TAG, "failed to create directory: "+mediaStorageDir);
	        }
		}
	}

	public String createMediaFilePath(int mediaType, String extension){
		String filePath = null;
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
		String prefix = getFileNamePrefix(mediaType);
		
		if(extension == null){
			extension = getDefaultMediaType(mediaType);
		}
		
	    filePath = mediaStorageDir.getPath() + File.separator + prefix + timeStamp + "."+extension;
	   
	    return filePath;
	}
	
	public File createMediaFile(int mediaType, String extension){
		
		String filePath = createMediaFilePath(mediaType, extension);
	    File mediaFile = null;
	    
		try {
			File file = new File(filePath);
			boolean exists = file.exists();
			if(!exists){
				exists = file.createNewFile();
				if(exists){
					Log.d(TAG, "file created: " + file.getAbsolutePath());
				}
			}
			if(exists){
				mediaFile = file;
			}
		} catch (Exception ex) {
			Log.e(TAG, "problem creating file:" + mediaFile, ex);
		}

	    return mediaFile;
	}
	
	public String getDefaultMediaType(int mediaType){
		String extension = null;
		switch (mediaType) {
		case MEDIA_TYPE_IMAGE:
			extension = "jpg";
			break;

		case MEDIA_TYPE_VIDEO:
			extension = "mp4";
			break;
		}
		return extension;
	}
	
	private String getFileNamePrefix(int mediaType){
		String prefix = null;
		
		switch (mediaType) {
		case MEDIA_TYPE_IMAGE:
			prefix = "IMG_";
			break;

		case MEDIA_TYPE_VIDEO:
			prefix = "VID_";
			break;
		}
		
		return prefix;
	}
	
	public boolean delete(File file){
		return file.delete();
	}

	
}

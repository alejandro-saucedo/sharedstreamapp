package com.sstream.util;

public class Constants {
	
	public static final int PORT = 5678;
	public static final int PACKET_SIZE = 188;
	public static final int HEADER_SIZE = PACKET_SIZE*5;
	public static final int DATA_BUFFER_SIZE = PACKET_SIZE*10 ;
	public static final long MAX_FILE_SIZE = PACKET_SIZE*1000;
	public static final long MIN_PLAYABLE_FILE_SIZE = PACKET_SIZE * 100;
	public static final String VIDEO_FILE_EXTENSION = "3gp";
	

}

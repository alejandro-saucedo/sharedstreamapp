package com.sstream.middleware.util;

/**
 * Sistemas Distribuidos
 * Miercoles 5 de Febrero 2014
 * 
 * CINVESTAV
 * 
 * Autores:
 * 
 * Juan Carlos Reyes Martinez
 * Hector Alejandro Saucedo Briseno.
 * Luis Angel Ramos Cobarruvias
 * 
 * 
 */
public class MSGTypes {
	
	public static final int COORDINATOR_SETTED = -1; 
	
	public static final int COORDINATOR_RECORD_REQUIRE = -2; 
	
	public static final int COORDINATOR_DESIGNATED= -3; 
	
	public static final int ACK = -4; // Acknowledge
	
	public static final int RAW_MESSAGE = -5; // raw messages
	
	public static final int ACQUIRE = -6; // acquire
	
	public static final int AYA = -7; // are you alive ?
	
	public static final int IAA = -8; // I am alive
	
	public static final int RELEASE = -9; 
	
	public static final int DISCONNECTED = -10; 
	
	public static final int NEW_MEMBER = -11; //remove
	
	public static final int CAMERA_ERROR = -12; 
	
	public static final int ERROR_TRY_AGAIN = -13; //Generic error
	
	public static final int UPDATE_CONTEXT = -14; 
	
	public static final int NEW_CONTEXT = -15;
	
	public static final int NEW_CONNECTED_NODE = -16;
	
	public static final int ACQUIRE_VIDEO_NOT_POSIBLE = -17; 
	
	public static final int OK_START_RECORDING = -18; 
	
	public static final int OK_START_PLAYING = -19; 
	
	public static final int COORDINATOR_POSITION_AVAILABLE = -20; 
	
	// Clock 
	
	public static final int REQUEST_GLOBAL_CLOCK = -30;
	
	public static final int RESPONSE_GLOBAL_CLOCK = -31;
	
	public static final int SET_GLOBAL_CLOCK = -32;
	
	
	//ERRORS 
	public static final int CANT_CONNECT_ERROR = -41;
	
	public static final int NO_NEXT_COORDINATOR_WARNING = -42;
	
	public static final int SERVER_DOWN_ERROR = -43;
	
	public static final int TIMEOUT_WAITING_NEW_COORDINATOR= -44;
	
	
	
}

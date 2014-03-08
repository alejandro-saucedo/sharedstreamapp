package com.middleware.util;

/**
 * Sistemas Distribuidos
 * Miercoles 5 de Febrero 2014
 * 
 * CINVESTAV
 * 
 * 
 * Autores:
 * 
 * Juan Carlos Reyes Martinez
 * Hector Alejandro Saucedo Briseno.
 * Luis Angel Ramos Cobarruvias
 * 
 * 
 */
import java.net.InetAddress;

public interface MessageInterruption extends Interruption{

	public void doInterruption (long processId, byte [] message , InetAddress origin);
	
	public void doInterruption ( VideoInterface vp);
}

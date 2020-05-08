package com.incubatesoft.gateway;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GatewayApplication 
{
	private static Logger log = LogManager.getLogger(GatewayApplication.class.getName());
	
    public static void main( String[] args )
    {
    	log.info( "Gateway Application initialized !!" );   
        String serverPort = "";
        
     // Added if block (Sharanya) to take in port # from cmd line
        if (args.length > 0) {   
        	serverPort = args[0].toString();
        }
        
        Thread gateWayThread = new Thread(new DeviceConnectivity(serverPort));        
        gateWayThread.start();                        
    }
}

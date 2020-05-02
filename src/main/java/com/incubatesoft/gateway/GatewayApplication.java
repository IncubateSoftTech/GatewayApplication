package com.incubatesoft.gateway;


public class GatewayApplication 
{
    public static void main( String[] args )
    {
        System.out.println( "Gateway Application !" );   
        String serverPort = "";
        
     // Added if block (Sharanya) to take in port # from cmd line
        if (args.length > 0) {   
        	serverPort = args[0].toString();
        }
        
        Thread gateWayThread = new Thread(new DeviceConnectivity(serverPort));        
        gateWayThread.start();                        
    }
}

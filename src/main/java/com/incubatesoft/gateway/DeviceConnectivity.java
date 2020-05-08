package com.incubatesoft.gateway;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.incubatesoft.gateway.nats.NatsPublisher;

public class DeviceConnectivity implements Runnable{
	private static Logger log = LogManager.getLogger(DeviceConnectivity.class.getName());
	
	private ServerSocketChannel serverChannel;
	private Selector selector;
		
	private Locale locale = new Locale("en", "US");
	private ResourceBundle gatewayResourceBundle;	
	public static int GATEWAY_PORT ;	

	private SimpleDateFormat ftDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static ConcurrentHashMap<SocketChannel, Long>  mClientStatus = new ConcurrentHashMap<SocketChannel, Long>();
	public int clients = 0;
	private Map<SocketChannel, List<?>> dataTracking = new HashMap<SocketChannel, List<?>>();
	private ByteBuffer readBuffer = ByteBuffer.allocate(16384); 
	private Map<Channel, String> deviceState = new HashMap<Channel, String>(); 
	private Map<String,Long> deviceLastTimeStamp = new HashMap<String, Long>();
	private NatsPublisher natsPublisher = new NatsPublisher();
	
	public DeviceConnectivity(String portNumber) {
		try {
			gatewayResourceBundle = ResourceBundle.getBundle("com.incubatesoft.gateway.resources.gateway_config", locale);
			//Getting the default port from properties file -- Aditya
			GATEWAY_PORT = Integer.parseInt(gatewayResourceBundle.getString("GATEWAY_PORT"));
			
			// Assigning the port number passed from Console -- Aditya  
			GATEWAY_PORT = Integer.parseInt(portNumber);
		}catch (NumberFormatException nfx) {
			log.error(" Number format Exception occurred : ", nfx);
			nfx.printStackTrace();
	   }//end try-catch 
				
		init();
	}

	private void init() {
		try {		
			
			selector = Selector.open();
			serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(false);
			
			String ipAddress = gatewayResourceBundle.getString("GATEWAY_IP_ADDRESS");
			int backlog = Integer.parseInt(gatewayResourceBundle.getString("SOCKET_BACKLOG"));
			int ops = Integer.parseInt(gatewayResourceBundle.getString("KEY_INTEREST_SET"));
			
			log.debug(ipAddress + "---" + GATEWAY_PORT + "---" + backlog + "---" + ops);

			serverChannel.socket().bind(new java.net.InetSocketAddress(ipAddress, GATEWAY_PORT),backlog);
			serverChannel.register(selector, ops);					

		}catch(IOException ioexp) {
			log.error(" Exception is : ", ioexp);
			ioexp.printStackTrace();
		}catch(Exception exp) {
			log.error(" Exception is : ", exp);
			exp.printStackTrace();
		}		
	}

	public void run(){
		Date recDateTime = new Date();
		log.debug("Now accepting connections..." + ftDateTime.format(recDateTime));
		byte[] data;
		while (!Thread.currentThread().isInterrupted()) {
			try {				
				int ready = selector.select();
				if (ready != 0){
					Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
					while (keys.hasNext()) {				
						
						SelectionKey key = (SelectionKey)keys.next();
						keys.remove();
						if (key.isValid()){							
							
							if (key.isAcceptable()){								
								accept(key);
							}
							if (key.isReadable()){
								// broke down the tasks here - Sarat								
								data = read(key);
								processDeviceStream(key, data);
							}
						}
					}
				}
			}
			catch (Exception rExp) {         
				log.error(" Exception while accept() or read() : ", rExp);
				rExp.printStackTrace(); 
			}
		}        	
	}

	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel serverSocketChannel = (ServerSocketChannel)key.channel();
		SocketChannel socketChannel = serverSocketChannel.accept();
		mClientStatus.put(socketChannel, System.currentTimeMillis());
		if (socketChannel == null)
		{
			log.error(" Socket Channel is null ");
			throw new IOException("Socket Channel is null");
		}
		socketChannel.configureBlocking(false);
		clients++;
		socketChannel.register(selector, 1);
		dataTracking.put(socketChannel, new ArrayList<Object>());
	}

	/**
	 * Modified the Read function
	 * 
	 * @param key
	 * @return
	 * @throws IOException
	 * @author sarat
	 */
	private byte[] read(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel)key.channel();
		readBuffer.clear();
		int length = channel.read(readBuffer);
		byte[] data = null;

		// Checking whether length is negative, to overcome java.lang.NegativeArraySizeException -- Aditya
		if (length == -1) {
			mClientStatus.remove(channel);
			clients--;
			channel.close();
			key.cancel();
			log.error(" No data found ");
			throw new IOException("No data found");
		}else {
			readBuffer.flip();
			data = new byte[length];
			readBuffer.get(data, 0, length);						
		}
		return data;
	}

	/**
	 * Quick sampling of the data received
	 * @param data
	 * @return
	 * @author sarat
	 */
	private StringBuilder byteToStringBuilder(byte[] data) {
		StringBuilder sbLog = new StringBuilder();
		for (byte b : data) {
			sbLog.append(String.format("%02x", new Object[] { b }));
		}
		//System.out.println(" data packet : "+ sbLog);
		return sbLog;
	}

	/**
	 * This method sends the initial handshake response
	 * @param key
	 * @param flag
	 * @throws IOException
	 * @author sarat
	 */
	private void sendDeviceHandshake(SelectionKey key, boolean flag) throws IOException {
		SocketChannel channel = (SocketChannel)key.channel();
		byte[] ackData = new byte[1];
		if(flag) {
			ackData[0]=01;	
		}else {
			ackData[0]=00;
		}


		ByteBuffer bSend = ByteBuffer.allocate(ackData.length);
		bSend.clear();
		bSend.put(ackData);
		bSend.flip();
		while (bSend.hasRemaining()) {
			try {
				channel.write(bSend);
			} catch (IOException e) {
				log.error(" Failed to send acknowledgement ");
				throw new IOException("Failed to send acknowledgement");
			}

		}
	}

	/**
	 * This method sends the data reception complete message
	 * @param key
	 * @param data
	 * @throws IOException
	 * @author sarat
	 */
	private void sendDataReceptionCompleteMessage(SelectionKey key, byte[] data) throws IOException {
		SocketChannel channel = (SocketChannel)key.channel();
		byte[] ackData = new byte[4];
		ackData[0]=00;
		ackData[1]=00;
		ackData[2]=00;
		ackData[3]=data[9]; //bPacketRec[0];

		ByteBuffer bSend = ByteBuffer.allocate(ackData.length);
		bSend.clear();
		bSend.put(ackData);
		bSend.flip();
		while (bSend.hasRemaining()) {
			try {
				channel.write(bSend);
			} catch (IOException e) {
				log.error(" Could not send Data Reception Acknowledgement ");
				throw new IOException("Could not send Data Reception Acknowledgement");
			}
		}
	}

	/**
	 * This generic method is called to process the device input
	 * @param key
	 * @param data
	 * @throws IOException
	 * @author sarat
	 */
	private void processDeviceStream(SelectionKey key, byte[] data) throws IOException{
		SocketChannel channel = (SocketChannel)key.channel();
		String sDeviceID = "";
		// Initial call where the device sends it's IMEI# - Sarat
		if ((data.length >= 10) && (data.length <= 17)) {
			int ii = 0;
			for (byte b : data)
			{
				if (ii > 1) { // skipping first 2 bytes that defines the IMEI length. - Sarat
					char c = (char)b;
					sDeviceID = sDeviceID + c;
				}
				ii++;
			}
			// printing the IMEI #. - Sarat
			log.debug("IMEI#: "+sDeviceID);

			LinkedList<String> lstDevice = new LinkedList<String>();
			lstDevice.add(sDeviceID);
			dataTracking.put(channel, lstDevice);

			// send message to module that handshake is successful - Sarat
			try {
				sendDeviceHandshake(key, true);
			} catch (IOException e) {
				log.error(" Exception occurred at Device Handshake stage ",e);
			}
			// second call post handshake where the device sends the actual data. - Sarat
		}else if(data.length > 17){
			mClientStatus.put(channel, System.currentTimeMillis());
			List<?> lst = (List<?>)dataTracking.get(channel);
			if (lst.size() > 0)
			{
				//Saving data to database
				sDeviceID = lst.get(0).toString();
				deviceState.put(channel, sDeviceID);

				//String sData = org.bson.internal.Base64.encode(data);

				// printing the data after it has been received - Sarat
				StringBuilder deviceData = byteToStringBuilder(data);
				log.debug(" Data received from device : "+deviceData);
				
				try {
					//publish device IMEI and packet data to Nats Server
					natsPublisher.publishMessage(sDeviceID, deviceData);
				} catch(Exception nexp) {
					log.error(" Exception occurred at NATS Publish message stage ",nexp);					
				}

				//sending response to device after processing the AVL data. - Sarat
				try {
					sendDataReceptionCompleteMessage(key, data);
				} catch (IOException e) {
					log.error(" Exception occurred while sending response to device ",e);
				}
				//storing late timestamp of device
				deviceLastTimeStamp.put(sDeviceID, System.currentTimeMillis());

			}else{
				// data not good.
				try {
					sendDeviceHandshake(key, false);
				} catch (IOException e) {
					log.error(" Bad data received ",e.getMessage());
					throw new IOException("Bad data received");
				}
			}
		}else{
			// data not good.
			try {
				sendDeviceHandshake(key, false);
			} catch (IOException e) {
				log.error(" Bad data received ",e.getMessage());
				throw new IOException("Bad data received");
			}
		}
	}
}

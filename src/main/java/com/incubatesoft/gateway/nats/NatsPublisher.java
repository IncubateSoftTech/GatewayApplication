package com.incubatesoft.gateway.nats;



import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.ResourceBundle;

import org.json.JSONObject;

import io.nats.client.Connection;
import io.nats.client.Nats;

public class NatsPublisher {

	private ResourceBundle gatewayResourceBundle;
	private Locale locale = new Locale("en", "US");
	private JSONObject gatewayJson = new JSONObject();
	
	public NatsPublisher() {
		
	}
	
	/**
	 * the publishMessage() publishes data packet received from a device to the Nats Server
	 * @param deviceId, deviceData
	 * @author Aditya
	 */
	public void publishMessage(String deviceId, StringBuilder deviceData) {
		
		try {
			
			// Creating a JSON Object
			gatewayJson.put("deviceId", deviceId);
			gatewayJson.put("deviceData", deviceData.toString());			

			gatewayResourceBundle = ResourceBundle.getBundle("com.incubatesoft.gateway.resources.gateway_config",locale);
			
			// Connect to the NATS Server
			String natsUrl = gatewayResourceBundle.getString("NATS_SERVER_URL");					
			Connection natConn = Nats.connect(natsUrl);
            
            // Publish a message which includes both deviceId and deviceData
            String msgToPublish = gatewayJson.toString();                        

            natConn.publish("data_packet", msgToPublish.getBytes(StandardCharsets.UTF_8));

            // Make sure the message goes through before we close
            natConn.flush(Duration.ZERO);   
            natConn.close();
            // [end publish_bytes]
        } catch(IOException iexp) {
        	iexp.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
	}
}

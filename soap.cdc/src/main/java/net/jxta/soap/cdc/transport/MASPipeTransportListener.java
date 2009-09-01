
package net.jxta.soap.cdc.transport;


import it.polimi.si.mas.chain.TransportRequestChain;
import it.polimi.si.mas.message.MessageContext;
import it.polimi.si.mas.util.ControlManager;

import javax.microedition.io.*;

import net.jxta.document.AdvertisementFactory;
import net.jxta.endpoint.ByteArrayMessageElement;
import net.jxta.id.IDFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.OutputPipe;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.soap.cdc.JXTAUtils;
import net.jxta.soap.cdc.ServiceDescriptor;

import org.ksoap2.serialization.SoapObject;
import org.ksoap2.transport.Transport;
import org.ksoap2.*;
import org.xmlpull.v1.*;

import java.io.*;


public class MASPipeTransportListener extends Transport implements Runnable {

	private int port = ControlManager.getInstance().PORT;
	
	
	private static final long serialVersionUID = 1L; // to define compatible versions of classes for serialization 
    static int nextNum = 1 ;
  
    private PeerGroup peergroup;
    private PipeAdvertisement advert;
    private ServiceDescriptor descriptor;
    private OutputPipe outputpipe;
	
	
	
	
	/**
	 * The <code>ServerSocketConnection</code> used to establish the connection with the 
	 * service requestors.
	 */
	private ServerSocketConnection ssc = null;
	
	/**
	 * A data container for client-server dialog.
	 */
	private MessageContext messageContext;
	
	/**
	 * The next link of the chain.
	 */
	private TransportRequestChain tReqC;
	
	private static int MAX = 3000;
	
	/**
	 * Creates a new <code>TransportListener</code> object, sets some properties
	 * and instantiates a new <code>TransportRequestChain</code>.
	 * @param form a <code>Form</code> suitable for writing information on the J2ME
	 * display device.
	 */
	public MASPipeTransportListener() {	
		messageContext = new MessageContext();
		messageContext.isReq = true;
		messageContext.isResp = false;
		tReqC = new TransportRequestChain();
	}
	
	/**
	 * Starts the server and accept client connections.
	 * The connections is actually managed by <code>manageSocket</code>.
	 */
	public void run() {
		SocketConnection sc = null;	
		
		try {
			ssc = (ServerSocketConnection) Connector.open("socket://:" + port);
			ControlManager.getInstance().writeIntoLog("[TransportListener] ready to accept connection");
			while(true) {
				sc = (SocketConnection) ssc.acceptAndOpen();
				ControlManager.getInstance().writeIntoLog("[TransportListener] incoming connection catched from address: " + sc.getAddress());
				this.manageSocket(sc);
				sc.close();
			}
		} catch (IOException e) {
			ControlManager.getInstance().writeIntoLog("[TransportListener] connection error: IOException raised");
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Manage a <code>SocketConnection</code> between client and server.
	 * When HTTP request packet is stored in <code>MessageContext</code> it passes 
	 * the control to <code>TransportRequestChain</code>.
	 * 
	 * @param sc a <code>SocketConnection</code>.
	 * @throws IOException if problems occur during the connection.
	 */
	public void manageSocket(SocketConnection sc) throws IOException {
		DataInputStream dins = null; //from socket
		DataOutputStream douts = null; //from socket
		String requestPacket; //HTTP Request Packet to store in MessageContext
		String responsePacket; //HTTP Response Packet to store in MessageContext
		StringBuffer sb;
		/* Write to log  */
		ControlManager.getInstance().writeIntoLog("[TransportListener] manageSocket() method called");
		/* Write to form */
		ControlManager.getInstance().appendToForm("Got connection from client: " + sc.getAddress());
		try {
			dins = sc.openDataInputStream();
			douts = sc.openDataOutputStream();
	
			do {
				byte[] byteArray = new byte[MAX];
				int len = dins.read(byteArray);
				sb = new StringBuffer();
				for(int i=0; i<len; i++)
					sb.append((char) byteArray[i]);
			} while(!this.validate(sb.toString()));
			

			requestPacket = sb.toString();
			System.out.println("TRANSPORT LISTENER: la richiesta è " + requestPacket);
			if(requestPacket.indexOf("Content-Length") == -1) {
				ControlManager.getInstance().writeIntoLog("[TransportListener] received HTTP Request - ControlManager called");
				responsePacket = ControlManager.getInstance().manageBrowserRequest(requestPacket);
			}
			else {
				ControlManager.getInstance().writeIntoLog("[TransportListener] received data from client: \n" + requestPacket);
				messageContext.getRequestMessage().setHttpPacket(requestPacket);
				System.out.println("TRANSPORT LISTENER: dati dal message context del client " + messageContext.getRequestMessage().getMethodName());
				tReqC.invoke(messageContext);
				/* send HTTP Response message to the client */
				responsePacket = messageContext.getResponseMessage().getHttpPacket();
			}

			douts.write(responsePacket.getBytes());
			douts.flush(); //flush the buffer: VERY IMPORTANT
		} catch (Exception e) {
			ControlManager.getInstance().writeIntoLog("[TransportListener] Exception raised while sending-receiving data");
			e.printStackTrace();
		}
		finally {
			/* close streams */
			dins.close();
			douts.close();
			sc.close();
			dins = null;
			douts = null;
		}
	}
	
	/**
	 * Returns <code>true</code> if all characters have been received from client.
	 * @param packet a <code>String</code> representing HTTP packet received until now.
	 * @return
	 */
	private boolean validate(String packet) {
		final String endPacket = "\r\n\r\n";
		int cl = this.getContentLength(packet);
		
		if(cl == -1) {
			return packet.endsWith(endPacket); //browser request
		}
		int indexHeader = packet.indexOf(endPacket) + endPacket.length();
		
		return (packet.length() >= (indexHeader + cl));
	}
	
	/**
	 * Returns a <code>int</code> representing the vaulue of Content-Length property.
	 * @param packet HTTP packet
	 * @return
	 */
	private int getContentLength(String packet) {
		String par = "Content-Length:";
		int index0 = packet.indexOf(par);
		if(index0 == -1)
			return -1;
		int index1 = packet.indexOf(' ', index0) + 1;
		int index2 = packet.indexOf('\r', index1);
		return Integer.parseInt(packet.substring(index1, index2));
	}
	
	/**
	 * Close the connection before closing the entire program 
	 *
	 */
	public void closeConnection(){
		
		try {
			
			//Closing the connection
			ssc.close();
		} catch (IOException e) {
			
			ControlManager.getInstance().writeIntoLog("[TransportListener] connection error: IOException raised");
		}
	}
	
    
    public void getJXTAproperties(SoapObject obj)
    {
    this.peergroup= (PeerGroup)obj.getProperty( "peergroup" );
    this.advert = (PipeAdvertisement)obj.getProperty( "advertisement" );
    this.descriptor = (ServiceDescriptor)obj.getProperty( "descriptor" );  
    try {
    	this.outputpipe = (OutputPipe)obj.getProperty( "outputpipe" );
    }catch(Exception e) {
    	outputpipe=null;
    	}
    
    System.out.println(" -> KSOAPpipeTransport:call(...) - Service Pipe adv: " + advert.toString() );
    }
    
    
	 public void call(String targetNamespace, SoapEnvelope envelope) 
	 throws IOException, XmlPullParserException  {
		 System.out.println(" -> KSOAPpipeTransport:call(...)");
		 if (targetNamespace==null)
			 targetNamespace="\"\"";
		 try {
			    System.out.println(" -> KSOAPpipeTransport:call(...) - Service Pipe adv: " + advert.toString() );            
			    if ( outputpipe == null ) {
	                //get an output pipe to this service from its advertisement.
			int attempt = 1;
			boolean redo = true;
			do {
			    try {
				System.out.println(" -> KSOAPpipeTransport:call(...) - binding op with service ip... (" + attempt + ")");
				outputpipe = peergroup.getPipeService().createOutputPipe( advert, descriptor.getTimeout() );
				System.out.println("OK");
				System.out.println("outputpipe.getType() " + outputpipe.getType());
				System.out.println("outputpipe.getName() " + outputpipe.getName());
				redo = false;
			    } catch( Exception e ) {
				System.err.println(" Exception in remote binding phase! TIMEOUT expired!");
				e.printStackTrace();
				attempt++;
			    }
			} while( redo && attempt < 5 );
			
	                System.out.println(" -> KSOAPpipeTransport:call(...) - op created of type: " + outputpipe.getType() );
	            } 
	            else
	            	System.out.println(" -> KSOAPpipeTransport:call(...) - op yet available");
		    
	            //create the remote input pipe to sue
	            
	            PipeAdvertisement pipeadv = (PipeAdvertisement)
	                AdvertisementFactory.newAdvertisement( PipeAdvertisement.getAdvertisementType() );
	            
	            PeerGroupID pgid = peergroup.getPeerGroupID();
	            
	            pipeadv.setPipeID( IDFactory.newPipeID( pgid ) );
	            pipeadv.setName( "remote-input-pipe" );
	            // Add client peer unique ID in pipe advertisement description
	            // for identification purpose
	            pipeadv.setDescription( peergroup.getPeerID().toString() );

				boolean isMessageType = ( descriptor.getPolicyType().compareToIgnoreCase("WSS-based") == 0 ) ? true : false;
	            if ( descriptor.isSecure() && !isMessageType ) {
	            	System.out.println(" -> KSOAPpipeTransport:call(...) - create remote ip Unicast SECURE");
	                pipeadv.setType( "JxtaUnicastSecure" );
	            
	            } else {
	            	System.out.println(" -> JKSOAPpipeTransport:call(...) - create remote ip Unicast");
	                pipeadv.setType( "JxtaUnicast" );    

	            } 
	            
	            InputPipe remoteInputPipe = peergroup.getPipeService().createInputPipe( pipeadv );

	            net.jxta.endpoint.Message message = new net.jxta.endpoint.Message();

	                
	            byte[] requestData = createRequestData(envelope);
	            
	            //send the SOAP message 
	    	    // Send the whole request message, including attachments
	    	    ByteArrayMessageElement msgElement = 
	    		new ByteArrayMessageElement("message", 
	    					    null,
	    					    requestData,
	    					    null);
	    	    message.addMessageElement(msgElement);
	    	    requestData = null;
	    	    ByteArrayMessageElement ripElement = 
	    		new ByteArrayMessageElement("remote-input-pipe",
	    					    null,
	    					    JXTAUtils.toByteArray( pipeadv ),
	    					    null);
	    	    message.addMessageElement(ripElement);
	    	    
	    	    System.out.println(" -> KSOAPpipeTransport:call(...) - send SOAP Request (message + rip)");
	    	    
	    	    outputpipe.send( message );
	    	    outputpipe.close();
	    	    outputpipe=null;
	           
	    	    //now listen on the remoteInputPipe the result...
	    	    System.out.println(" -> KSOAPpipeTransport:call(...) - wait for responses from server");
	    	    
	                net.jxta.endpoint.Message msg = remoteInputPipe.poll( 30000 );
	                if( msg == null ) {
	    		System.out.println(" -> KSOAPpipeTransport:call(...) - Timeout expired or a null message received!");   	
	    		return;
	    	    }
	                System.out.println(" -> KSOAPpipeTransport:call(...) - OK, response arrived");
	    	    
	                ByteArrayMessageElement result = (ByteArrayMessageElement) msg.getMessageElement("message");
	                System.out.println(result.toString());
	                         
	                System.out.println(" -> KSOAPpipeTransport:call(...) - setResponseMessage(...)");

	                ByteArrayInputStream  is =  new ByteArrayInputStream( result.getBytes(true));

	                parseResponse(envelope,is);
	                
	            //    msgContext.setResponseMessage( new Message( new ByteArrayInputStream( result.getBytes(true) ) ) );

		 } catch (Throwable t ) {
	        	    
	               t.printStackTrace();

		 			}
	 }
}

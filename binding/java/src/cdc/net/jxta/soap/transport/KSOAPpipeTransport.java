package net.jxta.soap.transport;

import net.jxta.soap.JXTAUtils;
import net.jxta.soap.ServiceDescriptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.axis.AxisFault;
import org.apache.axis.Message;
import org.apache.axis.handlers.BasicHandler;

import net.jxta.document.AdvertisementFactory;
import net.jxta.endpoint.ByteArrayMessageElement;
import net.jxta.id.IDFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.OutputPipe;
import net.jxta.protocol.PipeAdvertisement;

import org.ksoap2.transport.*;
import org.ksoap2.*;
import org.ksoap2.serialization.*;
import org.kxml2.io.*;
import org.xmlpull.v1.*;
import org.ksoap2.serialization.SoapSerializationEnvelope;
/**
 * A File Transport class.
 * 
 */

public class KSOAPpipeTransport extends Transport {
    private static final long serialVersionUID = 1L; // to define compatible versions of classes for serialization 
    static int nextNum = 1 ;
  
    private PeerGroup peergroup;
    private PipeAdvertisement advert;
    private ServiceDescriptor descriptor;
    private OutputPipe outputpipe;
    

    
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

 




    
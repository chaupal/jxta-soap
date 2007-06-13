/*
 * Copyright (c) 2005 JXTA-SOAP Project
 *
 * Redistributions in source code form must reproduce the above copyright
 * and this condition. The contents of this file are subject to the
 * Sun Project JXTA License Version 1.1 (the "License"); you may not use
 * this file except in compliance with the License.
 * A copy of the License is available at http://www.jxta.org/jxta_license.html.
 *
 */


package net.jxta.soap.transport;

import net.jxta.soap.JXTAUtils;
import net.jxta.soap.ServiceDescriptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import net.jxta.document.AdvertisementFactory;
import net.jxta.endpoint.ByteArrayMessageElement;
import net.jxta.id.IDFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.OutputPipe;
import net.jxta.protocol.PipeAdvertisement;

import org.apache.axis.AxisFault;
import org.apache.axis.handlers.BasicHandler;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;

/**
 * A File Transport class.
 * 
 * @author <a href="mailto:burton@openprivacy.org">Kevin A. Burton</a>
 * @author <a href="mailto:danel698@student.liu.se">Daniel Elenius</a>
 */
public class JXTASOAPTransportSender extends BasicHandler {
    private static final long serialVersionUID = 1L; // to define compatible versions of classes for serialization 

    static int nextNum = 1 ;
    
    public void invoke( MessageContext msgContext ) throws AxisFault {
	System.out.println(" -> JXTASOAPTransportSender:invoke(...)");
	try { 
	    
	    PeerGroup peergroup = (PeerGroup)msgContext.getProperty( "peergroup" );
	    PipeAdvertisement advert = (PipeAdvertisement)msgContext.getProperty( "advertisement" );
	    ServiceDescriptor descriptor = (ServiceDescriptor)msgContext.getProperty( "descriptor" );
	    
	    OutputPipe output = (OutputPipe)msgContext.getProperty( "outputpipe" );
	    System.out.println(" -> JXTASOAPTransportSender:invoke(...) - Service Pipe adv: " + advert.toString() );            
	    
	    output = (OutputPipe) msgContext.getProperty("outputpipe");
	    if ( output == null ) {
                //get an output pipe to this service from its advertisement.
		int attempt = 1;
		boolean redo = true;
		do {
		    try {
			System.out.println(" -> JXTASOAPTransportSender:invoke(...) - binding op with service ip... (" + attempt + ")");
			output = peergroup.getPipeService().createOutputPipe( advert, descriptor.getTimeout() );
			System.out.println("OK");
			redo = false;
		    } catch( Exception e ) {
			System.err.println(" Exception in remote binding phase! TIMEOUT expired!");
			e.printStackTrace();
			attempt++;
		    }
		} while( redo && attempt < 5 );
		
                System.out.println(" -> JXTASOAPTransportSender:invoke(...) - op created of type: " + output.getType() );
            } 
            else
            	System.out.println(" -> JXTASOAPTransportSender:invoke(...) - op yet available");
	    
            //create the remote input pipe to sue
            
            PipeAdvertisement pipeadv = (PipeAdvertisement)
                AdvertisementFactory.newAdvertisement( PipeAdvertisement.getAdvertisementType() );
            
            PeerGroupID pgid = peergroup.getPeerGroupID();
            
            pipeadv.setPipeID( IDFactory.newPipeID( pgid ) );
            pipeadv.setName( "remote-input-pipe" );
            // Add client peer unique ID in pipe adv description
            // for identification purpose
            pipeadv.setDescription( peergroup.getPeerID().toString() );

			boolean isMessageType = ( descriptor.getPolicyType().compareToIgnoreCase("WSS-based") == 0 ) ? true : false;
            if ( descriptor.isSecure() && !isMessageType ) {
            	System.out.println(" -> JXTASOAPTransportSender:invoke(...) - create remote ip Unicast SECURE");
                pipeadv.setType( "JxtaUnicastSecure" );
            
            } else {
            	System.out.println(" -> JXTASOAPTransportSender:invoke(...) - create remote ip Unicast");
                pipeadv.setType( "JxtaUnicast" );    

            } 
            
            InputPipe remoteInputPipe = peergroup.getPipeService().createInputPipe( pipeadv );
            
            net.jxta.endpoint.Message message = new net.jxta.endpoint.Message();

	    //send the SOAP message 
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    msgContext.getRequestMessage().writeTo( baos );
	    
	    // Send the whole request message, including attachments
	    ByteArrayMessageElement msgElement = 
		new ByteArrayMessageElement("message", 
					    null,
					    baos.toByteArray(),
					    null);
	    message.addMessageElement(msgElement);
	    
	    ByteArrayMessageElement ripElement = 
		new ByteArrayMessageElement("remote-input-pipe",
					    null,
					    JXTAUtils.toByteArray( pipeadv ),
					    null);
	    message.addMessageElement(ripElement);
	    
	    System.out.println(" -> JXTASOAPTransportSender:invoke(...) - send SOAP Request (message + rip)");
	    
	    output.send( message );
	    output.close();
	    
	    //now listen on the remoteInputPipe the result...
	    System.out.println(" -> JXTASOAPTransportSender:invoke(...) - wait for responses from server");
	    
            net.jxta.endpoint.Message msg = remoteInputPipe.poll( 30000 );
            if( msg == null ) {
		System.out.println(" -> JXTASOAPTransportSender:invoke(...) - Timeout expired or a null message received!");
            	msgContext.setResponseMessage( null );
            	return;
	    }
            System.out.println(" -> JXTASOAPTransportSender:invoke(...) - OK, response arrived");
	    
            ByteArrayMessageElement result = (ByteArrayMessageElement) msg.getMessageElement("message");
            
            //FIXME: set the response message on the message context.
	    // what's to fix? -daniel
            System.out.println(" -> JXTASOAPTransportSender:invoke(...) - setResponseMessage(...)");
	    
            msgContext.setResponseMessage( new Message( new ByteArrayInputStream( result.getBytes(true) ) ) );
            
        } catch ( Throwable t ) {
	    
            t.printStackTrace();
            
            throw new AxisFault( t.getMessage() );
            
        }

    }

}

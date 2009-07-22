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


package net.jxta.soap.j2se.transport;

import net.jxta.soap.j2se.JXTAUtils;
import net.jxta.soap.j2se.ServiceDescriptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import net.jxta.endpoint.ByteArrayMessageElement;
import net.jxta.peergroup.PeerGroup;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.OutputPipe;
import net.jxta.protocol.PipeAdvertisement;

import org.apache.axis.AxisFault;
import org.apache.axis.handlers.BasicHandler;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * A File Transport class.
 * 
 */

public class JXTASOAPTransportSender extends BasicHandler {
    private static final long serialVersionUID = 1L; // to define compatible versions of classes for serialization
    
    private final static Logger LOG = Logger.getLogger(JXTASOAPTransportSender.class.getName());

    static int nextNum = 1 ;
    
    public void invoke( MessageContext msgContext ) throws AxisFault {
    LOG.info(" -> JXTASOAPTransportSender:invoke(...)");
	try { 
	    
	    PeerGroup peergroup = (PeerGroup)msgContext.getProperty( "peergroup" );
	    PipeAdvertisement advert = (PipeAdvertisement)msgContext.getProperty( "advertisement" );
	    ServiceDescriptor descriptor = (ServiceDescriptor)msgContext.getProperty( "descriptor" );
	    
	    OutputPipe output = (OutputPipe)msgContext.getProperty( "outputpipe" );
	    if( LOG.isEnabledFor(Level.INFO) )
	    	LOG.info(" -> JXTASOAPTransportSender:invoke(...) - Service Pipe adv: " + advert.toString() );            
	    
	    output = (OutputPipe) msgContext.getProperty("outputpipe");
	    if ( output == null ) {
                //get an output pipe to this service from its advertisement.
		int attempt = 1;
		boolean redo = true;
		long timeout = descriptor.getTimeout(); 
		do {
		    try {
		    LOG.info(" -> JXTASOAPTransportSender:invoke(...) - binding op with service ip... (" + attempt + ") timeout: " + timeout);
			output = peergroup.getPipeService().createOutputPipe( advert, timeout);
			LOG.info("OK");
			redo = false;
		    } catch( Exception e ) {
		    LOG.warn(" Exception in remote binding phase! TIMEOUT expired!", e);
			attempt++;
			timeout = timeout*2;
		    }
		} while( redo && attempt < 5 );
			LOG.info(" -> JXTASOAPTransportSender:invoke(...) - op created of type: " + output.getType() );
            } 
            else
            	LOG.info(" -> JXTASOAPTransportSender:invoke(...) - op yet available");
	    
            // return an old input pipe from pool, or create one if none is available
            
            InputPipe remoteInputPipe = null; 

			boolean isMessageType = ( descriptor.getPolicyType().compareToIgnoreCase("WSS-based") == 0 ) ? true : false;
            if ( descriptor.isSecure() && !isMessageType ) {
            	LOG.info(" -> JXTASOAPTransportSender:invoke(...) - create remote ip Unicast SECURE");
            	remoteInputPipe = RemoteInputPipePool.getInstance(peergroup).getSecureInputPipe();
            
            } else {
            	LOG.info(" -> JXTASOAPTransportSender:invoke(...) - create remote ip Unicast");
            	remoteInputPipe = RemoteInputPipePool.getInstance(peergroup).getInputPipe();

            } 
            
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
					    JXTAUtils.toByteArray( remoteInputPipe.getAdvertisement() ),
					    null);
	    message.addMessageElement(ripElement);
	    
	    LOG.info(" -> JXTASOAPTransportSender:invoke(...) - send SOAP Request (message + rip)");
	    
	    output.send( message );
	    output.close();
	    
	    //now listen on the remoteInputPipe the result...
	    LOG.info(" -> JXTASOAPTransportSender:invoke(...) - wait for responses from server");
	    
            net.jxta.endpoint.Message msg = remoteInputPipe.poll( 100000 );  // argh!
            if( msg == null ) {
            	LOG.info(" -> JXTASOAPTransportSender:invoke(...) - Timeout expired or a null message received!");
            	msgContext.setResponseMessage( null );
            	remoteInputPipe.close();
            	return;
	    }
            LOG.info(" -> JXTASOAPTransportSender:invoke(...) - OK, response arrived");
	    
            remoteInputPipe.close();
            ByteArrayMessageElement result = (ByteArrayMessageElement) msg.getMessageElement("message");
            
            //FIXME: set the response message on the message context.
	    // what's to fix? -daniel
            LOG.info(" -> JXTASOAPTransportSender:invoke(...) - setResponseMessage(...)");
	    
            msgContext.setResponseMessage( new Message( new ByteArrayInputStream( result.getBytes(true) ) ) );
            
        } catch ( Throwable t ) {
	    
            t.printStackTrace();
            
            throw new AxisFault( t.getMessage() );
            
        }

    }

}

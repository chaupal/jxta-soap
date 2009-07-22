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

import java.io.IOException;
import java.util.LinkedList;

import org.apache.log4j.Logger;

import net.jxta.document.AdvertisementFactory;
import net.jxta.endpoint.Message;
import net.jxta.id.ID;
import net.jxta.id.IDFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.PipeService;
import net.jxta.protocol.PipeAdvertisement;

/**
 * A pool of InputPipes to be used during the service invocation.
 *
 */
public class RemoteInputPipePool {
	
	private final static Logger LOG = Logger.getLogger(RemoteInputPipePool.class.getName());
	
	private static RemoteInputPipePool instance;
	private PeerGroup group;
	private PipeService pipeSvc;
	private LinkedList<InputPipe> freePipesArray = new LinkedList<InputPipe>();
	private LinkedList<InputPipe> freeSecurePipesArray = new LinkedList<InputPipe>();
	
	private RemoteInputPipePool(PeerGroup group){
		this.group = group;
		this.pipeSvc = group.getPipeService();
	}
	
	public static RemoteInputPipePool getInstance(PeerGroup group) {
		//TODO: return an instance for each peergroup
		if (instance == null) {
			instance = new RemoteInputPipePool(group);
		}
		return instance;
	}
	
	private class CacheableInputPipe implements InputPipe {
		
		private InputPipe pipe;
		
		public CacheableInputPipe(InputPipe pipe) {
			this.pipe = pipe;
		}

		public void close() { 
			LOG.debug("Returning input pipe to pool");
			if ("JxtaUnicast".equals(this.getType())) {
				freePipesArray.add(this);
			} else {
				freeSecurePipesArray.add(this);
			}
		}
		
		public PipeAdvertisement getAdvertisement() { return pipe.getAdvertisement(); }
		public String getName() { return pipe.getName(); }
		public ID getPipeID() { return pipe.getPipeID(); }
		public String getType() { return pipe.getType(); }
		public Message poll(int arg0) throws InterruptedException {
			return pipe.poll(arg0);
		}
		public Message waitForMessage() throws InterruptedException {
			return pipe.waitForMessage();
		}
		
	}
	
	public synchronized InputPipe getInputPipe()
	throws IOException {
		
		InputPipe pipe = null;
		
		if (!freePipesArray.isEmpty()) {
			
			pipe = freePipesArray.removeLast();
			LOG.debug("Returning old InputPipe from pool");
			
		} else {
			
			LOG.debug("Adding InputPipe to pool");
			PipeAdvertisement pipeadv = (PipeAdvertisement)
            AdvertisementFactory.newAdvertisement( PipeAdvertisement.getAdvertisementType() );
        
	        PeerGroupID pgid = group.getPeerGroupID();
	        
	        pipeadv.setPipeID( IDFactory.newPipeID( pgid ) );
	        pipeadv.setName( "remote-input-pipe" );
	        // Add client peer unique ID in pipe adv description
	        // for identification purpose
	        pipeadv.setDescription( group.getPeerID().getUniqueValue().toString() );
	        pipeadv.setType("JxtaUnicast");
	        
	        LOG.debug("Creating remoteInputPipe");
	
	        pipe = new CacheableInputPipe(pipeSvc.createInputPipe( pipeadv ));
		}
		
		return pipe;

	}
	
	public synchronized InputPipe getSecureInputPipe()
	throws IOException {
		
		InputPipe pipe = null;
		
		if (!freeSecurePipesArray.isEmpty()) {
			
			pipe = freeSecurePipesArray.removeLast();
			LOG.debug("Returning old secure InputPipe from pool");
			
		} else {
			
			LOG.debug("Adding secure InputPipe to pool");
			PipeAdvertisement pipeadv = (PipeAdvertisement)
            AdvertisementFactory.newAdvertisement( PipeAdvertisement.getAdvertisementType() );
        
	        PeerGroupID pgid = group.getPeerGroupID();
	        
	        pipeadv.setPipeID( IDFactory.newPipeID( pgid ) );
	        pipeadv.setName( "remote-input-pipe" );
	        // Add client peer unique ID in pipe adv description
	        // for identification purpose
	        pipeadv.setDescription( group.getPeerID().getUniqueValue().toString() );
	        pipeadv.setType("JxtaUnicastSecure");
	        
	        LOG.debug("Creating secure remoteInputPipe");
	
	        pipe = new CacheableInputPipe(pipeSvc.createInputPipe( pipeadv ));
		}
		
		return pipe;

	}

}

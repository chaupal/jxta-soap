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

package it.unipr.ce.dsg.soap.basic.client.cdc;

import java.io.File;
import java.io.IOException;
//import java.net.URI;

import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.Vector;


import net.jxta.discovery.DiscoveryService;
import net.jxta.document.StructuredTextDocument;
import net.jxta.document.TextElement;
import net.jxta.id.IDFactory;
import net.jxta.peer.PeerID;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupFactory;
//import net.jxta.peergroup.NetPeerGroupFactory;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.platform.NetworkConfigurator;
import net.jxta.protocol.ModuleSpecAdvertisement;
import net.jxta.rendezvous.RendezVousService;


//import jxta.security.util.URLBase64;

import net.jxta.soap.cdc.CallFactory;
//import net.jxta.soap.deploy.SOAPTransportDeployer;
import net.jxta.soap.cdc.ServiceDescriptor;
import net.jxta.soap.cdc.transport.KSoapPipeTransport;
import net.jxta.soap.cdc.util.Base64;

//import org.apache.axis.client.Call;   
import org.ksoap2.serialization.*;
import org.ksoap2.*;
//import org.ksoap2.transport.*;

public class HelloClientCDC { 

    private PeerGroup netPeerGroup = null;
    private boolean started = false;
    private boolean stopped = false;
    private RendezVousService rendezvous;
    private String instanceName = "NA";
    private final static File home = new File(System.getProperty("JXTA_HOME", ".jxta"));
    private ModuleSpecAdvertisement msadv = null;
    private String decodedWSDL = new String();
    
    public HelloClientCDC(String instanceName) {
       this.instanceName = instanceName;
    }


    /**
     * @param args the command line arguments
     */
    public static void main( String args[] ) {
    	System.setProperty("net.jxta.logging.Logging", "OFF");
    	HelloClientCDC consumerPeer = new HelloClientCDC("ConsumerPeer");
        System.out.println("Starting ConsumerPeer ....");
        consumerPeer.start("EDGE", "principal", "peerPassword", true);	
       // consumerPeer.authenticateToPSE();
        System.out.println("-------------------------------------------------");
        consumerPeer.findService();
        System.out.println("-------------------------------------------------");
        consumerPeer.interactWithService();
        System.out.println("-------------------------------------------------");
        System.out.println("Good Bye ....");
        consumerPeer.stop();
    }

  
    /**
     *  Creates and starts the JXTA NetPeerGroup using a platform configuration
     *  template. This class also registers a listener for rendezvous events
     *
     * @param  principal  principal used the generate the self signed peer root cert
     * @param  password   the root cert password
     */
    public synchronized void start(String nodeType, String principal, String password, boolean multicastOn) {
    	
        if (started) {
            return;
        }
        
        try {
            File instanceHome = new File(home, instanceName);
            NetworkConfigurator config = new NetworkConfigurator();
            config.setHome(instanceHome);
            if (!config.exists()) {
                config.setPeerID(IDFactory.newPeerID(PeerGroupID.defaultNetPeerGroupID));
                config.setName(instanceName);
                config.setDescription("Created by ConsumerPeer");
                if (nodeType.equals("EDGE"))
                	config.setMode(NetworkConfigurator.EDGE_NODE);
                else if (nodeType.equals("RDV"))
                	config.setMode(NetworkConfigurator.RDV_NODE);
                else if (nodeType.equals("ADHOC"))
                	config.setMode(NetworkConfigurator.ADHOC_NODE);
                config.setUseMulticast(multicastOn); 
                config.setPrincipal(principal);
                config.setPassword(password);
              //  try {
                	config.addRdvSeedingURI("http://dsg.ce.unipr.it/research/SP2A/rdvlist2.txt"); 
                 /*   config.addRdvSeedingURI(new URI("http://rdv.jxtahosts.net/cgi-bin/rendezvous.cgi?2"));
                    config.addRelaySeedingURI(new URI("http://rdv.jxtahosts.net/cgi-bin/relays.cgi?2"));
                } catch (java.net.URISyntaxException use) {
                    use.printStackTrace();
                }  */
                try {
                    config.save();
                } catch (IOException io) {
                    io.printStackTrace();
                }
            }
            // create, and Start the default jxta NetPeerGroup
            netPeerGroup = PeerGroupFactory.newNetPeerGroup();
            System.out.println("default jxta NetPeerGroup creato");
        //    NetPeerGroupFactory factory  = new NetPeerGroupFactory(config.getPlatformConfig(), instanceHome.toURI());
        //    netPeerGroup = factory.getInterface();
            System.out.println("Node PeerID :" + netPeerGroup.getPeerID().getUniqueValue().toString());
            rendezvous = netPeerGroup.getRendezVousService();           
        } catch (Exception e) {
            // could not instantiate the group, print the stack and exit
            System.out.println("fatal error : group creation failure");
            e.printStackTrace();
            System.exit(1);
        }
        started = true;
        if (nodeType.equals("EDGE") && !multicastOn)
        	this.waitForRendezvousConnection(500);
    }

    
    /**
     * Blocks if not connected to a rendezvous, or
     * until a connection to rendezvous node occurs
     *
     * @param  timeout  timeout in milliseconds
     */
    public void waitForRendezvousConnection(long timeout) {
        if (!rendezvous.isConnectedToRendezVous() || !rendezvous.isRendezVous()) {
        	int numAttempts = 1;
        	System.out.println(" Waiting to connect to a rendezvous... ");
        //	long startRDVsearch = System.nanoTime();
        	long startRDVsearch = System.currentTimeMillis();
        	while(!rendezvous.isConnectedToRendezVous()) {
        		try {
        	    	System.out.println("attempt " + numAttempts);
        	    	Thread.sleep(timeout);
        	    	numAttempts++;
        	    } catch( Exception e ) {
        	    	System.out.println("Exception in connecting to rdv!");
        	    	e.printStackTrace();
        	    	System.exit(1);
        	    }
        	}
        	long timeRDVsearch = System.currentTimeMillis() - startRDVsearch;
        	System.out.println("Time for discovering a RDV " + timeRDVsearch + " nsec");
        	// Ok, now the peer is connected to a RDV		    
        	System.out.println(" [CONNECTED] in " + numAttempts + " attempts");
        	PeerID rdvId = null;
        	Enumeration rdvEnum = rendezvous.getConnectedRendezVous();
    	    if( rdvEnum != null ) {
    	    	while( rdvEnum.hasMoreElements() ) {
    	    		rdvId = (PeerID) rdvEnum.nextElement();
    	    		if ( rdvId != null )
    	    			break;
    	    	}
    	    	System.out.println("I am connected to " + rdvId.toString());
    	    }
        }
    }

    /**
     * Discovering the specified service module spec adv (by Name)
     * and extracting WSDL service description	  
     */    
    private void findService() {
        System.out.println("\n### Find remote HelloService ###");
	
        // Look for printer advertisements
        this.discoverServices();
	
        String WSDLbuffer = null;
        // Print out the params of the service advertisement we found
        try {
            StructuredTextDocument param = (StructuredTextDocument) msadv.getParam();
	    
            try {
                System.out.println("\nSaving to file service param fields...");
                param.sendToStream( new FileOutputStream("ServiceParm.xml") );
            } catch( Exception e ) {
                System.out.println("Exception in saving service params section!");
                e.printStackTrace();
            }
	    
            // Now get all fields
            Enumeration params = param.getChildren();
            TextElement elem;
            String testtext = new String();
            while( params != null && params.hasMoreElements() ) {
                elem = (TextElement) params.nextElement();
                // Check for WSDL service description
                if( elem.getName().equals("WSDL") ) {
                    WSDLbuffer = new String( elem.getTextValue() );
                    try {
                        decodedWSDL = new String( Base64.decode( WSDLbuffer.getBytes(), 
								    0, WSDLbuffer.length(), 0 ) );
                        System.out.println(decodedWSDL); 
                    } catch(Exception ce ) {
                        ce.printStackTrace();
                        System.exit(1);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error extracting service advertisement");
            e.printStackTrace();
            System.exit(1);
        }
	
    
        System.out.println("\nCheck service invocation policy");
        System.out.println("=========================================");
        System.out.println("-> Setting unsecure service invocation...");
        System.out.println("=========================================");
    }
    

    /**
     * Utility method for discovering service advertisements
     */
    private void discoverServices(){
        int found = 0;
        int timeout = 500;     // standard timeout to wait for peers
        Object tempAdv;
      //  Vector<ModuleSpecAdvertisement> serviceAdvs = new Vector<ModuleSpecAdvertisement>();
        Vector serviceAdvs = new Vector();
        System.out.println("Searching for 'HelloService'");
        // Initialize Discovery Service
        DiscoveryService discoverySvc = netPeerGroup.getDiscoveryService();
        long startSVCsearch = System.currentTimeMillis();
        while( true ) {
            try {
                System.out.println("Looking for local advertisements...");
                Enumeration advs = discoverySvc.getLocalAdvertisements(DiscoveryService.ADV, 
                                    "Name", 
                                    "HelloService");
                if( advs != null && advs.hasMoreElements() ) {
                    while (advs.hasMoreElements()) {
                        // Make sure it is a ModuleSpecAdvertisement (we will also find
                        // ModuleClass and Pipe advertisements)
                        if ((tempAdv = advs.nextElement()) instanceof ModuleSpecAdvertisement) {
                            System.out.println("Found advertisement in cache, adding to list");
                            serviceAdvs.addElement((ModuleSpecAdvertisement) tempAdv);
                            ++found;
                        }
                    }
                    if( found > 0 )
                    { long timeSVCsearch = System.currentTimeMillis() - startSVCsearch;
                    System.out.println("Time for discovering a service " + timeSVCsearch + " nsec");
                        break;
                    }
                }
                System.out.println("Looking for remote advertisements...");
                discoverySvc.getRemoteAdvertisements(null, 
                            DiscoveryService.ADV, 
                            "Name", 
                            "HelloService", 5, null);
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                }
            } 
            catch( IOException e ) {
                // Found nothing! move on
            }
        }
    
        System.out.println("Found " + found + " advertisements(s).");
    
        Object element = serviceAdvs.firstElement();
        if (!(element instanceof ModuleSpecAdvertisement)){
            System.out.println("Something wrong with ModuleSpecAdv");
            System.exit(1);
        }
    
        msadv = (ModuleSpecAdvertisement) serviceAdvs.firstElement();
    }

    
    
    /* TODO : da qui devo usare ksoap e la classe factoryME
    /**
     * Interact with the discovered service
     */
    private void interactWithService() {
        try {
            System.out.println("\n# Create ServiceDescriptor for 'HelloService' from msadv");
            System.out.println("--------------------------------------------------------");
            ServiceDescriptor desc1 = new ServiceDescriptor("HelloService",
                                msadv,
                                null, // no policy 
                                netPeerGroup );	
            System.out.println( desc1.toString() );
        
          //  System.out.println("# Deploy SOAPTransport");    
          //  new SOAPTransportDeployer().deploy();
        
                
            System.out.println("# Creating Call object");                    
            CallFactory callFactory= CallFactory.getInstance();
            SoapObject call= callFactory.createCall( desc1, 
                   msadv.getPipeAdvertisement(),
                   netPeerGroup);     	 
            
            System.out.println("Create call object with properties for transport");
            KSoapPipeTransport transport= callFactory.getTransport(call);
            System.out.println("Numero di property:  " + call.getPropertyCount());
            System.out.println("SoapObject creato:   " + call.getName());
        
            
            SoapObject requestObject = new SoapObject("HelloService","SayHello" );
          //  requestObject.addProperty(desc1.getName(), "Hey JXTA peer!");
            requestObject.addProperty("in0", "REQ");

         //   SoapSerializationEnvelope envelope =callFactory.getEnvelope(requestObject);
            SoapSerializationEnvelope envelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
           int i = 5;
           long totalTime = 0;
            while( i-- > 0 ) {         
                envelope.setOutputSoapObject(requestObject);
                System.out.println("# Trying to invoke SOAP service...");
                long start = System.currentTimeMillis();
               transport.call("SayHello", envelope);
               long time = System.currentTimeMillis() - start;
               totalTime += time;
               try {
               System.out.println(envelope.getResponse());
             //  envelope= null;
               } catch (Exception e){ System.out.println("ERRORE!");}
  
               System.out.println(time);
            }  
            System.out.println("Average time for invocation: " + totalTime/5 + " nsec");
        } catch( Exception e ){
            System.out.println("Error invoking SOAP service!");
            e.printStackTrace();
            System.exit(1);
        }
    }    
    

    /**
     *  Stops and unrefrences the NetPeerGroup
     */
    public synchronized void stop() {
        if (stopped && !started) {
            return;
        }
        //rendezvous.removeListener(this);
        netPeerGroup.stopApp();
        netPeerGroup.unref();
        netPeerGroup = null;
        stopped = true;
    }

} 

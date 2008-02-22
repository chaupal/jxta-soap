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


import java.io.File;
import java.io.IOException;
import java.net.URI;

import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.Vector;
import javax.xml.namespace.QName;

import net.jxta.credential.AuthenticationCredential;
import net.jxta.credential.Credential;
import net.jxta.discovery.DiscoveryService;
import net.jxta.document.StructuredTextDocument;
import net.jxta.document.TextElement;
import net.jxta.id.IDFactory;
import net.jxta.impl.membership.pse.PSEMembershipService;
import net.jxta.impl.membership.pse.StringAuthenticator;
import net.jxta.membership.MembershipService;
import net.jxta.peer.PeerID;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.NetPeerGroupFactory;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.platform.NetworkConfigurator;
import net.jxta.protocol.ModuleImplAdvertisement;
import net.jxta.protocol.ModuleSpecAdvertisement;
import net.jxta.rendezvous.RendezVousService;

import jxta.security.exceptions.CryptoException;
import jxta.security.util.URLBase64;

import net.jxta.soap.CallFactory;
import net.jxta.soap.deploy.SOAPTransportDeployer;
import net.jxta.soap.ServiceDescriptor;

import org.apache.axis.client.Call;


public class HelloClient { 

    private PeerGroup netPeerGroup = null;
    private boolean started = false;
    private boolean stopped = false;
    private RendezVousService rendezvous;
    private String instanceName = "NA";
    private final static File home = new File(System.getProperty("JXTA_HOME", ".jxta"));
    private ModuleSpecAdvertisement msadv = null;
    private String decodedWSDL = new String();
    
    public HelloClient(String instanceName) {
       this.instanceName = instanceName;
    }


    /**
     * @param args the command line arguments
     */
    public static void main( String args[] ) {
    	System.setProperty("net.jxta.logging.Logging", "OFF");
    	HelloClient consumerPeer = new HelloClient("ConsumerPeer");
        System.out.println("Starting ConsumerPeer ....");
        consumerPeer.start("EDGE", "principal", "peerPassword", false);	
        consumerPeer.authenticateToPSE();
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
                config.setUseMulticast(multicastOn); 
                config.setPrincipal(principal);
                config.setPassword(password);
                try {
                	config.addRdvSeedingURI("http://dsg.ce.unipr.it/research/SP2A/rdvlist.txt"); 
                    config.addRdvSeedingURI(new URI("http://rdv.jxtahosts.net/cgi-bin/rendezvous.cgi?2"));
                    config.addRelaySeedingURI(new URI("http://rdv.jxtahosts.net/cgi-bin/relays.cgi?2"));
                } catch (java.net.URISyntaxException use) {
                    use.printStackTrace();
                }
                try {
                    config.save();
                } catch (IOException io) {
                    io.printStackTrace();
                }
            }
            // create, and Start the default jxta NetPeerGroup
            NetPeerGroupFactory factory  = new NetPeerGroupFactory(config.getPlatformConfig(), instanceHome.toURI());
            netPeerGroup = factory.getInterface();
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
        	this.waitForRendezvousConnection(5000);
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
     * Authenticates this peer to the PSEMembership Service
     * in order to enabling it to use the JXTA secure pipes
     */
    private void authenticateToPSE() {
        MembershipService membershipService = null;
        PSEMembershipService pseMembershipService = null;
        String passwordString;

        try {
            // Get the MembershipService
            membershipService = netPeerGroup.getMembershipService();
            
            // Checking if PSEMembershipService is initialized
            if( !(membershipService instanceof PSEMembershipService) ) {
                ModuleImplAdvertisement mia = (ModuleImplAdvertisement) membershipService.getImplAdvertisement();
                System.out.println("Group membership service is not PSE. (" + mia.getDescription() + ")");
                System.out.println("Exiting the application...");
                System.exit(1);
            }
            // Get the PSEMemberhipService for authentication process
            pseMembershipService = (PSEMembershipService) membershipService;

            System.out.println("\nJoining to NetPeerGroup PSE Membership Service");
            System.out.println("-------------------------------------------------");
            Credential cred = null;
            cred = pseMembershipService.getDefaultCredential();
        
            if( cred == null ) {
                AuthenticationCredential authCred = new AuthenticationCredential( netPeerGroup, "StringAuthentication", null );
                StringAuthenticator auth = null;
                try {
                    auth = (StringAuthenticator) pseMembershipService.apply( authCred );
                } catch( Exception failed ) {
                    System.out.println(" Exception in 'apply' phase!");
                    failed.printStackTrace();
                    System.exit(1);
                }
        
                if( auth != null ) {
                    passwordString = System.getProperty("net.jxta.tls.password","");
                    System.out.println("Getting system password...\t\t" + passwordString);
                    System.out.print("Setting keystore password...\t\t");
                    if( passwordString.length() == 0 ) {
                        // No password is set: setting system password
                        passwordString = "peerPassword"; 
                        System.setProperty("net.jxta.tls.password", passwordString);
                    }
                    auth.setAuth1_KeyStorePassword( passwordString.toCharArray());
                    System.out.println("OK");
            
                    System.out.print("Setting PeerID...\t\t\t");
                    auth.setAuth2Identity( netPeerGroup.getPeerID() );
                    System.out.println("OK");
            
                    System.out.print("Setting identity password...\t\t");
                    auth.setAuth3_IdentityPassword( passwordString.toCharArray() );
                    System.out.println("OK");
            
                    System.out.print("Joining to PSE membership service...\t");
                    int numAttempt = 1;
                    while( !auth.isReadyForJoin() ) {
                        try {
                            numAttempt++;
                            System.out.println("\nWaiting to join..");
                            Thread.sleep( 5000 );
                        } catch( Exception e ) {
                        }
                        if( numAttempt == 5 )
                            System.exit(1);
                    }
                    pseMembershipService.join( auth );
                    System.out.println("OK\n");
                }
            }
        } catch ( Exception e ) {
            System.out.println("Error in PSE authentication phase!");
            e.printStackTrace();
            System.exit(1);
        }
    }


    /**
     *  rendezvousEvent the rendezvous event
     *
     * @param  event  rendezvousEvent
     */
    /*
    public void rendezvousEvent(RendezvousEvent event) {
        if (event.getType() == event.RDVCONNECT ||
            event.getType() == event.RDVRECONNECT ||
            event.getType() == event.BECAMERDV) {
            switch(event.getType()) {
            case RendezvousEvent.RDVCONNECT :
                System.out.println("Connected to rendezvous peer :"+event.getPeerID());
                break;
            case RendezvousEvent.RDVRECONNECT :
                System.out.println("Reconnected to rendezvous peer :"+event.getPeerID());
                break;
            case RendezvousEvent.BECAMERDV :
                System.out.println("Became a Rendezvous");
                break;
            }
            synchronized (connectLock) {
                connectLock.notify();
            }
        }
    }
     */

    /**
     * Discovering the specified service module spec adv (by Name)
     * and extracting WSDL service description	  
     */    
    private void findService() {
        System.out.println("\n### Find remote HelloService ###");
	
        // Look for printer advertisements
        discoverServices();
	
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
                        decodedWSDL = new String( URLBase64.decode( WSDLbuffer.getBytes(), 
								    0, WSDLbuffer.length() ) );
                    } catch( CryptoException ce ) {
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
	
	/*
        // Print out the decoded WSDL
        if( LOG.isEnabledFor(Level.INFO) ) {
            System.out.println("Print out decoded WSDL content:");
            System.out.println( decodedWSDL );
        }
	*/
    
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
        int timeout = 3000;     // standard timeout to wait for peers
        Object tempAdv;
        Vector<ModuleSpecAdvertisement> serviceAdvs = new Vector<ModuleSpecAdvertisement>();
    
        System.out.println("Searching for 'HelloService'");
        // Initialize Discovery Service
        DiscoveryService discoverySvc = netPeerGroup.getDiscoveryService();
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
                        break;
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
        
            System.out.println("# Deploy SOAPTransport");    
            new SOAPTransportDeployer().deploy();
        
            // Write the wsdl to a file. This is needed because of a bug in Axis.
            // The Service constructor in Axis that takes wsdl as an InputStream
            // doesn't work.
            System.out.println("# Write Service WSDL to file");	    
            File wsdlfile = new File("HelloService.wsdl");
            FileOutputStream fos = new FileOutputStream(wsdlfile);
            fos.write( decodedWSDL.getBytes(), 0, (int) decodedWSDL.length() );
            fos.close();
                
            System.out.println("# Creating Call object");
            Call call = CallFactory.
                getInstance().createCall( desc1, 
                   msadv.getPipeAdvertisement(),
                   netPeerGroup,
                   new String( "HelloService.wsdl" ),                        
                   new QName("http://DefaultNamespace", "HelloServiceService"),  // servicename
                   new QName("http://DefaultNamespace", "HelloService") );       // portname
        
            System.out.println("# setOperation: \"SayHello\"");	    
            call.setOperationName( new QName(desc1.getName(), "SayHello"));
            call.setTimeout( new Integer(20000));
        
            int i = 3;
            while( i-- > 0 ) {
                System.out.println("# Trying to invoke SOAP service...");
                String res = (String)call.invoke(new Object[] { "Hey JXTA peer!" } );
                System.out.println(res);
            }
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

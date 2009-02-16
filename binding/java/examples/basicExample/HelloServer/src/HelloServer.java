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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import jxta.security.util.URLBase64;

import net.jxta.credential.AuthenticationCredential;
import net.jxta.credential.Credential;
import net.jxta.document.Element;
import net.jxta.document.MimeMediaType;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.document.StructuredTextDocument;
import net.jxta.id.IDFactory;
import net.jxta.impl.membership.pse.PSEMembershipService;
import net.jxta.impl.membership.pse.StringAuthenticator;
import net.jxta.membership.MembershipService;
import net.jxta.peer.PeerID;
import net.jxta.peergroup.NetPeerGroupFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.platform.NetworkConfigurator;
import net.jxta.protocol.ModuleImplAdvertisement;

import net.jxta.rendezvous.RendezVousService;
import net.jxta.soap.ServiceDescriptor;
import net.jxta.soap.SOAPServiceThread;
import net.jxta.soap.SOAPService;


public class HelloServer {

    private PeerGroup netPeerGroup = null;
    private boolean started = false;
    private boolean stopped = false;
    private String userLogLevel = "WARN";
    private String instanceName = "NA";
    private RendezVousService rendezvous;
    private final static File home = new File(System.getProperty("JXTA_HOME", ".jxta"));

    public HelloServer(String instanceName) {
       this.instanceName = instanceName;
    }


    /**
     * @param args the command line arguments
     */
    public static void main( String args[] ) {
    	System.setProperty("net.jxta.logging.Logging", "OFF");
    	HelloServer providerPeer = new HelloServer("ProviderPeer");
        System.out.println("Starting ProviderPeer ....");
        providerPeer.start("RDV", "principal", "peerPassword", true);	
        providerPeer.authenticateToPSE();
        System.out.println("-------------------------------------------------");
        providerPeer.createSOAPService();
        providerPeer.stop();
    }


    /**
     * Read peer parameters from XML profile file and start
     * the JXTA platform	 
     */
    private void start(String nodeType, String principal, String password, boolean multicastOn) {
        try {
            File instanceHome = new File(home, instanceName);
            NetworkConfigurator config = new NetworkConfigurator();
            config.setHome(instanceHome);
            if (!config.exists()) {
                config.setPeerID(IDFactory.newPeerID(PeerGroupID.defaultNetPeerGroupID));
                config.setName(instanceName);
                config.setDescription("Created by ProviderPeer");
                if (nodeType.equals("EDGE"))
                	config.setMode(NetworkConfigurator.EDGE_NODE);
                else if (nodeType.equals("RDV"))
                	config.setMode(NetworkConfigurator.RDV_NODE);
                else if (nodeType.equals("ADHOC"))
                	config.setMode(NetworkConfigurator.ADHOC_NODE);
                config.setUseMulticast(multicastOn); 
                config.setPrincipal(principal);
                config.setPassword(password);
                config.addRdvSeedingURI("http://dsg.ce.unipr.it/research/SP2A/rdvlist.txt");
                /*
                config.addRdvSeedingURI(new URI("http://rdv.jxtahosts.net/cgi-bin/rendezvous.cgi?2"));
                try {    
                    config.addRelaySeedingURI(new URI("http://rdv.jxtahosts.net/cgi-bin/relays.cgi?2"));
                } catch (java.net.URISyntaxException use) {
                    use.printStackTrace();
                } 
                */               
                try {
                    config.save();
                } catch (IOException io) {
                    io.printStackTrace();
                }
            }
            // create and Start the default jxta NetPeerGroup
            NetPeerGroupFactory factory  = new NetPeerGroupFactory(config.getPlatformConfig(), instanceHome.toURI());
            netPeerGroup = factory.getInterface();
            rendezvous = netPeerGroup.getRendezVousService();
            System.out.println("Node PeerID :"+netPeerGroup.getPeerID().getUniqueValue().toString());           
        } catch (Exception e) {
            // could not instantiate the group, print the stack and exit
            System.out.println("fatal error : group creation failure");
            e.printStackTrace();
            System.exit(1);
        }
        started = true;
        System.out.println("Peer started and correctly joined the NetPeerGroup " + netPeerGroup.getPeerGroupID().toString());
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
			    System.out.println("PSE join failed!");
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
     * Create a SOAPService and enabling it
     */
    private void createSOAPService() {					
        System.out.println("\nMy PeerID: " + netPeerGroup.getPeerID().toString());
        // Create a new SOAPService instance
        System.out.println("\nCreate new SOAPService");
        System.out.println("-------------------------------------------");         
        System.out.println("1 Creating new SOAPService instance...");
        long lifetime = 3600000;
        long expiration = 3600000;
        SOAPService service = new SOAPService(userLogLevel, lifetime, expiration); // lifetime and expiration are set to 1h
        
        // Create the service descriptor
        System.out.println("2 Creating ServiceDescriptor...");
        ServiceDescriptor descriptor = HelloService.DESCRIPTOR;
        descriptor.addComplexTypeMapping("ArrayList", "java.util.ArrayList");
        descriptor.setTimeout(10000);
        System.out.println( descriptor.toString() );

        // Build the ModuleSpecAdv 'Param' section
        StructuredTextDocument param = (StructuredTextDocument)
        StructuredDocumentFactory.newStructuredDocument(new MimeMediaType("text/xml"), "Parm");

        // *************** 1. Add the service WSDL description *****************		
        Element wsdlElem = param.createElement("WSDL", readFile64("HelloService.wsdl"));
        param.appendChild(wsdlElem);

        // ********************* 2. Add secure pipe tag ************************
        String secure = ( descriptor.isSecure() ) ? "true" : "false";
        Element secureElem = param.createElement("secure", secure);
        param.appendChild( secureElem );
        
        // Set the context object, with init parameters
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("serviceName", descriptor.getName());
        service.setContext(parameters);
    
        // Initialise the service
        try {
            System.out.println("Initialize the service...");
            System.out.print("Setting security policy...\t\t");
            if( descriptor.isSecure() )
                System.out.println("DEFAULT TLS");
            else
                System.out.println("NOT SET");
            
            // Initialize the service
            service.init( netPeerGroup, descriptor, param, null );
        } catch( Exception ex ) {
            System.out.println("Exception in initializing SOAP Service! " + ex );
            ex.printStackTrace();
        }

        try {
            System.out.println("Saving msadv param fields...");
            param.sendToStream( new FileOutputStream("ServiceParm.xml") );
        } catch( Exception e ) {
            System.out.println("Exception in saving service params section!");
            e.printStackTrace();
        }
        
        // Start a new ServiceThread running this service
        System.out.println("Starting new ServiceThread...");
        new SOAPServiceThread(service).start();	
        
        // Sleep while waiting for client calls (none available for now)
        System.out.println("Waiting for calls...");
        while( true ) {
            synchronized( this ) {
                try{ wait(); } catch (InterruptedException ie){}
            }
        }
    }


    /**
    * Returns the file contents as a Base64-encoded string
    */
    public static String readFile64( String fileName ){
        System.out.println("readFile64: Received request for: " + fileName);
        try{
            File file = new File(fileName);		
            System.out.println("Size of " + fileName + " is " + file.length() );
                
            byte[] buffer = new byte[ (int) file.length() ];
            FileInputStream fistream = new FileInputStream( file );
            fistream.read( buffer, 0, (int) file.length() );
            return (new String( URLBase64.encode( buffer ) ) );
        } catch( IOException e ) {
            System.out.println("IO exception dealing with file");
            System.exit(1);
        } catch( Exception e ) {
            System.out.println("Encoding exception dealing with file");
            e.printStackTrace();
            System.exit(1);
        }
    
        return null;
    }


    /**
     *  Stops and unrefrences the NetPeerGroup
     */
    public synchronized void stop() {
        if (stopped && !started) {
            return;
        }
        netPeerGroup.stopApp();
        netPeerGroup.unref();
        netPeerGroup = null;
        stopped = true;
    }

} 

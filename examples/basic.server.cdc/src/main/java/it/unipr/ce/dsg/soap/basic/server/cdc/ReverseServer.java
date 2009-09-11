package it.unipr.ce.dsg.soap.basic.server.cdc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
//import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

//import jxta.security.util.URLBase64;

//import net.jxta.credential.AuthenticationCredential;
//import net.jxta.credential.Credential;
import net.jxta.document.Element;
import net.jxta.document.MimeMediaType;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.document.StructuredTextDocument;
import net.jxta.id.IDFactory;


import net.jxta.peer.PeerID;
import net.jxta.peergroup.PeerGroupFactory;
//import net.jxta.peergroup.NetPeerGroupFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.platform.NetworkConfigurator;
//import net.jxta.protocol.ModuleImplAdvertisement;

import net.jxta.rendezvous.RendezVousService;
import net.jxta.soap.cdc.ServiceDescriptor;
import net.jxta.soap.cdc.SOAPServiceThread;
import net.jxta.soap.cdc.SOAPService;
import net.jxta.soap.cdc.util.Base64;

import it.polimi.si.mas.services.*;
//import org.ksoap2.serialization.*;
//import org.ksoap2.*;
//import org.ksoap2.transport.*;

public class ReverseServer {

    private PeerGroup netPeerGroup = null;
    private boolean started = false;
    private boolean stopped = false;
//    private String userLogLevel = "INFO";
    private String instanceName = "NA";
    private RendezVousService rendezvous;
    private final static File home = new File(System.getProperty("JXTA_HOME", ".jxta"));

    public ReverseServer(String instanceName) {
       this.instanceName = instanceName;
    }


    /**
     * @param args the command line arguments
     */
    public static void main( String args[] ) {
    //	System.setProperty("net.jxta.logging.Logging", "OFF");
    	ReverseServer providerPeer = new ReverseServer("ProviderPeer");
        System.out.println("Starting ProviderPeer ....");
        providerPeer.start("EDGE", "principal", "peerPassword", true);	
      //  providerPeer.authenticateToPSE();
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
            netPeerGroup  = PeerGroupFactory.newNetPeerGroup();
        //    System.out.println("default jxta netPeerGroup creato");
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
     * Create a SOAPService and enabling it
     */
    private void createSOAPService() {					
        System.out.println("\nMy PeerID: " + netPeerGroup.getPeerID().toString());
        // Create a new SOAPService instance
        System.out.println("\nCreate new SOAPService");
        System.out.println("-------------------------------------------");         
        System.out.println("1 Creating new SOAPService instance...");
        SOAPService service = new SOAPService();
        
        // Create the service descriptor
        System.out.println("2 Creating ServiceDescriptor...");
        ServiceDescriptor descriptor = Reverse.DESCRIPTOR;
        descriptor.setTimeout(10000);
        System.out.println( descriptor.toString() );
        

        // Build the ModuleSpecAdv 'Param' section
        StructuredTextDocument param = (StructuredTextDocument)
        StructuredDocumentFactory.newStructuredDocument(new MimeMediaType("text/xml"), "Parm");

        // *************** 1. Add the service WSDL description *****************		
      //  Element wsdlElem = param.createElement("WSDL", readFile64("Reverse.wsdl"));
      //  param.appendChild(wsdlElem);

        // ********************* 2. Add secure pipe tag ************************
        String secure = ( descriptor.isSecure() ) ? "true" : "false";
        Element secureElem = param.createElement("secure", secure);
        param.appendChild( secureElem );
        
        // Set the context object, with init parameters
   //     Map<String, Object> parameters = new HashMap<String, Object>();
        Map parameters = new HashMap();
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
            return (new String( Base64.encodeBytes( buffer ) ) );
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


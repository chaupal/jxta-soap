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

package it.unipr.ce.dsg.soap.tls.client.j2se;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import javax.xml.namespace.QName;
import jxta.security.exceptions.CryptoException;
import jxta.security.util.URLBase64;

import net.jxta.credential.AuthenticationCredential;
import net.jxta.credential.Credential;
import net.jxta.discovery.DiscoveryService;
import net.jxta.document.AdvertisementFactory;
import net.jxta.document.Attributable;
import net.jxta.document.Attribute;
import net.jxta.document.Element;
import net.jxta.document.MimeMediaType;
import net.jxta.document.StructuredDocument;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.document.StructuredTextDocument;
import net.jxta.document.TextElement;
import net.jxta.document.XMLDocument;
import net.jxta.document.XMLElement;
import net.jxta.endpoint.ByteArrayMessageElement;
import net.jxta.endpoint.Message;
import net.jxta.exception.ConfiguratorException;
import net.jxta.exception.PeerGroupException;
import net.jxta.ext.config.Configurator;
import net.jxta.ext.config.Profile;
import net.jxta.id.IDFactory;
import net.jxta.impl.membership.pse.PSEMembershipService;
import net.jxta.impl.membership.pse.StringAuthenticator;
import net.jxta.membership.InteractiveAuthenticator;
import net.jxta.membership.MembershipService;
import net.jxta.peer.PeerID;
import net.jxta.peergroup.NetPeerGroupFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.OutputPipe;
import net.jxta.platform.NetworkConfigurator;
import net.jxta.protocol.ConfigParams;
import net.jxta.protocol.ModuleImplAdvertisement;
import net.jxta.protocol.ModuleSpecAdvertisement;
import net.jxta.protocol.PeerAdvertisement;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.rendezvous.RendezvousEvent;
import net.jxta.rendezvous.RendezvousListener;
import net.jxta.rendezvous.RendezVousService;

import net.jxta.soap.CallFactory;
import net.jxta.soap.deploy.SOAPTransportDeployer;
import net.jxta.soap.JXTAUtils;
import net.jxta.soap.security.certificate.ServiceCertificateManager;
import net.jxta.soap.ServiceDescriptor;
import org.apache.axis.client.Call;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class HelloClient implements RendezvousListener {

    //private final static Logger LOG = Logger.getLogger(HelloClient.class.getName());
    //private String userLogLevel = "WARN";
    
    private PeerGroup netPeerGroup = null;
    private boolean started = false;
    private boolean stopped = false;
    private RendezVousService rendezvous;
    private DiscoveryService discoverySvc = null;
    private final static String connectLock = new String("connectLock");
    private String instanceName = "NA";
    private final static File home = new File(System.getProperty("JXTA_HOME", ".jxta"));	
    
    // Module Spec Adv of the discovered service
    private ModuleSpecAdvertisement msadv = null;
    private PipeAdvertisement securePipeAdv = null;
    
    private String WSDLbuffer = new String();
    private String decodedWSDL = new String();
    
    private Object[] authenticationParams = null;
    private Object[] serverAuthResponseParams = null;
    private ServiceCertificateManager certManager = null;
    private boolean secureTag = false;
    private String policyName = null;
    private String policyType = null;
    private HashMap<String,Object> policyParams = new HashMap<String,Object>();
    

    public HelloClient(String instanceName) {
       this.instanceName = instanceName;
    }


    /**
     * @param args the command line arguments
     */
    public static void main( String args[] ) {
	HelloClient consumerPeer = new HelloClient("ConsumerPeer");
        System.out.println("Starting ConsumerPeer ....");
        consumerPeer.start("principal", "peerPassword");	
	consumerPeer.authenticateToPSE();
	System.out.println("-------------------------------------------------");
        consumerPeer.waitForRendezvousConnection(10000);
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
    public synchronized void start(String principal, String password) {
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
                config.setMode(NetworkConfigurator.EDGE_NODE);
                config.setPrincipal(principal);
                config.setPassword(password);
                try {
                    config.addRdvSeedingURI(new URI("http://rdv.jxtahosts.net/cgi-bin/rendezvous.cgi?2"));
                    config.addRelaySeedingURI(new URI("http://rdv.jxtahosts.net/cgi-bin/relays.cgi?2"));
		    config.addRdvSeedingURI("http://dsg.ce.unipr.it/research/SP2A/rdvlist.txt"); 
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
            NetPeerGroupFactory factory  = new NetPeerGroupFactory((ConfigParams) config.getPlatformConfig(), instanceHome.toURI());
            netPeerGroup = factory.getInterface();
            System.out.println("Node PeerID :"+netPeerGroup.getPeerID().getUniqueValue().toString());
            rendezvous = netPeerGroup.getRendezVousService();
            rendezvous.addListener(this);
            started = true;
        } catch (PeerGroupException e) {
            // could not instantiate the group, print the stack and exit
            System.out.println("fatal error : group creation failure");
            e.printStackTrace();
            System.exit(1);
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
     *  Establishes group credential.  This is a required step when planning to
     *  to utilize TLS messegers or secure pipes
     *
     * @param  group      peer group to establish credentials in
     * @param  principal  the principal
     * @param  password   pass word
     */
    
    public static void login(PeerGroup group, String principal, String password) {
        try {
            StringAuthenticator auth = null;
            MembershipService membership = group.getMembershipService();
            Credential cred = membership.getDefaultCredential();
            if (cred == null) {
                AuthenticationCredential authCred = new AuthenticationCredential(group, "StringAuthentication", null);
                try {
                    auth = (StringAuthenticator) membership.apply(authCred);
                } catch (Exception failed) {
                    ;
                }

                if (auth != null) {
                    auth.setAuth1_KeyStorePassword(password.toCharArray());
                    auth.setAuth2Identity(group.getPeerID());
                    auth.setAuth3_IdentityPassword(principal.toCharArray());
                    if (auth.isReadyForJoin()) {
                        membership.join(auth);
                    }
                }
            }

            cred = membership.getDefaultCredential();
            if (null == cred) {
                AuthenticationCredential authCred = new AuthenticationCredential(group, "InteractiveAuthentication", null);
                InteractiveAuthenticator iAuth = (InteractiveAuthenticator) membership.apply(authCred);
                if (iAuth.interact() && iAuth.isReadyForJoin()) {
                    membership.join(iAuth);
                }
            }
        } catch (Throwable e) {
            // make sure output buffering doesn't wreck console display.
            System.err.println("Uncaught Throwable caught by 'main':");
            e.printStackTrace();
            System.exit(1);
        } finally {
            System.err.flush();
            System.out.flush();
        }
    }    
    

     /**
     * Blocks if not connected to a rendezvous, or
     * until a connection to rendezvous node occurs
     *
     * @param  timeout  timeout in milliseconds
     */
    public void waitForRendezvousConnection(long timeout) {
        if (!rendezvous.isConnectedToRendezVous() || !rendezvous.isRendezVous()) {
            System.out.println("Waiting for Rendezvous Connection");
            try {
                if (!rendezvous.isConnectedToRendezVous()) {
                    synchronized (connectLock) {
                        connectLock.wait(timeout);
                    }
                }
                //System.out.println("Connected to Rendezvous");
            } catch (InterruptedException e) {}
        }
    }
   

    /**
     *  rendezvousEvent the rendezvous event
     *
     * @param  event  rendezvousEvent
     */
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
	    StringWriter out = new StringWriter();
	    param.sendToWriter(out);
	    System.out.println("\nParam field of msadv:");
	    System.out.println( out.toString() );
	    out.close();
            
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
                // Check for secure pipe tag
                else if( elem.getName().equals("secure") ) {
                    secureTag = new Boolean( elem.getTextValue() );
                }
                // Check for secure invocation requirements (if any)
                else if( elem.getName().equals("Invocation-Charter") ) {
                    // Iterating along the security requirements...
                    Enumeration secureParams = elem.getChildren();
                    while( secureParams != null && secureParams.hasMoreElements() ){
                        TextElement el = (TextElement) secureParams.nextElement();
                        if( el.getName().equals("PCName") ) {
                            policyName = el.getTextValue();
                        }
                        else if( el.getName().equals("PCType") ) {
                            policyType = el.getTextValue();
                        }
                        else if( el.getName().equals("PCDesc") ) {
                            // Parsing parameters number
                            StringTokenizer pcDesc = new StringTokenizer( el.getTextValue().substring( el.getTextValue().indexOf(":") + 1 ), "#" );
                            int num_params = pcDesc.countTokens();
                            System.out.println("Invocation Charter requirements:\t" + num_params + " elem/s");
                            // Allocating Invocation Parameters Data Store
                            authenticationParams = new Object[ num_params + 2 ];
                            authenticationParams[ 0 ] = policyName;
                            authenticationParams[ 1 ] = policyType;
                            // Setting policy params keys
                            while( pcDesc.hasMoreTokens() ) {
                                policyParams.put( pcDesc.nextToken(), null );
                            }
                        }
                        else if( el.getName().equals("ClientAdv") ) {
                            // Ok, we have recognized the mandatory policy param
                            policyParams.put( el.getName(), el.getTextValue() );
                        }
                        // Ok, we don't have other elements to find...
                        //...
                    }
                }
            }
        } 
        catch (IOException e) {
            System.out.println("Error extracting service advertisement");
            e.printStackTrace();
            System.exit(1);
        }

        // Print out the decoded WSDL
	System.out.println("Print out decoded WSDL content:");
	System.out.println( decodedWSDL );
        
        System.out.println("\nCheck service secure invocation policy");
        System.out.println("=========================================");
        System.out.print("Policy required?\t");
        if( secureTag ) {
            System.out.println("YES");
            if( authenticationParams != null ) {
                authenticationParams[ 2 ] = policyParams;
                System.out.println("Policy Name:\t\t" + (String) authenticationParams[ 0 ] );
                System.out.println("Policy Type:\t\t" + (String) authenticationParams[ 1 ] );
                // Print out all required policy parameters
                System.out.println("\nRequired Params");
                System.out.println("-----------------------------------------");
                Set keySet = ((HashMap) authenticationParams[ 2 ]).keySet();
                Iterator keyIterator = keySet.iterator();
                while( keyIterator.hasNext() ) {
                    String key = (String) keyIterator.next();
                    // Get the associated value
                    Object value = ((HashMap) authenticationParams[ 2 ]).get( key );
                    System.out.println(key + "\t=>\t" + value);
                }
            }
            else {
                authenticationParams = new Object[ 2 ];
                authenticationParams[ 0 ] = policyName;
                authenticationParams[ 1 ] = policyType;
                System.out.println("Policy Name:\t\t" + policyName );
                System.out.println("Policy Type:\t\t" + policyType );
                System.out.println("Policy Params:\t\t-----");
            }
        }
        else {
            System.out.println("NO\n-> Setting unsecure service invocation...");
        }
        System.out.println("=========================================");
    }
    
    /**
     * Utility method for discovering service advertisements
     */	 	
    private void discoverServices(){
        int found = 0;
        int timeout = 10000;  	// standard timeout to wait for peers
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
                                                policyType,
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
            
            // Check service security policy
            boolean authResult = false;
            if( secureTag ) {
                // Retrieving all the needed security invocation params
                getAuthenticationParams();
                // Sending client advertisement for authentication purpose 
                // and waiting for server response
                authResult = authenticateToService();
                if( !authResult ) {
                    System.out.println("Cannot invoke target service! Authentication failed!");
                    return;
                }
            }
            else {
                securePipeAdv = msadv.getPipeAdvertisement();
            }
            
            System.out.println("# Creating Call object");
            Call call = CallFactory.
                getInstance().createCall( desc1, 
                        securePipeAdv,
                        netPeerGroup,
                        new String( "HelloService.wsdl" ),
                        new QName("http://DefaultNamespace", "HelloServiceService"),  // servicename
                        new QName("http://DefaultNamespace", "HelloService") );       // portname
            
            System.out.println("# setOperation: \"SayHello\"");	    
            call.setOperationName( new QName(desc1.getName(), "SayHello"));
            call.setTimeout( new Integer(10000));
            
            int i = 5;
            while( i-- > 0 ){
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
     * Retrieving all the needed security invocation parameters
     */
    private void getAuthenticationParams() {
        // Retrieve all required authentication params
        // The number and the type of params is depending on specific security policy
        //...
    }
    
    /**
     * Authenticate client peer to remote Service
     * It needs a CA signed X509 Certificate	 
     */	 	
    private boolean authenticateToService() {
        Message authMessage = new Message();
        PipeAdvertisement inputPipeAdv = null;
        InputPipe inputPipe = null;
        
        try {
            // Build an output pipe that binds to the service public input pipe
            OutputPipe outputPipe = null;
            
            int attempt = 1;
            boolean redo = true;
            do {
                try {
                    System.out.print("Binding op with remote ip... (" + attempt +  ")\t");
                    outputPipe = netPeerGroup.getPipeService().
                    createOutputPipe(msadv.getPipeAdvertisement(), 10000);
                    System.out.println("OK");
                    redo = false;
                } catch( Exception e ) {
                    System.err.println(" Exception in remote binding phase! TIMEOUT expired!");
		    e.printStackTrace();
                    attempt++;
                }
            } while( redo && attempt < 5 );	
            
            if( attempt < 5 ) {
                // Build authentication request message
                // NOTE: the message content depends on service security policy 
                ByteArrayMessageElement authCertElement =
                new ByteArrayMessageElement("message",              // Name
                                        MimeMediaType.XMLUTF8,      // Encoding
                                        getAuthRequestByteArray(),
                                        null);
                authMessage.addMessageElement( authCertElement );
                
                // Create client input pipe advertisement
                inputPipeAdv = (PipeAdvertisement) AdvertisementFactory.
                newAdvertisement( PipeAdvertisement.getAdvertisementType() );
                inputPipeAdv.setPipeID( IDFactory.newPipeID( netPeerGroup.getPeerGroupID() ) );
                inputPipeAdv.setName("Authentication Pipe");
                inputPipeAdv.setType("JxtaUnicast");
                inputPipeAdv.setDescription( netPeerGroup.getPeerID().toString() );
                
		System.out.println( JXTAUtils.toString( inputPipeAdv ) );
                
                // Add client input pipe adv to authentication message
                ByteArrayMessageElement authInputPipeElement =
                new ByteArrayMessageElement("remote-input-pipe",    // Name
                                        MimeMediaType.XMLUTF8,      // Encoding
                                        JXTAUtils.toByteArray(inputPipeAdv),
                                        null);
                authMessage.addMessageElement( authInputPipeElement );
                
                try {
                    inputPipe = netPeerGroup.getPipeService().createInputPipe( inputPipeAdv );
                } catch( Exception e ) {
                    System.out.println("-> Exception in creating client input pipe!");
                    e.printStackTrace();
                }
                
                // Send the message
                System.out.println("# Client Authentication message sent");
                outputPipe.send( authMessage );
                
                // Wait for server response
                System.out.print("# Waiting for server authentication response...\t\t");
                Message authResponse = inputPipe.waitForMessage();
                System.out.println("OK");
                
                // Getting server response
                ByteArrayMessageElement authResponseElement = (ByteArrayMessageElement) 
                authResponse.getMessageElement("AUTHRESPONSE");
                
                serverAuthResponseParams = new Object[ 3 ]; 
                // Init the keystore for storing client identity certificate
                PSEMembershipService pseSvc = (PSEMembershipService) netPeerGroup.getMembershipService();
                certManager = new ServiceCertificateManager( pseSvc.getPSEConfig() );
                
                try {
                    StructuredDocument authResponseDoc = StructuredDocumentFactory
                        .newStructuredDocument(MimeMediaType.XMLUTF8, authResponseElement.getStream()); 
                    System.out.print("# Saving server response to file...\t\t\t");
                    authResponseDoc.sendToStream( new FileOutputStream("authResponseDoc.xml" ) );
                    System.out.println("OK");
                    
                    // Getting authResponseDoc content
                    System.out.print("# Getting Auth Server Response parameters...\t\t");
                    initialize( authResponseDoc, serverAuthResponseParams );
                    System.out.println("OK");
                } catch( Exception e ) {
                    System.out.println("-> Exception in get server parameters!");
                    e.printStackTrace();
                }
                
                if( ((String) serverAuthResponseParams[ 0 ]).equals("Accepted") ) {
                    System.out.println("-> Authentication request was accepted!");
                    // Importing server cert in PSE KeyStore
                    System.out.println("# Importing server cert in PSE KeyStore...");
                    certManager.importCert( netPeerGroup, 
                                    (PeerAdvertisement) serverAuthResponseParams[ 1 ], 
                                    false );	
                    return true;
                }
                else {
                    System.out.println("-> Authentication request was refused!");
                }
            }
        } catch( Exception e ) {
            System.out.println("Exception in authenticate to service!");
            e.printStackTrace();
        }
        return false; 
    }
    
    /**
     * Return the client authentication request, codified as a byte array
     */
    private byte[] getAuthRequestByteArray() {
        // *************** Add mandatory element 'ClientAdv' *******************
        StructuredDocument authRequestDoc = 
            StructuredDocumentFactory.newStructuredDocument( MimeMediaType.XMLUTF8, "jxta:AuthRequest" );
        
        if( authRequestDoc instanceof XMLDocument ) {
            ((XMLDocument)authRequestDoc).addAttribute( "xmlns:jxta", "http://jxta.org" );
            ((XMLDocument)authRequestDoc).addAttribute( "xml:space", "preserve" );
            ((Attributable)authRequestDoc).addAttribute( "type", "jxta:AuthRequest" );
        }
        
        if( authRequestDoc instanceof Attributable ) {
            ((Attributable)authRequestDoc).addAttribute( "type", "jxta:AuthRequest" );
        }
        
        Element e = null;
        // 1. Add this Client Peer advertisement
        e = authRequestDoc.createElement( "ClientAdv", netPeerGroup.getPeerAdvertisement().toString() );
        authRequestDoc.appendChild( e );
        
        // 2. Add other elements in according to specific security policy
        // nothing...
        
        // Convert Document to byte[]
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] byteRequest = null;
        try {
            authRequestDoc.sendToStream( bos );
            authRequestDoc.sendToStream( new FileOutputStream("authRequest.xml") );
            byteRequest = bos.toByteArray();
        } catch( Exception ex ) {}
        return byteRequest;
    }
    
    /**
     * Utility method for parsing server auth response content
     * looking for server peer adv and service private secure pipe	 
     */	 	
    private void initialize( Element root, Object[] params ) { 
        if( !XMLElement.class.isInstance( root ) ) {
            throw new IllegalArgumentException( "authResponse only supports XMLElement" );
        }
        XMLElement doc = (XMLElement) root;
        String typedoctype = "";
        Attribute itsType = doc.getAttribute( "type" );
        if( null != itsType ) {
            typedoctype = itsType.getValue();
        }
        String doctype = doc.getName();
        if( !doctype.equals("jxta:AuthResponse") && !typedoctype.equals("jxta:AuthResponse") ) {
            throw new IllegalArgumentException( "Could not construct : "
                                    + "authResponse from doc containing a " + doctype );
        }
        Enumeration elements = doc.getChildren();
        while (elements.hasMoreElements()) {
            XMLElement elem = (XMLElement) elements.nextElement();
            if( !handleElement( elem, params ) ) {
                System.out.println("Unhandled element '" + elem.getName() + "' in " + doc.getName() );
            }
        }
    }
    
    /**
     * Managing each single response element
     */
    private boolean handleElement( XMLElement elem, Object[] params ) {
	System.out.println("# Handle element: " + elem.getName() );
  
        // Server Authentication result
        if( elem.getName().equals("AuthResult")) {
            String value = elem.getTextValue();
            value = value.trim();
            
            String authResult = elem.getTextValue();	
            params[ 0 ] = authResult;
	    System.out.println("Auth Result: " + params[ 0 ].toString() );
  
            return true;
        }
        
        // Server X509 Certificate
        if( elem.getName().equals("ServerAdv")) {
            String value = elem.getTextValue();
            value = value.trim();
            
            if( params[ 0 ].equals("Accepted") ) {
                PeerAdvertisement serverAdv = null;
                try {
                    StructuredDocument doc = (StructuredDocument) StructuredDocumentFactory
                    .newStructuredDocument( MimeMediaType.XMLUTF8, new StringReader( elem.getTextValue() ) );
                    serverAdv = (PeerAdvertisement) AdvertisementFactory.newAdvertisement( (XMLElement) doc );
                } catch( Exception e ) {
                    System.out.println("Exception in reading server advertisement!");
                    e.printStackTrace();
                }
                
                params[ 1 ] = serverAdv;
            }
            return true;
        }
        
        // Get Service Secure Input Pipe
        if( elem.getName().equals("SecurePipe")) {
            if( params[ 0 ].equals("Accepted") ) {
                PipeAdvertisement serviceSecurePipe = null;	
                try {
                    StructuredDocument doc = (StructuredDocument) StructuredDocumentFactory
                    .newStructuredDocument( MimeMediaType.XMLUTF8, new StringReader( elem.getTextValue() ) );
                    serviceSecurePipe = (PipeAdvertisement) AdvertisementFactory.newAdvertisement( (XMLElement) doc );
                } catch( Exception e ) {
                    System.out.println("Exception in reading service secure pipe adv!");
                    e.printStackTrace();
                }
                
                params[ 2 ] = serviceSecurePipe;
                securePipeAdv = serviceSecurePipe;
                
		System.out.println("\nService Secure Pipe: " + params[ 2 ].toString() );
            }
            return true;
        }
        
        // element was not handled
        return false;
    }


    /**
     *  Stops and unrefrences the NetPeerGroup
     */
    public synchronized void stop() {
        if (stopped && !started) {
            return;
        }
        rendezvous.removeListener(this);
        netPeerGroup.stopApp();
        netPeerGroup.unref();
        netPeerGroup = null;
        stopped = true;
    }
    
} 

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


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import javax.xml.namespace.QName;
import javax.xml.rpc.JAXRPCException;
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
import net.jxta.id.ID;
import net.jxta.id.IDFactory;
import net.jxta.impl.membership.pse.PSEMembershipService;
import net.jxta.impl.membership.pse.StringAuthenticator;
import net.jxta.membership.InteractiveAuthenticator;
import net.jxta.membership.MembershipService;
import net.jxta.peer.PeerID;
import net.jxta.peergroup.NetPeerGroupFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.platform.NetworkConfigurator;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.OutputPipe;
import net.jxta.protocol.ConfigParams;
import net.jxta.protocol.ModuleImplAdvertisement;
import net.jxta.protocol.ModuleSpecAdvertisement;
import net.jxta.protocol.PeerAdvertisement;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.protocol.RdvAdvertisement;
import net.jxta.rendezvous.RendezvousEvent;
import net.jxta.rendezvous.RendezvousListener;
import net.jxta.rendezvous.RendezVousService;

import net.jxta.soap.CallFactory;
import net.jxta.soap.deploy.SOAPTransportDeployer;
import net.jxta.soap.JXTAUtils;
import net.jxta.soap.security.certificate.ServiceCertificateManager;
import net.jxta.soap.security.wss4j.WSSecurity;
import net.jxta.soap.ServiceDescriptor;

import org.apache.axis.AxisFault;
import org.apache.axis.client.Call;
import org.apache.axis.description.ParameterDesc;
import org.apache.axis.encoding.XMLType;
import org.apache.axis.message.RPCElement;
import org.apache.axis.message.RPCHeaderParam;
import org.apache.axis.message.RPCParam;
import org.apache.axis.message.SOAPBodyElement;
import org.apache.axis.message.SOAPEnvelope;
import org.apache.axis.utils.JavaUtils;
import org.apache.axis.utils.Messages;
import org.apache.axis.utils.XMLUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class HelloClient implements RendezvousListener {

    //private final static Logger LOG = Logger.getLogger(HelloClient.class.getName());
    private String userLogLevel = "WARN";
    
    private PeerGroup netPeerGroup = null;
    private boolean started = false;
    private boolean stopped = false;
    private RendezVousService rendezvous;
    private final static String connectLock = new String("connectLock");
    private String instanceName = "NA";
    private final static File home = new File(System.getProperty("JXTA_HOME", ".jxta"));
    private DiscoveryService discoverySvc = null;	
    
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
			    StringTokenizer pcDesc = new StringTokenizer(el.getTextValue().substring( el.getTextValue().indexOf(":") + 1 ), "#" );
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
			else if( el.getName().equals("ClientX509Cert") ) {
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
	int timeout = 3000;  	// standard timeout to wait for peers
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
		if( policyType.equals("WSS-based") ) {
		    // Message based security policy
		    securePipeAdv = msadv.getPipeAdvertisement();
		}			
	    }
	    else {
		securePipeAdv = msadv.getPipeAdvertisement();
	    }
	    
	    System.out.println("# Creating Call object");	    	    
	    Call call = CallFactory.getInstance().createCall( desc1, 
							      // msadv.getPipeAdvertisement(),
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
		if( secureTag ) {
		    // Setting method parameters 
		    Object[] params = new Object[] { "Hey JXTA peer!" };
		    // Build WSS STR-compliant message 
		    org.apache.axis.Message secureMsg = null;
		    try {
			secureMsg = this.buildSignedEnvelopeMessage( call, params ); 
		    } catch( Exception e ) {
			System.out.println("Exception in signing soap envelope!");
			e.printStackTrace();				
		    }
		    if( secureMsg != null ) {
			SOAPEnvelope env = call.invoke( secureMsg.getSOAPEnvelope() );
			if( call.getMessageContext().getResponseMessage() == null ) {
			    System.out.println("-> Invocation timeout expired!");
			}
			else {
			    // Get result
			    SOAPBodyElement resBody = env.getFirstBody();
			    String res = (String) this.convertToObject( call, resBody );
			    System.out.println( res );	
			}
		    }
		}
		else {
		    String res = (String)call.invoke(new Object[] { "Hey JXTA peer!" } );
		    System.out.println(res);
		}
	    }				    
	} catch( Exception e ){
	    System.out.println("Error invoking SOAP service!");
	    e.printStackTrace();
	    System.exit(1);
	}
    }
    
    
    /**
     * Signs the specified message envelope with the private key
     * ssociated to the public key attached to the message
     */
    private org.apache.axis.Message buildSignedEnvelopeMessage( Call call, Object[] params ) throws AxisFault {
	org.apache.axis.Message secureMsg = null;
	int i = 0;
	
	// Test integrity
	for( i = 0 ; params != null && i < params.length ; i++ )
	    if ( !(params[i] instanceof SOAPBodyElement) ) {
		break ;
	    }
	// Is Messaging?
	if( params != null && params.length > 0 && i == params.length ) {
	    try {
		// Ok, we're doing Messaging, so build up the message
		// Initialize a new SOAP Envelope
		SOAPEnvelope soapEnv = new SOAPEnvelope(
							call.getMessageContext().getSOAPConstants(),
							call.getMessageContext().getSchemaVersion());
		
		// Adding parameters element
		for( i = 0; i < params.length; i++ ) {
		    soapEnv.addBodyElement((SOAPBodyElement) params[i]);
		}	
		
		System.out.println("Initialize custom WSS4J utility class...");
		WSSecurity wss4j = new WSSecurity( soapEnv.toString() );
		System.out.println("Sign with direct reference to signature key (X509 cert embedded)");	
		// Sign with direct reference to signature key (X509 cert embedded)
		secureMsg = wss4j.testX509SignatureDirectSTR(soapEnv);
	    } catch( Exception e ) {
		System.out.println("Exception in signing soap envelope!");
		e.printStackTrace();
		return null;
	    }
	    
	    return secureMsg;			
	}
	else {
	    if ( call.getOperationName() == null ) {
		throw new AxisFault( Messages.getMessage("noOperation00") );
	    }
	    try {
		// 2. Initialize a new SOAP Envelope
		SOAPEnvelope soapEnv = new SOAPEnvelope(
							call.getMessageContext().getSOAPConstants(),
							call.getMessageContext().getSchemaVersion());
		
		// 3. Adding parameters element
		for( i = 0; i < params.length; i++ ) {
		    Object obj = (Object) params[i];
		}
		
		//System.out.println("Create new RPC Element (SOAPBodyElement)");
		//System.out.println("Namespace URI: " + call.getOperationName().getNamespaceURI() );
		//System.out.println("Local part: " + call.getOperationName().getLocalPart() );
		RPCElement body = new  RPCElement( 
						  "http://localhost:8080/" + call.getOperationName().getNamespaceURI(),
						  call.getOperationName().getLocalPart(),
						  getParamList(call, params));
		
		soapEnv.addBodyElement(body);
		
		System.out.println("Print out SOAP Envelope: ");
		XMLUtils.PrettyElementToWriter(soapEnv.getAsDOM(), new PrintWriter(System.out));	
		
		System.out.println("Initialize custom WSS4J utility class...");
		WSSecurity wss4j = new WSSecurity( soapEnv.toString() );			
		System.out.println("Sign with direct reference to signature key (X509 cert embedded)");	
		// Sign with direct reference to signature key (X509 cert embedded)
		secureMsg = wss4j.testX509SignatureDirectSTR(soapEnv);
		System.out.println("Print current msg: ");
		XMLUtils.PrettyElementToWriter(secureMsg.getSOAPEnvelope().getAsDOM(),
					       new PrintWriter(System.out));
		
		return secureMsg;
	    }
	    catch( Exception e ) {
		System.out.println("Exception in building signed soap envelope with direct ref!");
		e.printStackTrace();
		return null;
	    }
	}
    }
    
    /**
     * Convert the list of objects into RPCParam's based on the paramNames,
     * paramXMLTypes and paramModes variables.  If those aren't set then just
     * return what was passed in.
     *
     * @param  params   Array of parameters to pass into the operation/method
     * @return Object[] Array of parameters to pass to invoke()
     */
    private Object[] getParamList(Call call, Object[] params) {
	int  numParams = 0 ;
	
	// If we never set-up any names... then just return what was passed in
	if ( call.getOperation() == null || call.getOperation().getNumParams() == 0 ) {
	    return( params );
	}
	
	// Count the number of IN and INOUT params, this needs to match the
	// number of params passed in - if not throw an error
	numParams = call.getOperation().getNumInParams();				
	if ( params == null || numParams != params.length ) {
	    throw new JAXRPCException(
				      Messages.getMessage(
							  "parmMismatch00",
							  (params == null) ? "no params" : "" + params.length, 
							  "" + numParams) 
				      );
	}
	
	// All ok - so now produce an array of RPCParams
	Vector<RPCParam> result = new Vector<RPCParam>();
	int j = 0;
	ArrayList parameters = call.getOperation().getParameters();
	
	for( int i = 0; i < parameters.size(); i++ ) {
	    ParameterDesc param = (ParameterDesc)parameters.get(i);
	    if (param.getMode() != ParameterDesc.OUT) {
		QName paramQName = param.getQName();
		
		// Create an RPCParam if param isn't already an RPCParam.
		RPCParam rpcParam = null;
		Object p = params[j++];
		if(p instanceof RPCParam) {
		    rpcParam = (RPCParam)p;
		} else {
		    rpcParam = new RPCParam(paramQName.getNamespaceURI(),
					    paramQName.getLocalPart(),
					    p);
		}
		// Attach the ParameterDescription to the RPCParam
		// so that the serializer can use the (javaType, xmlType)
		// information.
		rpcParam.setParamDesc(param);
		
		// Add the param to the header or vector depending
		// on whether it belongs in the header or body.
		if (param.isInHeader()) {
		    call.addHeader(new RPCHeaderParam(rpcParam));
		} else {
		    result.add(rpcParam);
		}
	    }
	}
	return( result.toArray() );
    }
    
    /**
     * Converting the resulting message body element into standard Object		 	 
     */
    private Object convertToObject( Call call, SOAPBodyElement soapBodyEl ) throws AxisFault {
	Vector resArgs = new Vector();
	Object result = null;
	if( soapBodyEl instanceof RPCElement ) {
	    try {
		resArgs = ((RPCElement) soapBodyEl).getParams();
	    } catch (Exception e) {
		throw AxisFault.makeFault(e);
	    }
	    
	    if( resArgs != null && resArgs.size() > 0 ) {	
		// If there is no return, then we start at index 0 to create the outParams Map.
		// If there IS a return, then we start with 1.
		int outParamStart = 0;
		boolean findReturnParam = false;
		QName returnParamQName = null;
		if (call.getOperation() != null) {
		    returnParamQName = (call.getOperation() != null) ?
			call.getOperation().getReturnType() : null;
		}
		
		QName returnType = (call.getOperation() != null) ?
		    call.getOperation().getReturnType() : null;
		if (!XMLType.AXIS_VOID.equals(returnType)) {
		    if (returnParamQName == null) {
			// Assume the first param is the return
			RPCParam param = (RPCParam)resArgs.get(0);
			result = param.getObjectValue();
			outParamStart = 1;
		    } else {
			// If the QName of the return value was given to us, look
			// through the result arguments to find the right name
			findReturnParam = true;
		    }
		}
		
		// The following loop looks at the resargs and
		// converts the value to the appropriate return/out parameter
		// value.  If the return value is found, is value is
		// placed in result.  The remaining resargs are
		// placed in the outParams list (note that if a resArg
		// is found that does not match a operation parameter qname,
		// it is still placed in the outParms list).
		for (int i = outParamStart; i < resArgs.size(); i++) {
		    RPCParam param = (RPCParam) resArgs.get(i);
		    
		    Class<?> javaType = null;
		    if (call.getOperation() == null) {
			javaType = null;
		    }
		    ParameterDesc paramDesc = call.getOperation().getOutputParamByQName(param.getQName());
		    javaType = ( paramDesc == null ) ? null : paramDesc.getJavaType();
		    Object value = param.getObjectValue();
		    
		    // Convert type if needed
		    if (javaType != null && value != null &&
			!javaType.isAssignableFrom(value.getClass())) {
                        value = JavaUtils.convert(value, javaType);
                    }
		    
		    // Check if this parameter is our return
		    // otherwise just add it to our outputs
		    if (findReturnParam &&
			returnParamQName.equals(param.getQName())) {
			// found it!
			result = value;
			findReturnParam = false;
		    } else {
			call.getOutputParams().put(param.getQName(), value);
			call.getOutputValues().add(value);
		    }
		}
		
		// If the return param is still not found, that means
		// the returned value did not have the expected qname.
		// The soap specification indicates that this should be
		// accepted (and we also fail interop tests if we are strict here).
		// Look through the outParms and find one that
		// does not match one of the operation parameters.
		if (findReturnParam) {
		    Iterator it = call.getOutputParams().keySet().iterator();
		    while (it.hasNext() && findReturnParam) {
			QName qname = (QName) it.next();
			ParameterDesc paramDesc =
			    call.getOperation().getOutputParamByQName(qname);
			if (paramDesc == null) {
			    // Doesn't match a paramter, so use this for the return
			    findReturnParam = false;
			    result = call.getOutputParams().remove(qname);
			}
		    }
		}
		
		// If we were looking for a particular QName for the return and
		// still didn't find it, throw an exception
		if (findReturnParam) {
		    String returnParamName = returnParamQName.toString();
		    throw new AxisFault(Messages.getMessage("noReturnParam",
							    returnParamName));
		}
	    }
	} else {
	    // This is a SOAPBodyElement, try to treat it like a return value
	    try {
		result = soapBodyEl.getValueAsType(call.getReturnType());
	    } catch (Exception e) {
		// just return the SOAPElement
		result = soapBodyEl;
	    }
	    
	}
	
	// Convert type if needed
	if (call.getOperation() != null && call.getOperation().getReturnClass() != null) {
	    result = JavaUtils.convert(result, call.getOperation().getReturnClass());
	}
	
        return result;
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

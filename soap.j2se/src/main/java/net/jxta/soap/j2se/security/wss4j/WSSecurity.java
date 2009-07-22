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


package net.jxta.soap.j2se.security.wss4j;
 
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.cert.X509Certificate;
import java.util.Vector;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.dom.DOMSource;

import org.apache.axis.client.AxisClient;
import org.apache.axis.configuration.NullProvider;
import org.apache.axis.message.SOAPEnvelope;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.SOAPPart;
import org.apache.axis.utils.XMLUtils;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.components.crypto.CryptoFactory;
import org.apache.ws.security.message.token.Reference;
import org.apache.ws.security.message.token.SecurityTokenReference;
import org.apache.ws.security.message.WSEncryptBody;
import org.apache.ws.security.message.WSSAddSAMLToken;
import org.apache.ws.security.message.WSSAddUsernameToken;
import org.apache.ws.security.message.WSSignEnvelope;
import org.apache.ws.security.saml.SAMLIssuer;
import org.apache.ws.security.saml.SAMLIssuerFactory;
import org.apache.ws.security.SOAPConstants;
import org.apache.ws.security.util.WSSecurityUtil;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSEncryptionPart;
import org.apache.ws.security.WSPasswordCallback;
import org.apache.ws.security.WSSConfig;
import org.apache.ws.security.WSSecurityEngine;
import org.apache.ws.security.WSSecurityEngineResult;
import org.apache.xml.security.c14n.Canonicalizer;
import org.opensaml.SAMLAssertion;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

//---------
import org.apache.ws.security.WSSecurityException;
import org.w3c.dom.Node;
import javax.xml.namespace.QName;
import java.security.Principal;
import org.w3c.dom.NodeList;
import org.apache.xml.security.encryption.XMLCipher;
import javax.crypto.SecretKey;
import org.apache.xml.security.encryption.XMLEncryptionException;
import org.apache.ws.security.WSSConfig;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.XMLSignature;
import java.security.cert.X509Certificate;
import org.apache.ws.security.message.EnvelopeIdResolver;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.ws.security.WSDocInfo;
import org.apache.ws.security.WSDocInfoStore;
//---------

/**
 * This class tests common WSS4J features, such as add security tokens,
 * encrypt or signing a message envelope and verify the results 
 */
public class WSSecurity implements CallbackHandler {

    // Initializes the security engine to the default WS-Security settings
    private static final WSSecurityEngine secEngine = new WSSecurityEngine();
    
    // The following creates a crypto provider according to the
    // class name specified by the system property:
    //    org.apache.ws.security.crypto.provider
    //
    // If the provider property is not set, the default class,
    // org.apache.ws.security.components.crypto.BouncyCastle, is used.
    //
    // The provider is initialized to the values specified in
    // the crypto.properties file. The crypto.properties file
    // found in the wss4j jar file specifies
    //    org.apache.ws.security.components.crypto.Merlin
    // as the provider class.
    private static final Crypto crypto =
	CryptoFactory.getInstance("cryptoSKI.properties");
    
    private AxisClient engine = null;
    private MessageContext msgContext = null;
    private Message axisMessage = null;
    private SOAPEnvelope unsignedEnvelope = null;
    
    /**
     * WSSecurity constructor
     */	 	
    public WSSecurity( String soapMsg ) {
	engine = new AxisClient(new NullProvider());
	msgContext = new MessageContext(engine);
	
	try {
	    axisMessage = this.getAxisMessage(soapMsg);
	    unsignedEnvelope = axisMessage.getSOAPEnvelope();
	    
	    //System.out.println("\n<<<<<< Original message content >>>>>>");
	    //this.printSOAPMessage( unsignedEnvelope );			
	} catch( Exception e ) {
	    System.out.println("Exception in WSSecuritySampleSOAP constructor!");
	    e.printStackTrace();
	}
    }
    
    /**
     * Creates and returns an Axis message from a
     * SOAP envelope string.
     *
     * @param unsignedEnvelope   a string containing a SOAP envelope
     * @return <code>Message</code>   the Axis message
     */
    private Message getAxisMessage(String unsignedEnvelope) {
	InputStream inStream =
	    new ByteArrayInputStream(unsignedEnvelope.getBytes());
	Message axisMessage = new Message(inStream);
	axisMessage.setMessageContext(msgContext);
	return axisMessage;
    }
    
    /**
     * Print current message content
     */
    private void printSOAPMessage( SOAPEnvelope soapEnv ) throws Exception {
	XMLUtils.PrettyElementToWriter(soapEnv.getAsDOM(),
				       new PrintWriter(System.out));	
    }
    
    /**
     * Set current SOAP envelope
     */
    public void setSOAPEnvelope( SOAPEnvelope soapEnv ) {
	this.unsignedEnvelope = soapEnv;
    } 
	 	 			 	
    /**
     * Get current SOAP envelope
     */
    public SOAPEnvelope getSOAPEnvelope() {
	return unsignedEnvelope;
    } 

    /**
     * Get WSSecurityEngine
     */
    public WSSecurityEngine getSecurityEngine() {
	return secEngine;
    }	 	

    // <<<<<<<<<<<<<<<<<<<<  U S E R N A M E   T O K E N  >>>>>>>>>>>>>>>>>>>>>
    /**
     * Adds user tokens to a SOAP envelope in compliance with WS-Security.
     *
     * @throws Exception on error
     */
    public Message addEncrUserNameToken(SOAPEnvelope unsignedEnvelope) throws Exception {		
	// Get the message as document
	Document doc = unsignedEnvelope.getAsDocument();
	
	// Main steps:
	// 1: Add a UserName token
	// 2: Add an Id to it
	// 3: Create a reference to the UserName token
	// 4: Setting necessary parameters in WSEncryptBody
	// 5: Encrypt using the password associated to UserName token
	
	// STEP 1 - Add the UserNameToken
	String username = "joedoe";
	String password = "this is a lot of foobar ";
	byte[] key = password.getBytes();
	
	WSSAddUsernameToken builder = new WSSAddUsernameToken("", false);
	builder.setPasswordType(WSConstants.PASSWORD_TEXT);
	long startTime = System.nanoTime();
	builder.build(doc, username, password);
	long stopTime = System.nanoTime();
	this.printElapsedTime( stopTime - startTime );
	
	// STEP 2: Add an Id to it
	Element usrEle = (Element) (doc.getElementsByTagNameNS(WSConstants.WSSE_NS, "UsernameToken").item(0));
	String idValue = "7654";
	usrEle.setAttribute("Id", idValue);
	
	// STEP 3: Create a Reference to the UserNameToken
	Reference ref = new Reference(WSSConfig.getDefaultWSConfig(), doc);
	ref.setURI("#" + idValue);
	ref.setValueType("UsernameToken");
	SecurityTokenReference secRef =
	    new SecurityTokenReference(WSSConfig.getDefaultWSConfig(), doc);
	secRef.setReference(ref);
	
	// Adding the namespace
	WSSecurityUtil.setNamespace(secRef.getElement(),
				    WSConstants.WSSE_NS,
				    WSConstants.WSSE_PREFIX);
	
	// STEP 4: Setting necessary parameters in WSEncryptBody
	WSEncryptBody wsEncrypt = new WSEncryptBody();		
	wsEncrypt.setKeyIdentifierType(WSConstants.EMBED_SECURITY_TOKEN_REF);
	wsEncrypt.setSecurityTokenReference(secRef);
	wsEncrypt.setKey(key);
	
	// STEP 5: Encrypt using the using the key
	startTime = System.nanoTime();
	Document encDoc = wsEncrypt.build(doc, crypto);
	stopTime = System.nanoTime();
	this.printElapsedTime( stopTime - startTime );
	
	// Now convert the resulting document into a message first. The toSOAPMessage()
	// method performs the necessary c14n call to properly set up the signed
	// document and convert it into a SOAP message. After that we extract it
	// as a document again for further processing.
	Message signedMsg = (Message) this.toSOAPMessage(encDoc);
	System.out.println("Message with Encrypted UserNameToken:");
	XMLUtils.PrettyElementToWriter(signedMsg.getSOAPEnvelope().getAsDOM(), new PrintWriter(System.out));
	System.out.println("--------------------------------------------------------");
	return signedMsg;
    }
    
    /**
     * Test that adds a UserNameToken with password Digest to a WS-Security envelope
     * 
     * @throws java.lang.Exception Thrown when there is any problem in signing or verification
     */
    public Message addUserNameTokenDigest(SOAPEnvelope unsignedEnvelope) throws Exception {
	SOAPEnvelope envelope = null;
	WSSAddUsernameToken builder = new WSSAddUsernameToken();
	System.out.println("Before adding UsernameToken PW Digest....");
	Document doc = unsignedEnvelope.getAsDocument();
	
	long startTime = System.nanoTime();
	Document signedDoc = builder.build(doc, "Matteo", "security");
	long stopTime = System.nanoTime();
	this.printElapsedTime( stopTime - startTime );
	
	// Now convert the resulting document into a message first. The toSOAPMessage()
	// method performs the necessary c14n call to properly set up the signed
	// document and convert it into a SOAP message. After that we extract it
	// as a document again for further processing.
	Message signedMsg = (Message) this.toSOAPMessage(signedDoc);
	System.out.println("Message with UserNameToken PW Digest:");
	XMLUtils.PrettyElementToWriter(signedMsg.getSOAPEnvelope().getAsDOM(), new PrintWriter(System.out));
	System.out.println("--------------------------------------------------------");
	signedDoc = signedMsg.getSOAPEnvelope().getAsDocument();
	System.out.println("After adding UsernameToken PW Digest....");
	
	return signedMsg;
    }
    
    /**
     * Test that adds a UserNameToken with password text to a WS-Security envelope
     * 
     * @throws java.lang.Exception Thrown when there is any problem in signing or verification
     */
    public Message addUserNameTokenText(SOAPEnvelope unsignedEnvelope) throws Exception {
	SOAPEnvelope envelope = null;
	WSSAddUsernameToken builder = new WSSAddUsernameToken();
	builder.setPasswordType(WSConstants.PASSWORD_TEXT);
	System.out.println("Before adding UsernameToken PW Text....");
	Document doc = unsignedEnvelope.getAsDocument();
	
	long startTime = System.nanoTime();
	Document signedDoc = builder.build(doc, "Matteo", "verySecret");
	long stopTime = System.nanoTime();
	this.printElapsedTime( stopTime - startTime );
	
	Message signedMsg = (Message) this.toSOAPMessage(signedDoc);
	System.out.println("Message with UserNameToken PW Text:");
	XMLUtils.PrettyElementToWriter(signedMsg.getSOAPEnvelope().getAsDOM(), new PrintWriter(System.out));
	signedDoc = signedMsg.getSOAPEnvelope().getAsDocument();
	System.out.println("After adding UsernameToken PW Text....");
	
	return signedMsg;
    }
    
    /**
     * Verifies the soap envelope
     * 
     * @param env soap envelope
     * @throws java.lang.Exception Thrown when there is a problem in verification
     */
    public void verifyUserNameToken(Document doc) throws Exception {
	System.out.println("Before verifying UsernameToken....");
	long startTime = System.nanoTime();
	secEngine.processSecurityHeader(doc, null, this, null);
	long stopTime = System.nanoTime();
	System.out.println("UsernameToken verification successfully!"); 
	this.printElapsedTime( stopTime - startTime );
    }
    
    // <<<<<<<<<<<<<<<<<<<<  E N C R Y P T I O N  >>>>>>>>>>>>>>>>>>>>>
    /**
     * Encrypts a SOAP envelope in compliance with WS-Security.
     *
     * @throws Exception on error
     */
    public Message encryptSOAPEnvelope(SOAPEnvelope unsignedEnvelope) throws Exception {
	WSEncryptBody encrypt = new WSEncryptBody();
	encrypt.setUserInfo("privKey");
	encrypt.setMustUnderstand( false );
	
	// Encrypting document
	Document doc = unsignedEnvelope.getAsDocument();
	System.out.print("Start single encryption...\t\t");
	long startTime = System.nanoTime();
	Document encryptedDoc = encrypt.build(doc, crypto);
	long stopTime = System.nanoTime();
	System.out.println("OK");
	this.printElapsedTime( stopTime - startTime );
	
	// Convert the document into a SOAP message.
	Message encryptedMsg = (Message) this.toSOAPMessage(encryptedDoc);
	encryptedDoc = encryptedMsg.getSOAPEnvelope().getAsDocument();
	
	// Convert the document into a SOAP message.
	Message encryptedSOAPMsg = (Message) this.toSOAPMessage(encryptedDoc);
	System.out.println("\n<<< ENCRYPTED MESSAGE >>>");
	XMLUtils.PrettyElementToWriter(encryptedMsg.getSOAPEnvelope().getAsDOM(), new PrintWriter(System.out));	
	System.out.println("--------------------------------------------------------");
	encryptedDoc = encryptedMsg.getSOAPEnvelope().getAsDocument();
	System.out.println("After Encryption....");
	
	return encryptedMsg;		
    }
    
    /**
     * Test that encrypt and then again encrypts (Super encryption) WS-Security envelope
     * and then verifies ist
     * 
     * @throws Exception Thrown when there is any problem in encryption or verification
     */
    public Message testEncryptionEncryption(SOAPEnvelope unsignedEnvelope) throws Exception {
	SOAPEnvelope envelope = null;
	WSEncryptBody encrypt = new WSEncryptBody();
	encrypt.setUserInfo("privKey");
	encrypt.setMustUnderstand( false );
	
	System.out.println("Begin Strong Encryption....");
	Document doc = unsignedEnvelope.getAsDocument();
	long startTime = System.nanoTime();
	System.out.print("Start 1st phase encryption...\t");
	Document encryptedDoc = encrypt.build(doc, crypto);
	System.out.println("OK");
	System.out.print("Start 2nd phase encryption...\t");
	Document encryptedEncryptedDoc = encrypt.build(encryptedDoc, crypto);
	System.out.println("OK");
	long stopTime = System.nanoTime();
	this.printElapsedTime( stopTime - startTime );
	
	// Now convert the resulting document into a message first. The toSOAPMessage()
	// method performs the necessary c14n call to properly set up the signed
	// document and convert it into a SOAP message. After that we extract it
	// as a document again for further processing.
	
	Message encryptedMsg = (Message) this.toSOAPMessage(encryptedEncryptedDoc);
	System.out.println("\n<<< ENCRYPTED MESSAGE >>>");
	XMLUtils.PrettyElementToWriter(encryptedMsg.getSOAPEnvelope().getAsDOM(), new PrintWriter(System.out));
	System.out.println("--------------------------------------------------------");
	encryptedEncryptedDoc = encryptedMsg.getSOAPEnvelope().getAsDocument();
	System.out.println("After Encryption....");
	
	return encryptedMsg;
    }
    
    /**
     * Verifies the soap envelope
     * 
     * @param envelope 
     * @throws Exception Thrown when there is a problem in verification
     */
    public void verifyEncryption(Document doc) throws Exception {
	System.out.println("Begin encryption verification...");
	long startTime = System.nanoTime();
	Vector results = secEngine.processSecurityHeader(doc, null, this, crypto);
	long stopTime = System.nanoTime();
	System.out.println("Encryption verification was successfully!");
	this.printElapsedTime( stopTime - startTime );
    }
    
    /**
     * Verifies the soap envelope
     * 
     * @param envelope 
     * @return The X509 certificate enveloped in the security header	 
     * @throws Exception Thrown when there is a problem in verification
     */
    public X509Certificate verifyEncryptionSTR(Document doc) throws Exception {
	X509Certificate cert = null;
	System.out.println("Begin encryption verification...");
	long startTime = System.nanoTime();
	Vector results = secEngine.processSecurityHeader(doc, null, this, crypto);
	for( int i = 0; i < results.size(); i++ ) {
	    WSSecurityEngineResult result = (WSSecurityEngineResult) results.get( i );
	    if( result.getCertificate() != null )
		cert = result.getCertificate();
	}
	long stopTime = System.nanoTime();
	System.out.println("Encryption verification was successfully! - STR Cert extracted!");
	this.printElapsedTime( stopTime - startTime );
	return cert;
    }
    
    // <<<<<<<<<<<<<<<<<<<<  S I G N A T U R E  >>>>>>>>>>>>>>>>>>>>>
    /**
     * Creates a signed SOAP message in compliance with WS-Security.
     *
     * @throws Exception on error
     */
    public void signSOAPEnvelope(SOAPEnvelope unsignedEnvelope) throws Exception {
	// WSSignEnvelope signs a SOAP envelope according to the
	// WS Specification (X509 profile) and adds the signature data
	// to the envelope.
	WSSignEnvelope signer = new WSSignEnvelope();
	
	String alias = "privKey";
	String password = "security";
	signer.setUserInfo(alias, password);
	
	Document doc = unsignedEnvelope.getAsDocument();
	System.out.println("Begin signing....");
	
	// The "build" method, creates the signed SOAP envelope.
	// It takes a SOAP Envelope as a W3C Document and adds
	// a WSS Signature header to it. The signed elements
	// depend on the signature parts that are specified by
	// the WSBaseMessage.setParts(java.util.Vector parts)
	// method. By default, SOAP Body is signed.
	// The "crypto" parameter is the object that implements
	// access to the keystore and handling of certificates.
	// A default implementation is included:
	//    org.apache.ws.security.components.crypto.Merlin
	long startTime = System.nanoTime();
	Document signedDoc = signer.build(doc, crypto);
	long stopTime = System.nanoTime();
	this.printElapsedTime( stopTime - startTime );
	
	// Convert the signed document into a SOAP message.
	Message signedSOAPMsg = (Message) this.toSOAPMessage(signedDoc);
	System.out.println("\n<<< SIGNED MESSAGE >>>");
	XMLUtils.PrettyElementToWriter(signedSOAPMsg.getSOAPEnvelope().getAsDOM(), new PrintWriter(System.out));
	System.out.println("--------------------------------------------------------");
	signedDoc = signedSOAPMsg.getSOAPEnvelope().getAsDocument();
	System.out.println("After Signing....");
    }
    
    /**
     * Test that signs and verifies a WS-Security envelope.
     * This test uses the direct reference key identifier (certificate included
     * as a BinarySecurityToken (BST) in the message). The test signs the message
     * body (SOAP Body) and uses the STRTransform to sign the embedded certificate
     * 
     * @throws java.lang.Exception Thrown when there is any problem in signing or verification
     */
    public Message testX509SignatureDirectSTR(SOAPEnvelope unsignedEnvelope) throws Exception {
	SOAPEnvelope envelope = null;
	WSSignEnvelope builder = new WSSignEnvelope();
	builder.setUserInfo("privKey", "security");
	SOAPConstants soapConstants = WSSecurityUtil.getSOAPConstants(unsignedEnvelope.getAsDOM());
	Vector<WSEncryptionPart> parts = new Vector<WSEncryptionPart>();
    
	// Set up to sign body and use STRTransorm to sign
	// the signature token (e.g. X.509 certificate)
	WSEncryptionPart encP = new WSEncryptionPart(soapConstants.getBodyQName().getLocalPart(),
						     soapConstants.getEnvelopeURI(),
						     "Content");
	parts.add(encP);
	encP = new WSEncryptionPart("STRTransform",
				    soapConstants.getEnvelopeURI(),
				    "Content");
	parts.add(encP);
	
	builder.setParts(parts);
	builder.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);

	System.out.println("Before Signing STR DirectReference....");
	Document doc = unsignedEnvelope.getAsDocument();
	long startTime = System.nanoTime();
	Document signedDoc = builder.build(doc, crypto);
	long stopTime = System.nanoTime();
	this.printElapsedTime( stopTime - startTime );
	
	Message signedMsg = (Message) this.toSOAPMessage(signedDoc);
	System.out.println("After Signing STR DirectReference....");
	
	return signedMsg;
    }
    
    /**
     * Verifies the soap envelope signature
     * 
     * @param env soap envelope
     * @throws java.lang.Exception Thrown when there is a problem in verification
     */
    public void verifySignature(Document doc) throws Exception {
	System.out.println("Begin signature verification...");
	long startTime = System.nanoTime();
	secEngine.processSecurityHeader(doc, null, this, crypto);
	long stopTime = System.nanoTime();
	System.out.println("Signature verification was successfully!");
	this.printElapsedTime( stopTime - startTime );
    }
    
    // <<<<<<<<<<<<<<<  E N C R Y P T I O N   /   S I G N I N G  >>>>>>>>>>>>>>>>>
    /**
     * Test that encrypts and signs a WS-Security envelope, then performs
     * verification and decryption
     * 
     * @throws Exception Thrown when there is any problem in signing, encryption,
     *                   decryption, or verification
     */
    public void testEncryptionSigning(SOAPEnvelope unsignedEnvelope) throws Exception {
	SOAPEnvelope envelope = null;
	WSEncryptBody encrypt = new WSEncryptBody();
	WSSignEnvelope sign = new WSSignEnvelope();
	encrypt.setUserInfo("privKey");
	sign.setUserInfo("privKey", "security");
	System.out.println("Double step: [Encryption + Signing]");
	System.out.println("Start Encryption....");
	Document doc = unsignedEnvelope.getAsDocument();
	long startTime = System.nanoTime();
	Document encryptedDoc = encrypt.build(doc, crypto);
	System.out.println("Start Signing encrypted message....");
	Document encryptedSignedDoc = sign.build(encryptedDoc, crypto);
	long stopTime = System.nanoTime();
	this.printElapsedTime( stopTime - startTime );
	
	Message encryptedMsg = (Message) this.toSOAPMessage(encryptedSignedDoc);
	System.out.println("\n<<< ENCRYPTED + SIGNED MESSAGE >>>");
	XMLUtils.PrettyElementToWriter(encryptedMsg.getSOAPEnvelope().getAsDOM(), new PrintWriter(System.out));
	System.out.println("--------------------------------------------------------");
	encryptedSignedDoc = encryptedMsg.getSOAPEnvelope().getAsDocument();
	System.out.println("After Encryption + Signing....");
    }
    
    /**
     * Test that first signs, then encrypts a WS-Security envelope.
     * The test uses the IssuerSerial key identifier to get the keys for
     * signature and encryption. Encryption uses 3DES.
     * 
     * @throws Exception Thrown when there is any problem in signing, encryption,
     *                   decryption, or verification
     */
    public void testSigningEncryptionIS3DES(SOAPEnvelope unsignedEnvelope) throws Exception {
	SOAPEnvelope envelope = null;
	
	WSEncryptBody encrypt = new WSEncryptBody();
	encrypt.setUserInfo("privKey");
	encrypt.setKeyIdentifierType(WSConstants.ISSUER_SERIAL);
	encrypt.setSymmetricEncAlgorithm(WSConstants.TRIPLE_DES);
	
	WSSignEnvelope sign = new WSSignEnvelope();
	sign.setUserInfo("privKey", "security");
	sign.setKeyIdentifierType(WSConstants.ISSUER_SERIAL);
	
	System.out.println("Double step: [Signing + Encryption (3DES)]");		
	System.out.println("Before Sign/Encryption....");
	Document doc = unsignedEnvelope.getAsDocument();
	long startTime = System.nanoTime();
	Document signedDoc = sign.build(doc, crypto);
	Document encryptedSignedDoc = encrypt.build(signedDoc, crypto);
	long stopTime = System.nanoTime();
	this.printElapsedTime( stopTime - startTime );
	
	Message encryptedMsg = (Message) this.toSOAPMessage(encryptedSignedDoc);
	System.out.println("<<< Signed and encrypted message with IssuerSerial key identifier (both), 3DES >>>");
	XMLUtils.PrettyElementToWriter(encryptedMsg.getSOAPEnvelope().getAsDOM(), new PrintWriter(System.out));
	System.out.println("--------------------------------------------------------");
	System.out.println("After Signing + Encryption....");
    }
    
    // <<<<<<<<<<<<<<<  S A M L   A S S E R T I O N  >>>>>>>>>>>>>>>>>
    /**
     * Test that encrypt and decrypt a WS-Security envelope.
     * This test uses the RSA_15 alogrithm to transport (wrap) the symmetric key
     * 
     * @throws Exception Thrown when there is any problem in signing or verification
     */
    public void testSAMLUnsignedSenderVouches(SOAPEnvelope unsignedEnvelope) throws Exception {
	SOAPEnvelope envelope = null;
	SAMLIssuer saml = SAMLIssuerFactory.getInstance("saml.properties");
	
	SAMLAssertion assertion = saml.newAssertion();
	
	WSSAddSAMLToken wsSign = new WSSAddSAMLToken();
	Document doc = unsignedEnvelope.getAsDocument();
	System.out.println("Before SAMLUnsignedSenderVouches....");	
	long startTime = System.nanoTime();
	Document signedDoc = wsSign.build(doc, assertion);
	long stopTime = System.nanoTime();
	this.printElapsedTime( stopTime - startTime );
	System.out.println("After SAMLUnsignedSenderVouches....");
	
	// convert the resulting document into a message first. The toSOAPMessage()
	// method performs the necessary c14n call to properly set up the signed
	// document and convert it into a SOAP message. Check that the contents can't
	// be read (cheching if we can find a specific substring). After that we extract it
	// as a document again for further processing.
	
	Message signedMsg = (Message) this.toSOAPMessage(signedDoc);
	System.out.println("<<< Unsigned SAML message (sender vouches) >>>");
	XMLUtils.PrettyElementToWriter(signedMsg.getSOAPEnvelope().getAsDOM(), new PrintWriter(System.out));
	String encryptedString = signedMsg.getSOAPPartAsString();
	signedDoc = signedMsg.getSOAPEnvelope().getAsDocument();
    }
    
    /**
     * Custom callback handler implementation
     */	 	
    public void handle(Callback[] callbacks) 
	throws IOException, UnsupportedCallbackException {
	for (int i = 0; i < callbacks.length; i++) {
	    if (callbacks[i] instanceof WSPasswordCallback) {
		WSPasswordCallback pc = (WSPasswordCallback) callbacks[i];
                
		// here call a function/method to lookup the password for
		// the given identifier (e.g. a user name or keystore alias)
		// e.g.: pc.setPassword(passStore.getPassword(pc.getIdentfifier))
		// for Testing we supply a fixed name here.
		
		System.out.println("WSPasswordCallback: setting password...");
		pc.setPassword("security");
	    } else {
		throw new UnsupportedCallbackException(callbacks[i], "Unrecognized Callback");
	    }
	}
    }
    
    /**
     * Convert a DOM Document into a SOAP message
     */
    private SOAPMessage toSOAPMessage(Document doc) throws Exception {
	Canonicalizer c14n =
	    Canonicalizer.getInstance(Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS);
	byte[] canonicalMessage = c14n.canonicalizeSubtree(doc);
	ByteArrayInputStream in = new ByteArrayInputStream(canonicalMessage);
	MessageFactory factory = MessageFactory.newInstance();
	return factory.createMessage(null, in);
    }
    
    /**
     * Update soap message
     */
    private SOAPMessage updateSOAPMessage(Document doc, SOAPMessage message)
	throws Exception {
	DOMSource domSource = new DOMSource(doc);
	message.getSOAPPart().setContent(domSource);
	return message;
    }
    
    /** 
     * Utility method for evaluating elapsed computation time in nanos
     */	 	
    private void printElapsedTime( long time ) {
	System.out.println("============================================");
	StringBuffer sb = new StringBuffer();
	int seconds = (int)(time / 1e9);
	long remainder = (long)(time % 1e9);
	sb.append( seconds );
	sb.append( " s, ");
	long ms = (long) (remainder / 1e6);
	remainder = (long) (remainder % 1e6);
	sb.append( ms );
	sb.append( " ms, ");
	long us = (long) (remainder / 1e3);
	remainder = (long) (remainder % 1e3);
	sb.append( us );
	sb.append( " us, ");
	long ns = remainder;
	sb.append( ns );
	sb.append( " ns");
	System.out.println("Elapsed time: " + sb.toString());
	System.out.println("============================================");
    }
    
}


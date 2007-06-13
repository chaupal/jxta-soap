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


package net.jxta.soap.security.certificate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.KeyStoreException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;

import net.jxta.document.Attributable;
import net.jxta.document.Element;
import net.jxta.document.StructuredDocument;
import net.jxta.id.ID;
import net.jxta.id.IDFactory;
import net.jxta.impl.membership.pse.PSEConfig;
import net.jxta.impl.protocol.Certificate;
import net.jxta.peergroup.PeerGroup;
import net.jxta.protocol.PeerAdvertisement;
 
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class ServiceCertificateManager extends BaseCertificateManager implements PSEManager {

    // Setting up ServiceCertificatesManager class Logger (WARN level by default)
    private final static Logger LOG = Logger.getLogger(ServiceCertificateManager.class.getName());
    private PSEConfig myPSEConfig = null;
    
    public ServiceCertificateManager( PSEConfig pseConfig ) {
	myPSEConfig = pseConfig;
    }
    
    public PSEConfig getPSEConfig() {
	return myPSEConfig;
	}
    
    /** 
     *	Import a trusted certificate or certificate chain into the PSE
     *                             
     *	@param pg The peergroup	used to define the ID under which the cert chain
     *	will be stored in the PSE KeyStore	   	 	 	 	 
     *	@param pa The peer advertisement who's certificate will be trusted
     *	@param trimChain Trim the certificate chain at the first certificate already
     *	present in the PSE KeyStore	 
     *	@return The number of imported certificates or -1 if an error occurred
     *	@exception IllegalArgumentException If peer advertisement is wrong
     *	@see <a href="http://java.sun.com/j2se/1.4.2/docs/api/java/lang/IllegalArgumentException.html">IllegalArgumentException</a> 
     *	@exception CertificateEncodingException If the provided certificate is wrong
     *	@see <a href="http://java.sun.com/j2se/1.4.2/docs/api/java/security/cert/CertificateEncodingException.html">CertificateEncodingException</a> 
     *	@exception KeyStoreException If a KeyStore problem occurred during import phase
     *  @see <a href="http://java.sun.com/j2se/1.4.2/docs/api/java/security/KeyStoreException.html">KeyStoreException</a> 	 
     *	@exception IOException If an IO problem occurred during import phase 	 	 	 	 
     *  @see <a href="http://java.sun.com/j2se/1.4.2/docs/api/java/io/IOException.html">IOException</a> 	 
     */	 
    public int importCert( PeerGroup pg, PeerAdvertisement pa, boolean trimChain )
	throws IllegalArgumentException {
	ID createid = pa.getPeerID();
	
	System.out.println(" ******* BEGIN IMPORTCERT ********");
	System.out.println(" PeerID: " + pa.getPeerID());
	System.out.println(" PeerGroup.peerGroupClassID: " + PeerGroup.peerGroupClassID);
	
	StructuredDocument rootParams = pa.getServiceParam( PeerGroup.peerGroupClassID );
	
	if( null == rootParams ) {
	    throw new IllegalArgumentException("Peer advertisement does not contain group parameters");
	}
	
	Enumeration eachRoot = rootParams.getChildren( "RootCert" );
	
	if( !eachRoot.hasMoreElements() ) {
	    throw new IllegalArgumentException("Peer advertisement does not contain root certificate");
	}
	
	Element root = (Element) eachRoot.nextElement();
	if( root instanceof Attributable ) {
	    // Backwards compatibility hack. Adds type so cert chain is recognized.
	    ((Attributable)root).addAttribute( "type",  Certificate.getMessageType() );
	}
	
	Certificate cert_msg = new Certificate( root );
	int imported = 0;
	
	try {
	    Iterator sourceChain = Arrays.asList( cert_msg.getCertificates() ).iterator();
	    X509Certificate aCert = (X509Certificate) sourceChain.next();
            
	    do {
		if( null != myPSEConfig.getTrustedCertificateID( aCert ) ) {
		    // The certificate to be imported is already present in the PSE KeyStore
		    // So we avoid to import it and exit
					break;
		}
		
		myPSEConfig.erase( createid );
		myPSEConfig.setTrustedCertificate( createid, aCert );
		imported++;
                
		// create a codat id for the next certificate in the chain.
		aCert = null;
		if( sourceChain.hasNext() ) {
		    aCert = (X509Certificate) sourceChain.next();
                    
		    if( trimChain ) {
			if( null != myPSEConfig.getTrustedCertificateID( aCert ) ) {
			    // it's already in the pse
			    break;
			}
		    }
		    byte [] der = aCert.getEncoded();
		    createid = IDFactory.newCodatID( pg.getPeerGroupID(), new ByteArrayInputStream(der) );
		}
	    } while( null != aCert );
	    System.out.println(" ******** END IMPORTCERT *********");
	} catch( CertificateEncodingException failure ) {
	    IllegalStateException failed = new IllegalStateException( "Bad certificate" );
	    failed.initCause( failure );
	    return -1;
	} catch( KeyStoreException failure ) {
	    IllegalStateException failed = new IllegalStateException( "KeyStore failure while importing certificate." );
	    failed.initCause( failure );
	    return -1;
	} catch( IOException failure ) {
	    IllegalStateException failed = new IllegalStateException(  "IO failure while importing certificate." );
	    failed.initCause( failure );
	    return -1;
	}
        
	return imported;
    }
    
    /** 
     *	Look in the PSE KeyStore for the specified certificate
     *
     *	@param issuerName The certificate issuer name
     *	@param subjectName The certificate subject name	 
     *	@return The X509 certificate or null if the certificate has not been found
     */	 
    public X509Certificate findCert( String issuerName, String subjectName ) {
	ID[] allCertsID = null;
	X509Certificate aCert = null;
	boolean certFound = false;
	
	// Retrieving all certificates currently stored into PSE KeyStore
	try {
	    allCertsID = myPSEConfig.getTrustedCertsList();
	} catch( KeyStoreException e ) {
	    System.out.println("Exception in findCert: wrong keystore provided!");
	    e.printStackTrace();
	    return null;
	} catch( IOException e ) {
	    System.out.println("Exception in findCert: error during KeyStore processing!");
	    e.printStackTrace();
	    return null;
	}
	
	Iterator allCertsIDIterator = Arrays.asList( allCertsID ).iterator();
	
	// Look for requested cert
	try {
	    while( !certFound && allCertsIDIterator.hasNext() ) {
		ID idCert = (ID) allCertsIDIterator.next();
		aCert = myPSEConfig.getTrustedCertificate( idCert );
		//System.out.println("FINDCERT: Cert Subject Name: " + aCert.getSubjectX500Principal().getName());
		//System.out.println("FINDCERT: Cert Issuer Name: " + aCert.getIssuerX500Principal().getName());				
		if( ( (aCert.getSubjectX500Principal().getName()).compareTo( subjectName ) == 0 ) &&
		    ( (aCert.getIssuerX500Principal().getName()).compareTo( issuerName ) == 0 ) ) {  
		    // Is the requested cert?
		    certFound = true;
		}
	    }
	} catch( Exception e ) {
	    System.out.println("Exception while seeking the PSE KeyStore!");
	    e.printStackTrace();
	    return null;
	}
	
	if( certFound == false )
	    return null;
	else
			return aCert;
    }
    
    /** 
     *	Replace a certificate of the PSE KeyStore with the specified certificate
     *
     *	@param currentCert The current certificate to be replaced
     *	@param newCert The new certificate	 
     */	 
    public void replaceCert( X509Certificate currentCert, X509Certificate newCert ) {
	ID currentCertID = null;
	
	try {
	    currentCertID = myPSEConfig.getTrustedCertificateID( currentCert );
	    
	    // Is there a chain related to this cert?
	    X509Certificate[] currentCertChain = myPSEConfig.getTrustedCertificateChain( currentCertID );
	    // Handling chain...
	    
	    // Ok, now we have to replace current cert with new cert
	    myPSEConfig.erase( currentCertID );
	    myPSEConfig.setTrustedCertificate( currentCertID, newCert );
	    
	} catch( KeyStoreException e ) {
	    System.out.println("Exception in replaceCert: wrong keystore provided!");
	    e.printStackTrace();
	    return;
	} catch( IOException e ) {
	    System.out.println("Exception in replaceCert: error during KeyStore processing!");
	    e.printStackTrace();
	    return;
	}
    }
    
    
    /** 
     *	Print out the content of the trusted certificates specified
     *	Note: the cert specified must exist in the PSE KeyStore	 
     *
     *	@param cert The X509 compliant certificate to be printed out	  	 	 	 	 
     *	@param showCerts Print the complete certificate content
     *	@param showChains Print also the certificate chain
     */	 
    public void printPSECert( X509Certificate cert, boolean showCerts, boolean showChains ) {
	System.out.println("\n Print out X509 certificate content");
	System.out.println(" -------------------------------------------------");
	
	System.out.println( "[ " + cert.getSubjectX500Principal().getName() + " ]" );
	
	// Show also the certificate chain
	try {
	    if( showChains ) {
		ID certID = myPSEConfig.getTrustedCertificateID( cert ); 
		X509Certificate certs[] = myPSEConfig.getTrustedCertificateChain( certID );
		
		if( null != certs ) {
		    System.out.println(" Show chain for this certificate");
		    Iterator eachChainedCert = Arrays.asList( certs ).iterator();
		            
		    while( eachChainedCert.hasNext() ) {
			System.out.println(" Element chain *********************************");
			X509Certificate aChainCert = (X509Certificate) eachChainedCert.next();
			if( showCerts ) {
			    StringBuffer indent = new StringBuffer( "\n" + aChainCert.toString().trim() );
			    int from = indent.length();
			    
			    while( from > 0 ) {
				int returnAt = indent.lastIndexOf( "\n", from ) ;
				
				from = returnAt -1 ;
				
				if( (returnAt >= 0)  && (returnAt != indent.length()) ) {
				    indent.insert( returnAt + 1, "\t" );
				}
			    }
			    
			    System.out.println( indent.toString() );
			    System.out.println(" ");
			} else {
			    System.out.println("\t[ " + cert.getSubjectX500Principal().getName() + " ] Fingerprint (SHA1) : " + calcCertFingerPrint( aChainCert ) );
			}
		    }
		}
	    } else {
		if( showCerts ) {
		    StringBuffer indent = new StringBuffer( "\n" + cert.toString().trim() );
		    int from = indent.length();
		    
		    while ( from > 0 ) {
			int returnAt = indent.lastIndexOf( "\n", from ) ;
			
			from = returnAt -1 ;
			
			if( (returnAt >= 0)  && (returnAt != indent.length()) ) {
			    indent.insert( returnAt + 1, "\t\t" );
			}
		    }
		    
		    System.out.println( indent.toString() );
		    System.out.println(" ");
		} else {
		    System.out.println( "\t\tFingerprint (SHA1) : " + calcCertFingerPrint( cert ) );
		}
	    }
	} catch( Exception e ) {
	}
	System.out.println(" ---------------- END CERT CONTENT ----------------");	
    }
    
    /** 
     *	Print out all trusted certificates stored into PSE KeyStore
     *		 
     *	@param showCerts Print the complete certificate content
     *	@param showChains Print the certificate chain for each certificate
     */	 
    public void printAllPSECerts( boolean showCerts, boolean showChains ) {
	System.out.println("\n Print out all certs stored into PSE KeyStore");
	System.out.println(" -------------------------------------------------");

	try {
	    Iterator eachCert = Arrays.asList( myPSEConfig.getTrustedCertsList() ).iterator();
	    if( eachCert.hasNext() ) {
		System.out.println(" PSE KeyStore isn't empty");
	    }
	    
	    while( eachCert.hasNext() ) {
		ID aCert = (ID) eachCert.next();
		
		X509Certificate cert = myPSEConfig.getTrustedCertificate( aCert );
		System.out.println( aCert.toString() + "\t[ " + cert.getSubjectX500Principal().getName() + " ]" );
                
		// Show also the certificate chain
		if( showChains ) {
		    X509Certificate certs[] = myPSEConfig.getTrustedCertificateChain( aCert );
		    System.out.println(" Show chain for this certificate");
		    
		    if( null != certs ) {
			Iterator eachChainedCert = Arrays.asList( certs ).iterator();
                        
			while( eachChainedCert.hasNext() ) {
			    X509Certificate aChainCert = (X509Certificate) eachChainedCert.next();
			    System.out.println(" Element chain *********************************");
			    if( showCerts ) {
				StringBuffer indent = new StringBuffer( "\n" + aChainCert.toString().trim() );
				int from = indent.length();
				
				while( from > 0 ) {
				    int returnAt = indent.lastIndexOf( "\n", from ) ;									
				    from = returnAt -1 ;
				    if( (returnAt >= 0)  && (returnAt != indent.length()) ) {
					indent.insert( returnAt + 1, "\t" );
				    }
				}
                                
				System.out.println( indent.toString() );
				System.out.println(" ");
			    } else {
				System.out.println("\t[ " + cert.getSubjectX500Principal().getName() + " ] Fingerprint (SHA1) : " + calcCertFingerPrint( aChainCert ) );
			    }
			}
		    }
                } else {
                    if( showCerts ) {
                        StringBuffer indent = new StringBuffer( "\n" + cert.toString().trim() );
                        int from = indent.length();
                        
                        while ( from > 0 ) {
                            int returnAt = indent.lastIndexOf( "\n", from ) ;
                            
                            from = returnAt -1 ;
                            
                            if( (returnAt >= 0)  && (returnAt != indent.length()) ) {
                                indent.insert( returnAt + 1, "\t\t" );
                            }
                        }
                        
			System.out.println( indent.toString() );
			System.out.println(" ");
                    } else {
                    	System.out.println( "\t\tFingerprint (SHA1) : " + calcCertFingerPrint( cert ) );
                    }
                }
            }
	} catch( Exception failure ) {
	    failure.printStackTrace();
	    return;
	}
	System.out.println(" --------------------- END PSE KEYSTORE ----------------------");
    }
    
}

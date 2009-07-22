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


package net.jxta.soap.j2se.security.certificate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.MessageDigest;
import java.util.Enumeration;

import net.jxta.document.Attributable;
import net.jxta.document.Element;
import net.jxta.document.StructuredDocument;
import net.jxta.id.ID;
import net.jxta.impl.membership.pse.PSEUtils;
import net.jxta.peergroup.PeerGroup;
import net.jxta.protocol.PeerAdvertisement;

public class BaseCertificateManager {
    /**
     *  Extract a certificate or a certificate chain from a peer advertisement
     *  
     *  @param peerAdv The peer advertisement which contains the certificate to be extracted
     *  @return An array of X509 compliant certificates or null if an error occurred
     */
    public static X509Certificate[] extractCertificatesFromAdv( PeerAdvertisement peerAdv )
	throws IllegalArgumentException {
	X509Certificate[] allCerts = null;
	ID createid = peerAdv.getPeerID();
	
	StructuredDocument rootParams = peerAdv.getServiceParam( PeerGroup.peerGroupClassID );  
	if( null == rootParams ) {
	    throw new IllegalArgumentException("Peer advertisement does not contain group parameters");
	}
	
	Enumeration eachRoot = rootParams.getChildren( "RootCert" );
	if( !eachRoot.hasMoreElements() ) {
	    throw new IllegalArgumentException("Peer advertisement does not contain root certificate");
	}
        
	Element root = (Element) eachRoot.nextElement();
	if( root instanceof Attributable ) {
	    ((Attributable)root).addAttribute( "type",  net.jxta.impl.protocol.Certificate.getMessageType() );
	}
	
	try {
	    allCerts = (new net.jxta.impl.protocol.Certificate( root )).getCertificates();
	} catch( Exception e ) {
	    System.out.println("Exception in extracting Certificates from peer adv!");
	    e.printStackTrace();
	    return null; 
	}
	return allCerts;
    }	 	 	 	 	 	
    
    /**
     * Encode an X509 Certificate in Base64 format
     */	 	
    public static String encodeBase64Cert( X509Certificate cert ) {
	String encodedCert;
	try {
	    encodedCert = PSEUtils.base64Encode( cert.getEncoded() );
	} catch( CertificateEncodingException failed ) {
	    IllegalStateException failure = new IllegalStateException( "bad signed certificate." );
	    failure.initCause( failed );
	    
	    throw failure;
	} catch( IOException failed ) {
	    IllegalStateException failure = new IllegalStateException( "Could not encode certificate." );
	    failure.initCause( failed );
	    
	    throw failure;
		}
	
	return encodedCert;	
    }
    
    /**
     * Decode an X509 Certificate from Base64 format
     */	 	
    public static X509Certificate decodeBase64Cert( String base64Cert ) {
	byte[] cert_der = null;
	X509Certificate certX509 = null;
	try {
	    cert_der = PSEUtils.base64Decode( new StringReader( base64Cert ) );
	    CertificateFactory cf = CertificateFactory.getInstance( "X.509" );
	    certX509 = (X509Certificate) cf.generateCertificate( new ByteArrayInputStream(cert_der) );
	} catch( IOException failed ) {
	    IllegalStateException failure = new IllegalStateException( "Could not decode certificate." );
	    failure.initCause( failed );
	    
	    throw failure;
	} catch( CertificateException error ) {
	    throw new IllegalArgumentException( "bad certificate." );
	}
	
	return certX509;	
    }

    /**
     * Check Certificate validity
     * @return true if it is valid, false otherwise	 
     */	 	
    public static boolean isSignedCertValid( X509Certificate cert ) {
	try {
	    cert.checkValidity();
	} catch ( CertificateExpiredException expired ) {
	    return false;
	} catch ( CertificateNotYetValidException notyet ) {
	    return false;
	}
	return true;	
    }

    /** 
     *	Print out trusted specified certificate content
     *
     *	@param cert The X509 compliant certificate to be printed out	  	 	 	 	 
     *	@param showCerts Print the complete certificate content
     */	 
    public static void printX509Certificate( X509Certificate cert, boolean showCerts ) {
	System.out.println("\n Print out X509 certificate content");
	System.out.println(" -------------------------------------------------");
	System.out.println( "[ " + cert.getSubjectX500Principal().getName() + " ]" );
	
	// Show cert content
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
	    try {
		System.out.println( "\t\tFingerprint (SHA1) : " + calcCertFingerPrint( cert ) );
	    } catch( Exception e ) {
		System.out.println(" Exception in 'calcFingerPrint'!");
		e.printStackTrace();
	    }
	}
	System.out.println(" ---------------- END CERT CONTENT ----------------");	
    }
    
    /**
     *	Evaluate the finger print of the X509 certificate
     */	 
    static String calcCertFingerPrint( X509Certificate cert ) throws Exception {
	byte[] derCert = cert.getEncoded();
	MessageDigest md = MessageDigest.getInstance("SHA1");
	byte[] digest = md.digest(derCert);
	StringBuffer hexes = new StringBuffer( );
	for( int eachByte = 0; eachByte < digest.length; eachByte++ ) {
	    hexes.append( toHexDigits(digest[eachByte]) );
	    if( eachByte + 1 != digest.length ) {
		hexes.append( ':' );
	    }
	}
        
	return hexes.toString();
    }
    
    /** 
     *	Convert to HEX format
     */	 
    static String toHexDigits( byte theByte ) {
	final char [] HEXDIGITS = { '0', '1', '2', '3', '4', '5', '6', '7',
				    '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
	StringBuffer result = new StringBuffer(2);
        
	result.append( HEXDIGITS[(theByte >>> 4) & 15] );
	result.append( HEXDIGITS[theByte & 15] );
        
	return result.toString();
    }
    
    /**
     *  Reads a certificate from the provided file in PEM format.
     *
     *  @param certFile the targetfile from which the certificate will be read.
     *  @return cert the certificate.
     *  @throws IOException if there is a problem reading the certificate.
     **/
    public static Certificate readCertPEM( File certFile ) throws IOException {
        FileReader fr = null;
        try {
            fr = new FileReader( certFile );
            BufferedReader br = new BufferedReader( fr );
            
	    byte [] cert_der = PSEUtils.loadObject( br, "CERTIFICATE" );
            if( null == cert_der ) {
                throw new IOException( "File does not contrain a certificate" );
            } 
	    
            CertificateFactory cf = CertificateFactory.getInstance( "X.509" );
	    return cf.generateCertificate( new ByteArrayInputStream(cert_der) );
	} catch ( CertificateException badcert ) {
	    System.out.println("Failed to read cert!");
	    badcert.printStackTrace();
	} finally {
	    if( null != fr ) {
		try {
		    fr.close();
		} catch ( IOException ignored ){;}
	    }
	}
	return null;
    }
    
    /**
     *  Writes a certificate to the provided file in PEM format.
     *
     *  @param certFile the targetfile in which the certificate will be written.
     *  @param cert the certificate to write.
     *  @throws IOException if there is a problem writing the certificate.
     **/
    public static void writeBase64CertPEM( File certFile, String cert ) throws IOException {
	FileWriter fw = null;
	try {
	    fw = new FileWriter( certFile );
	    BufferedWriter bw = new BufferedWriter( fw );
	    PSEUtils.writeBase64Object( bw, "CERTIFICATE", cert );
	} finally {
	    if( null != fw ) {
		try {
		    fw.close();
		} catch ( IOException ignored ){;}
	    }
	}
    }
    
    /**
     *  Writes a certificate to the provided file in PEM format.
     *
     *  @param certFile the targetfile in which the certificate will be written.
     *  @param cert the certificate to write.
     *  @throws IOException if there is a problem writing the certificate.
     **/
    public static void writeCertPEM( File certFile, Certificate cert ) throws IOException {
	FileWriter fw = null;        
	try {
	    fw = new FileWriter( certFile );
	    BufferedWriter bw = new BufferedWriter( fw );
	    bw.write( cert.toString() );
	    bw.write( "\n" );
	    PSEUtils.writeObject( bw, "CERTIFICATE", cert.getEncoded() );
	} catch( CertificateEncodingException failed ) {
	    System.out.println("Could not encode certificate!");
	    failed.printStackTrace();
	} finally {
	    if( null != fw ) {
		try {
		    fw.close();
		} catch ( IOException ignored ){;}
	    }
	}
    }
    
    /**
     * Convert the specified validity period from days floating format to a [day, hours, minutes] format
     */	 	
    public static int[] getValidityArray( double validityDays ) {
	int[] validityArray = new int[ 3 ];		// [ days, hours, minutes ]
	
        if( validityDays < (1 / 1440) ) {
	    validityArray[ 0 ] = 30;	// default = 1 month
	    validityArray[ 1 ] = validityArray[ 2 ] = 0;
	}
        else if( ( validityDays - Math.floor( validityDays ) ) > 0 ) {
	    // Remainder
	    validityArray[ 0 ] = (int) Math.floor( validityDays );	// days
	    validityArray[ 1 ] = (int) (( validityDays - validityArray[ 0 ] ) * 24);	// hours
	    validityArray[ 2 ] = (int) Math.round((( (( validityDays - validityArray[ 0 ] ) * 24) - validityArray[ 1 ] ) * 60));	// minutes
	} else {
	    validityArray[ 0 ] = (int) validityDays;
	}
	
	return validityArray;
    }
    
    /**
     * Convert specified validity from days floating format to a String format
     */	 	
    public static String getValidityString( double validityDays ) {
	StringBuffer sb = new StringBuffer();
	int[] validityArray = getValidityArray( validityDays );
	
	if( validityArray[ 0 ] != 0 )
	    sb.append( validityArray[ 0 ] + "g " );
	if( validityArray[ 1 ] != 0 )
	    sb.append( validityArray[ 1 ] + "h " );
	if( validityArray[ 2 ] != 0 )
	    sb.append( validityArray[ 2 ] + "min" );
	
	return sb.toString();
    }
    
}

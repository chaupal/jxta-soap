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


package net.jxta.soap.cdc;

import java.io.ByteArrayOutputStream;

import net.jxta.document.Advertisement;
import net.jxta.document.Document;
import net.jxta.document.MimeMediaType;

/**
 * Misc JXTA utils
 * 
 */
public class JXTAUtils {
    
    /**
     * Convert this Advertisement to a String
     */
    public static String toString( Advertisement advert ) throws Exception {

        Document doc = advert.getDocument( new MimeMediaType( "text/xml" ) );

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.sendToStream( bos );

        return bos.toString();
        
    }

    /**
     * Convert this Advertisement to a String
     */
    public static byte[] toByteArray( Advertisement advert ) throws Exception {

        Document doc = advert.getDocument( new MimeMediaType( "text/xml" ) );

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.sendToStream( bos );

        return bos.toByteArray();
        
    }
 
}

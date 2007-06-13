/*
 * Copyright (c) 2001 Sun Microsystems, Inc.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *       Sun Microsystems, Inc. for Project JXTA."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact Project JXTA at http://www.jxta.org.
 *
 * 5. Products derived from this software may not be called "JXTA",
 *    nor may "JXTA" appear in their name, without prior written
 *    permission of Sun.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of Project JXTA.  For more
 * information on Project JXTA, please see
 * <http://www.jxta.org/>.
 *
 * This license is based on the BSD license adopted by the Apache Foundation.
 *
 * 
 */

/**
 * Methods for base64 encode/decode
 */

package net.jxta.soap.util;

import java.lang.Exception;

public class URLBase64 {
  private final static byte[] codes = {
    (byte)'A', (byte)'B', (byte)'C', (byte)'D', 
    (byte)'E', (byte)'F', (byte)'G', (byte)'H',
    (byte)'I', (byte)'J', (byte)'K', (byte)'L', 
    (byte)'M', (byte)'N', (byte)'O', (byte)'P', 
    (byte)'Q', (byte)'R', (byte)'S', (byte)'T', 
    (byte)'U', (byte)'V', (byte)'W', (byte)'X',
    (byte)'Y', (byte)'Z', (byte)'a', (byte)'b', 
    (byte)'c', (byte)'d', (byte)'e', (byte)'f',
    (byte)'g', (byte)'h', (byte)'i', (byte)'j', 
    (byte)'k', (byte)'l', (byte)'m', (byte)'n',
    (byte)'o', (byte)'p', (byte)'q', (byte)'r',
    (byte)'s', (byte)'t', (byte)'u', (byte)'v', 
    (byte)'w', (byte)'x', (byte)'y', (byte)'z', 
    (byte)'0', (byte)'1', (byte)'2', (byte)'3',
    (byte)'4', (byte)'5', (byte)'6', (byte)'7', 
    (byte)'8', (byte)'9', (byte)'+', (byte)'_'
  };
  private static boolean debug = false;
  public static void setDebug() { debug = true; }
  public static void clearDebug() { debug = false; }

  /**
   * Base 64 decode the input byte array into 
   */
  public static byte[] decode(byte[] input, int inOffset, int inLength)
    throws Exception
  {
    // 6 bits per byte to 8 bit bytes: 3/4*input.length ..
    byte[] decoded = new byte[(inLength*3 ) >>> 2];
    int state = 0, j = 0;
    byte d = 0;
    if (debug) {
      System.out.println("\nBase64.decode:");
    } 
    for (int i = 0; i < inLength; i++) {
      int c = (int)(input[i + inOffset]); // next 6 bits
      if (debug) {
	System.out.println("c = " + c + " byte = " + input[i + inOffset]);
      }	
      // convert to binary
      if ((int)'A' <= c && c <= (int)'Z')
	c = c - (int)'A';	// 0 <= c <= 25
      else if ((int)'a' <= c && c <= (int)'z')
	c = c - (int)'a' + 26;	// 26 <= c <= 51
      else if ((int)'0' <= c && c <= (int)'9')
	c = c - (int)'0' + 52;	// 52 <= c <= 61
      else if (c == (int)'+') c = 62;
      else if (c == (int)'_') c = 63; // URL Base64 only
      else {			// Not base 64 char
	if (debug) {
	  System.out.println("\nc = " + c + " byte = " + input[i + inOffset]);
	}
	throw new Exception("illegal value");
      }
      switch (state++) {	// Next six bits
      case 0:
	d = (byte)(c << 2);	// 12345600
	break;
      case 1:
	// 00123456 : 00000012
	d = (byte)(d | (byte)(c >> 4));
	decoded[j++] = d;	// 12345612
	d = (byte)(c << 4);	// 00123456 : 34560000
	break;
      case 2:
	// 00123456 : 00001234
	d = (byte)(d | (byte)(c >> 2));
	decoded[j++] = d;	// 34561234
	d = (byte)(c << 6);	// 56000000
	break;
      case 3:
	// 65123456
	decoded[j++] = (byte)(d | (byte)c); 
	state = 0;
	break;
      }
    }
    return decoded;
  }
  /**
   * Base 64 encode the input byte array
   */
  public static byte[] encode(byte[] input)
  {
    // 8 bits per byte to 6 bits per byte
    byte[] encoded = new byte[((input.length << 3) + 5)/6];

    // We do 8bit bytes in chunks of 3, and then what is less, since 3 8bit
    // bytes become 4 6bit bytes.
    int i = input.length/3;	// N 3 byte chunks
    int j = input.length % 3;	// What's left
    if (debug) {
      System.out.println("\nEncode[" + encoded.length + "]: i = " +
			 i + " j = " + j);
    }
    int k = 0, e = 0;
    int c = 0, d = 0;
    for (int l = 0; l < i; l++) {
      if (debug) System.out.println("k = " + k);
      c = (int)(input[k++] & 0XFF); // 12345678
      // 1st byte : 12345678 : 00123456
      encoded[e++] = codes[c >> 2];
      // 2nd byte : 00780000
      d = ((c & 0x03) << 4);
      c = (int)(input[k++] & 0XFF); // 9abcdefg
      // 2nd byte : 00789abc
      d |= ((c >> 4) & 0x0F); 
      encoded[e++] = codes[d];
      // 3rd byte : 00defg00
      d = (c & 0x0F) << 2;
      c = (int)(input[k++] &0xFF); // hijklmno
      // 3rd byte : 00defghi
      d |= ((c >> 6) & 0x0F);
      encoded[e++] = codes[d];
      // 4th byte : 00jklmno
      encoded[e++] = codes[c & 0x3F];
    }
    // Now, lets do the last 0,1 or 2 bytes
    if (j != 0) {
      c = (int)(input[k++] & 0xFF);
      // High order 6 bits : 00123456  
      encoded[e++] = codes[(c >> 2) & 0x3F];// 6bits #1
      // 00780000
      c = (c << 4) & 0x30;
      switch (j) {
      case 1:			// 1 byte
	// Make 2 6bit bytes
	// 00123456 : 00780000
	encoded[e] = codes[c];	// 6bits #2
	break;
      case 2:			// 2 bytes
	// Make 3 6bit bytes from 12345678 9abcdefg
	// 00123456 : 00789abc : 00defg00
	d = (int)(input[k] & 0xFF);// 8bits 9abcdefg
	c = (int)(c | ((d >> 4) & 0x0F));
	encoded[e++] = codes[c];// 00789abc
	// 00defg00
	d = (byte)((d << 2) & 0x3C);// 00defg00
	encoded[e] = codes[d];
	break;
      }
    }
    return encoded;
  }
}

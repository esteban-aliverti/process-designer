/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intalio.web.preprocessing.impl;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import org.apache.abdera.model.Entry;
import org.apache.abdera.protocol.client.ClientResponse;
import org.apache.log4j.Logger;

/**
 *
 * @author esteban
 */
public class GuvnorAtomHelper {
    private static final Logger _logger =
            Logger.getLogger(GuvnorAtomHelper.class);
    
    public static Set<String> getClassNamesFromWorkingSetEntry(Entry workingSetEntry, AbderaGuvnorHelper guvnorHelper) {
        Set<String> result = new HashSet<String>();

        //get the url of WS's binary content
        String binaryURL = workingSetEntry.getContentSrc().toString();

        if (binaryURL == null || binaryURL.isEmpty()) {
            throw new IllegalArgumentException("Working Set Entry doesn't have any binary URL");
        }
        
        try {
            binaryURL = URLDecoder.decode(binaryURL, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
        }

        ClientResponse resp = null;

        try{
            resp = guvnorHelper.invokeGET(binaryURL, "application/octet-stream");
        } catch (Exception e){
            throw new IllegalStateException("Error occurred when retrieving Working Sets from package: ", e);
        }
        
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(resp.getInputStream());

            boolean parsingValidFacts = false;
            boolean continueParsing = true;
            while (reader.hasNext() && continueParsing) {
                switch (reader.next()) {
                    case XMLStreamReader.START_ELEMENT:
                        if ("validFacts".equals(reader.getLocalName())) {
                            parsingValidFacts = true;
                        }
                        if ("string".equals(reader.getLocalName()) && parsingValidFacts) {
                            result.add(reader.getElementText());
                        }
                        break;
                    case XMLStreamReader.END_ELEMENT:
                        parsingValidFacts = false;
                        if ("validFacts".equals(reader.getLocalName())) {
                            //don't accept nested WorkingSets
                            continueParsing = false;
                        }
                        break;
                }
            }
        } catch (Exception e) {
            // we dont want to barf..just log that error happened
            _logger.error(e.getMessage());
        }

        return result;
    }
}

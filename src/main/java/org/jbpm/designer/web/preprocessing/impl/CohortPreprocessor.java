/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jbpm.designer.web.preprocessing.impl;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.protocol.client.ClientResponse;
import org.apache.log4j.Logger;

/**
 *
 * @author esteban
 */
public class CohortPreprocessor {
    private static final Logger _logger =
            Logger.getLogger(CohortPreprocessor.class);
    
    public static final String COHORT_WORKING_SET_NAME = "Configuration Facts";
    
    public static final String COHORT_DEFINITION_EXT = "modelDescriptor.attached";
    public static final String COHORT_TYPE_DEFINITION_TAG = "factType";
    public static final String COHORT_TYPE_DEFINITION_CLASS_ATTRIBUTE = "class";
    public static final String COHORT_FIELD_DEFINITION_TAG = "field";
    public static final String COHORT_FIELD_DEFINITION_NAME_ATTRIBUTE = "name";
    
    private String packageName;
    private String stencilPath;
    private AbderaGuvnorHelper guvnorHelper;

    public CohortPreprocessor(String packageName, AbderaGuvnorHelper guvnorHelper, String stencilPath) {
        this.packageName = packageName;
        this.guvnorHelper = guvnorHelper;
        this.stencilPath = stencilPath;
    }
    
    /**
     * Returns the Cohort elements defined in all
     * the cht elements present in Guvnor.
     * @return 
     */
    public List<CohortDefinition> getCohortTemplateData() throws IOException{
        
        Set<String> cohortNames = this.getCohortNames();
        return this.getFactTypesDescriptors(cohortNames);
    }
    
    
    
    private Set<String> getCohortNames(){
        
        Set<String> workingSetClasses = new HashSet<String>();
        
        String cohortWorkingSetURL = "/rest/packages/" + packageName + "/assets/"+COHORT_WORKING_SET_NAME;
        
        ClientResponse resp = null;

        try{
            resp = guvnorHelper.invokeGETGuvnor(cohortWorkingSetURL, "application/atom+xml");
            Document<Entry> document = resp.getDocument();
        
            workingSetClasses = GuvnorAtomHelper.getClassNamesFromWorkingSetEntry(document.getRoot(), guvnorHelper);
        } catch (Exception e){
            throw new IllegalStateException("Error occurred when retrieving "+COHORT_WORKING_SET_NAME+" from package: "+packageName, e);
        }

        return workingSetClasses;
    } 
    
    private List<CohortDefinition> getFactTypesDescriptors(Set<String> validTypes) throws IOException{
        
        List<CohortDefinition> result = new ArrayList<CohortDefinition>();
        List<String> factTypesDescriptorsURLs = this.getFactTypesDescriptorsURLs();
        
        for (String url : factTypesDescriptorsURLs) {
            result.addAll(this.getCohortContent(url, validTypes));
        }
        
        Collections.sort(result, new Comparator<CohortDefinition>(){

            public int compare(CohortDefinition o1, CohortDefinition o2) {
                return o1.getName().compareTo(o2.getName());
            }
            
        });
        
        return result;
    }
    
    private List<String> getFactTypesDescriptorsURLs(){
        String processesURL = "/rest/packages/" + packageName + "/assets?format="+COHORT_DEFINITION_EXT;

        ClientResponse resp = null;

        try{
            resp = guvnorHelper.invokeGETGuvnor(processesURL, "application/atom+xml");
        } catch (Exception e){
            throw new IllegalStateException("Error occurred when retrieving FactTypesDescriptors from package: "+packageName, e);
        }

        Document<Feed> document = resp.getDocument();
        
        List<String> factTypesDescriptorsSourceURLs = new ArrayList<String>();
        for (Entry entry : document.getRoot().getEntries()) {
            try {
                factTypesDescriptorsSourceURLs.add(URLDecoder.decode(entry.getContentSrc().toString(), "UTF-8"));
            } catch (UnsupportedEncodingException ex) {
            }
        }
        
        return factTypesDescriptorsSourceURLs;
    }
    
    private List<CohortDefinition> getCohortContent(String url, Set<String> validTypes){
        List<CohortDefinition> data = new ArrayList<CohortDefinition>();
        
        ClientResponse resp = null;
        try{
            resp = guvnorHelper.invokeGET(url, "application/octet-stream");
        } catch (Exception e){
            throw new IllegalStateException("Error occurred when retrieving Cohort Entry: "+url, e);
        }
        
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(resp.getInputStream());

            String typeClass = null;
            Set<String> typeClassFields = null;
            while (reader.hasNext()) {
                switch (reader.next()) {
                    case XMLStreamReader.START_ELEMENT:
                        if (COHORT_TYPE_DEFINITION_TAG.equals(reader.getLocalName())) {
                            typeClass = reader.getAttributeValue("", COHORT_TYPE_DEFINITION_CLASS_ATTRIBUTE);
                            if (typeClass == null || typeClass.trim().isEmpty()){
                                throw new IllegalArgumentException("<"+COHORT_TYPE_DEFINITION_TAG+"> element with no "+COHORT_TYPE_DEFINITION_CLASS_ATTRIBUTE+" attribute?");
                            }
                            typeClassFields = new HashSet<String>();
                        }
                        if (COHORT_FIELD_DEFINITION_TAG.equals(reader.getLocalName())) {
                            if (typeClassFields == null){
                                throw new IllegalStateException("Unexpected <"+COHORT_FIELD_DEFINITION_TAG+"> element");
                            }
                            //get only the name of the field
                            String fieldName = reader.getAttributeValue("", COHORT_FIELD_DEFINITION_NAME_ATTRIBUTE);
                            if (fieldName == null || fieldName.trim().isEmpty()){
                                throw new IllegalArgumentException("<"+COHORT_FIELD_DEFINITION_TAG+"> element with no "+COHORT_FIELD_DEFINITION_NAME_ATTRIBUTE+" attribute?");
                            }
                            typeClassFields.add(fieldName);
                        }
                        break;
                    case XMLStreamReader.END_ELEMENT:
                        if (COHORT_TYPE_DEFINITION_TAG.equals(reader.getLocalName())) {
                            if (typeClass == null){
                                throw new IllegalStateException("Unexpected </"+COHORT_TYPE_DEFINITION_TAG+"> element");
                            }
                            //just use the simple class name
                            if(typeClass.contains(".")){
                                typeClass = typeClass.substring(typeClass.lastIndexOf(".")+1, typeClass.length());
                            }
                            
                            //Create the cohort Definition
                            CohortDefinition cohort = new CohortDefinition();
                            cohort.setName(typeClass);
                            
                            //if an png file with the same name as the cohort 
                            //definitions exists under stencilsets/kmr/icons/model
                            //then the iconSrc value is set
                            if (new File(stencilPath+"/kmr/icons/model/"+typeClass+".png").exists()){
                                cohort.setIconSource("../icons/model/"+typeClass+".png");
                                cohort.setIconName(typeClass+".png");
                            }else{
                                //default values
                                cohort.setIconSource(null);
                                cohort.setIconName("cohort.type.png");
                            }
                            
                            //set the fields
                            for (String fieldName : typeClassFields) {
                                CohortFieldDefinition field = new CohortFieldDefinition();
                                field.setName(fieldName);
                                cohort.addField(field);
                            }
                            
                            if (validTypes == null || validTypes.isEmpty() || validTypes.contains(cohort.getName())){
                                data.add(cohort);
                            }
                            typeClass = null;
                            typeClassFields = null;
                        }
                        break;
                }
            }
        } catch (Exception e) {
            // we dont want to barf..just log that error happened
            _logger.error(e.getMessage());
        }
        
        return data;
    }
    
}

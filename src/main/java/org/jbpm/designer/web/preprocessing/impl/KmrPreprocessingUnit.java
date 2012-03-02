/**
 * *************************************
 * Copyright (c) Intalio, Inc 2010
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
***************************************
 */
package org.jbpm.designer.web.preprocessing.impl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.antlr.stringtemplate.StringTemplate;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.drools.process.core.ParameterDefinition;
import org.drools.process.core.impl.ParameterDefinitionImpl;
import org.jbpm.process.workitem.WorkDefinitionImpl;
import org.drools.process.core.datatype.DataType;
import org.mvel2.MVEL;


import java.util.Arrays;
import java.util.Collections;
import javax.xml.namespace.QName;
import org.antlr.stringtemplate.AttributeRenderer;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.ExtensibleElement;
import org.apache.abdera.model.Feed;
import org.apache.abdera.protocol.client.ClientResponse;
import org.jbpm.designer.web.preprocessing.IDiagramPreprocessingUnit;
import org.jbpm.designer.web.profile.IDiagramProfile;
import org.jbpm.designer.web.profile.impl.ExternalInfo;

/**
 * KmrPreprocessingUnit - preprocessing unit for the jbpm profile
 *
 * @author Esteban Aliverti
 */
public class KmrPreprocessingUnit implements IDiagramPreprocessingUnit {

    private static final Logger _logger =
            Logger.getLogger(KmrPreprocessingUnit.class);
    public final static String STENCILSET_PATH = "stencilsets";
    public static final String WORKITEM_DEFINITION_EXT = "wid";
    private String stencilPath;
    private String origStencilFilePath;
    private String stencilFilePath;
    private String outData = "";
    private String workitemSVGFilePath;
    private String origWorkitemSVGFile;
    private String modelSVGFilePath;
    private String cohortSVGFilePath;
    private String origCohortSVGFile;
    private String generatedCohortSVGFile;
    private String origModelSVGFile;
    private IDiagramProfile profile;

    private String guvnorBaseURL;
    private AbderaGuvnorHelper guvnorHelper;
    
    public KmrPreprocessingUnit(ServletContext servletContext) {
        stencilPath = servletContext.getRealPath("/" + STENCILSET_PATH);
        origStencilFilePath = stencilPath + "/kmr/stencildata/" + "kmr.orig.json";
        stencilFilePath = stencilPath + "/kmr/" + "kmr.json";
        workitemSVGFilePath = stencilPath + "/kmr/view/activity/workitems/";
        origWorkitemSVGFile = workitemSVGFilePath + "workitem.orig";
        modelSVGFilePath = stencilPath + "/kmr/view/model/dynamic/";
        origModelSVGFile = modelSVGFilePath + "model.orig";
        cohortSVGFilePath = stencilPath + "/kmr/view/model/";
        origCohortSVGFile = cohortSVGFilePath + "dynamic/cohort.orig";
        generatedCohortSVGFile = cohortSVGFilePath + "/cohort.svg";
        
        
    }

    public String getOutData() {
        if (outData != null && outData.length() > 0) {
            if (outData.endsWith(",")) {
                outData = outData.substring(0, outData.length() - 1);
            }
        }
        return outData;
    }

    public void preprocess(HttpServletRequest req, HttpServletResponse res, IDiagramProfile profile) {
        String uuid = req.getParameter("uuid");
        String[] wsUuids = req.getParameterValues("wsUuid");
        String securityToken = req.getParameter("securityToken");

        this.profile = profile;
        guvnorBaseURL = ExternalInfo.getExternalProtocol(profile) + "://" + ExternalInfo.getExternalHost(profile)
                + "/" + profile.getExternalLoadURLSubdomain().substring(0, profile.getExternalLoadURLSubdomain().indexOf("/"));
        
        guvnorHelper = new AbderaGuvnorHelper(guvnorBaseURL, securityToken);

        List<String> wsUuidsList = wsUuids == null ? new ArrayList<String>() : Arrays.asList(wsUuids);

        outData = "";
        // check with guvnor to see what packages exist
        List<String> packageNames = findPackages(profile);

        //get the package where the asset with the given UUID is defined
        String[] info = findPackageAndNameForProcessUUID(uuid, packageNames);
        String packageName = info[0];
        String processName = info[1];
        
        //Get back the list of WI configs names and content of the given package
        Map<String, List<WorkDefinitionImpl>> workitemConfigInfo = null;
        try {
            workitemConfigInfo = findWorkitemInfoForPackage(packageName);
        } catch (Exception ex) {
            _logger.error("Error retrieving Work Item definitions", ex);
        }

        // set the out parameter and create workDefinitions
        Map<String, WorkDefinitionImpl> workDefinitions = new HashMap<String, WorkDefinitionImpl>();
        for (Map.Entry<String, List<WorkDefinitionImpl>> definition : workitemConfigInfo.entrySet()) {

            for (WorkDefinitionImpl workDefinitionImpl : definition.getValue()) {
                //concatenate outData
                outData += workDefinitionImpl.getName() + ",";

                //add security token to urls
                //workDefinitionImpl.setIcon(workDefinitionImpl.getIcon()+"?securityToken="+securityToken+"&SAMLResponseEncoded=true");
                
                //convert from List<WorkDefinitionImpl> to Map<String,WorkDefinitionImpl>
                //so it can be used in template
                workDefinitions.put(workDefinitionImpl.getName(), workDefinitionImpl);
            }

        }

        //get the available classes according to the passed working-sets
        List<String> workingSetsClassNames = new ArrayList(getWorkingSetsClasses(packageName, wsUuidsList, profile));
        Collections.sort(workingSetsClassNames);
        
        //get all the Cohort Types from the package
        List<CohortDefinition> cohortTemplateData = new ArrayList<CohortDefinition>(); 
        try{        
            cohortTemplateData = new CohortPreprocessor(packageName, guvnorHelper, stencilPath).getCohortTemplateData();
        } catch (Exception e) {
            _logger.error("Failed to setup Cohort Configuration Facts",e);
        }
        
        // parse the profile json to include config data
        try {
            // parse the orig stencil data with workitem definitions
            StringTemplate workItemTemplate = new StringTemplate(readFile(origStencilFilePath));
            workItemTemplate.setAttribute("workitemDefs", workDefinitions);

            workItemTemplate.setAttribute("workingSetsClassNames", workingSetsClassNames);
            
            workItemTemplate.setAttribute("cohortDefs", cohortTemplateData);

            // default the process id
            workItemTemplate.setAttribute("processid", "com.sample.bpmm2");
            
            workItemTemplate.setAttribute("packageName", packageName);
            
            workItemTemplate.setAttribute("processName", processName);
            
            workItemTemplate.registerRenderer(String.class, new AttributeRenderer() {

                public String toString(Object o) {
                    return o.toString();
                }

                public String toString(Object o, String format) {
                    if (format.equals("upper")) {
                        return o.toString().toUpperCase();
                    }
                    throw new UnsupportedOperationException("Unsuported format " + format);
                }
            });

            // delete stencil data json if exists
            deletefile(stencilFilePath);
            // copy our results as the stencil json data
            createAndWriteToFile(stencilFilePath, workItemTemplate.toString());
        } catch (Exception e) {
            _logger.error("Failed to setup workitems : " + e.getMessage());
        }
        // create and parse the view svg to include WorkItem data
        createAndParseWorkItemSVGs(workDefinitions);

        // create and parse the view svg to include Model data
        //createAndParseModelSVGs(packageName, workingSetsClassNames);
        
        // create the cohort.svg file
        createAndParseCohortSVG(cohortTemplateData);
    }

    @SuppressWarnings("unchecked")
    private void createAndParseWorkItemSVGs(Map<String, WorkDefinitionImpl> workDefinitions) {
        // first delete all existing workitem svgs
        Collection<File> workitemsvgs = FileUtils.listFiles(new File(workitemSVGFilePath), new String[]{"svg"}, true);
        if (workitemsvgs != null) {
            for (File wisvg : workitemsvgs) {
                deletefile(wisvg);
            }
        }
        try {
            for (Map.Entry<String, WorkDefinitionImpl> definition : workDefinitions.entrySet()) {
                StringTemplate workItemTemplate = new StringTemplate(readFile(origWorkitemSVGFile));
                workItemTemplate.setAttribute("workitemDef", definition.getValue());
                String fileToWrite = workitemSVGFilePath + definition.getValue().getName() + ".svg";
                createAndWriteToFile(fileToWrite, workItemTemplate.toString());
            }
        } catch (Exception e) {
            _logger.error("Failed to setup workitem svg images : " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void createAndParseCohortSVG(List<CohortDefinition> definitions) {
        try {
            // first delete previous svg
            deletefile(generatedCohortSVGFile);
            
            //only use definitions that have a valid iconSource attribute
            List<CohortDefinition> validDefinitions = new ArrayList<CohortDefinition>();
            for (CohortDefinition cohortDefinition : definitions) {
                if (cohortDefinition.getIconSource() != null){
                    validDefinitions.add(cohortDefinition);
                }
            }
            
            StringTemplate cohortTemplate = new StringTemplate(readFile(origCohortSVGFile));
            cohortTemplate.setAttribute("cohortDefs", validDefinitions);
            createAndWriteToFile(generatedCohortSVGFile, cohortTemplate.toString());
            
        } catch (Exception ex) {
            _logger.error("Failed to setup Cohort svg image : " + ex.getMessage());
        }
        
    }

    @SuppressWarnings("unchecked")
    private void createAndParseModelSVGs(String packageName, List<String> workingSetsClassNames) {

        String baseURL = ExternalInfo.getExternalProtocol(profile) + "://" + ExternalInfo.getExternalHost(profile)
                + "/" + profile.getExternalLoadURLSubdomain().substring(0, profile.getExternalLoadURLSubdomain().indexOf("/"));

        String baseIconURL = baseURL + "/rest/packages/" + packageName + "/assets/";

        // first delete all existing model svgs
        Collection<File> modelsvgs = FileUtils.listFiles(new File(modelSVGFilePath), new String[]{"svg"}, true);
        if (modelsvgs != null) {
            for (File modelsvg : modelsvgs) {
                deletefile(modelsvg);
            }
        }
        try {
            StringTemplate modelTemplate = new StringTemplate(readFile(origModelSVGFile));
            for (String className : workingSetsClassNames) {
                modelTemplate.setAttribute("modelClassName", className);
                modelTemplate.setAttribute("modelClassNameIconURL", baseIconURL + className + ".ICON/binary");
                String fileToWrite = modelSVGFilePath + className + ".svg";
                createAndWriteToFile(fileToWrite, modelTemplate.toString());
                modelTemplate.reset();
            }
        } catch (Exception e) {
            _logger.error("Failed to setup model svg images : " + e.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<WorkDefinitionImpl> evaluateWorkDefinitionContent(String content) {

        List<WorkDefinitionImpl> result = new ArrayList<WorkDefinitionImpl>();

        List<Map<String, Object>> workDefinitionsMaps = (List<Map<String, Object>>) MVEL.eval(content, new HashMap());

        for (Map<String, Object> workDefinitionMap : workDefinitionsMaps) {
            if (workDefinitionMap != null) {
                WorkDefinitionImpl workDefinition = new WorkDefinitionImpl();
                workDefinition.setName((String) workDefinitionMap.get("name"));
                workDefinition.setDisplayName((String) workDefinitionMap.get("displayName"));
                workDefinition.setIcon((String) workDefinitionMap.get("icon"));
                workDefinition.setCustomEditor((String) workDefinitionMap.get("customEditor"));
                Set<ParameterDefinition> parameters = new HashSet<ParameterDefinition>();
                if (workDefinitionMap.get("parameters") != null) {
                    Map<String, DataType> parameterMap = (Map<String, DataType>) workDefinitionMap.get("parameters");
                    if (parameterMap != null) {
                        for (Map.Entry<String, DataType> entry : parameterMap.entrySet()) {
                            parameters.add(new ParameterDefinitionImpl(entry.getKey(), entry.getValue()));
                        }
                    }
                    workDefinition.setParameters(parameters);
                }

                if (workDefinitionMap.get("results") != null) {
                    Set<ParameterDefinition> results = new HashSet<ParameterDefinition>();
                    Map<String, DataType> resultMap = (Map<String, DataType>) workDefinitionMap.get("results");
                    if (resultMap != null) {
                        for (Map.Entry<String, DataType> entry : resultMap.entrySet()) {
                            results.add(new ParameterDefinitionImpl(entry.getKey(), entry.getValue()));
                        }
                    }
                    workDefinition.setResults(results);
                }
                if (workDefinitionMap.get("defaultHandler") != null) {
                    workDefinition.setDefaultHandler((String) workDefinitionMap.get("defaultHandler"));
                }
                if (workDefinitionMap.get("dependencies") != null) {
                    workDefinition.setDependencies(((List<String>) workDefinitionMap.get("dependencies")).toArray(new String[0]));
                }
                result.add(workDefinition);
            }
        }

        return result;
    }

    private String getWorkitemConfigContent(String packageName, String configInfoName){
        String content = "";

        String widURL = "/rest/packages/"+packageName+"/assets/"+configInfoName+"/source";
        
        ClientResponse resp = null;

        try{
            resp = guvnorHelper.invokeGETGuvnor(widURL, "text/plain");
        } catch (Exception e){
            throw new IllegalStateException("Error occurred when retrieving Work Item Config from package: ", e);
        }

        try {
            InputStream in = resp.getInputStream();
            StringWriter writer = new StringWriter();
            IOUtils.copy(in, writer, "UTF-8");
            content = writer.toString();
        } catch (Exception e) {
            // we dont want to barf..just log that error happened
            _logger.error(e.getMessage());
        }

        return content;
    }

    private String[] findPackageAndNameForProcessUUID(String uuid, List<String> packageNames) {

        for (String nextPackage : packageNames) {
            //The UUID must be of a bpmn2 asset
            String processesURL = "/rest/packages/" + nextPackage + "/assets?format=bpmn2";

            ClientResponse resp = null;

            try{
                resp = guvnorHelper.invokeGETGuvnor(processesURL, "application/atom+xml");
            } catch (Exception e){
                throw new IllegalStateException("Error occurred when retrieving Processes from package: ", e);
            }

            Document<Feed> document = resp.getDocument();

            //check the UUID of the returned assets to see if the one we 
            //are looking for is in this package
            for (Entry entry : document.getRoot().getEntries()) {
                ExtensibleElement metadataExtension = entry.getExtension(new QName("", "metadata"));
                String assetUuid = ((ExtensibleElement) metadataExtension.getExtension(new QName("", "uuid"))).getSimpleExtension(new QName("", "value"));
                if (uuid.equals(assetUuid)) {
                    return new String[]{nextPackage,entry.getTitle()};
                }
            }
        }

        throw new IllegalArgumentException("Couldn't find asset's package!");
    }

    /**
     * Return a Map of &lt;WorkItem title, List<WorkItemDefinition>&gt; for each of
     * the WorkItem definitions present in the package
     * @param packageName
     * @return 
     */
    private Map<String, List<WorkDefinitionImpl>> findWorkitemInfoForPackage(String packageName) {

        String widsURL = "/rest/packages/" + packageName + "/assets?format=" + WORKITEM_DEFINITION_EXT;

        ClientResponse resp = null;

        try{
            resp = guvnorHelper.invokeGETGuvnor(widsURL, "application/atom+xml");
        } catch (Exception e){
            throw new IllegalStateException("Error occurred when retrieving Work Item definitions from package: ", e);
        }

        Document<Feed> document = resp.getDocument();

        Map<String, List<WorkDefinitionImpl>> result = new HashMap<String, List<WorkDefinitionImpl>>();
        for (Entry entry : document.getRoot().getEntries()) {
            //get the content of the wid
            String content = this.getWorkitemConfigContent(packageName, entry.getTitle());

            //convert the content to WorkDefinitionImpl
            List<WorkDefinitionImpl> definition = evaluateWorkDefinitionContent(content);

            result.put(entry.getTitle(), definition);
        }

        return result;
    }

    private List<String> findPackages(IDiagramProfile profile) {
        List<String> packages = new ArrayList<String>();

        String packagesURL = "/rest/packages/";

        ClientResponse resp = null;

        try{
            resp = guvnorHelper.invokeGETGuvnor(packagesURL, "application/atom+xml");
        } catch (Exception e){
            throw new IllegalStateException("Couldn't get list of packages from Guvnor", e);
        }

        Document<Feed> document = resp.getDocument();
        for (Entry entry : document.getRoot().getEntries()) {
            packages.add(entry.getTitle());
        }

        return packages;

    }

    private Set<String> getWorkingSetsClasses(String packageName, List<String> wsUuids, IDiagramProfile profile) {

        Set<String> result = new HashSet<String>();

        if (wsUuids == null || wsUuids.isEmpty()) {
            return result;
        }

        String workingSetsURL = "/rest/packages/" + packageName + "/assets?format=workingset";

        ClientResponse resp = null;

        try{
            resp = guvnorHelper.invokeGETGuvnor(workingSetsURL, "application/atom+xml");
        } catch (Exception e){
            throw new IllegalStateException("Error occurred when retrieving Working Sets from package: ", e);
        }
        
        Document<Feed> document = resp.getDocument();

        //Convert from UUIDs to entry
        List<Entry> workingSetEntries = new ArrayList<Entry>();
        for (Entry entry : document.getRoot().getEntries()) {
            //get the UUID
            ExtensibleElement metadataExtension = entry.getExtension(new QName("", "metadata"));
            String uuid = ((ExtensibleElement) metadataExtension.getExtension(new QName("", "uuid"))).getSimpleExtension(new QName("", "value"));

            if (wsUuids.contains(uuid)) {
                workingSetEntries.add(entry);
            }
        }

        //All the Working-Sets UUID should have its corresponding name...
        //And because all the WS must be in the same package we can
        //do this:
        if (wsUuids.size() != workingSetEntries.size()) {
            _logger.warn("Couldn't find all the requiered working-sets.");
        }

        //for each working-set we need to get the classes names they define
        for (Entry workingSetEntry : workingSetEntries) {
            result.addAll(GuvnorAtomHelper.getClassNamesFromWorkingSetEntry(workingSetEntry, guvnorHelper));
        }

        return result;
    }

    private String readFile(String pathname) throws IOException {
        StringBuilder fileContents = new StringBuilder();
        Scanner scanner = new Scanner(new File(pathname));
        String lineSeparator = System.getProperty("line.separator");
        try {
            while (scanner.hasNextLine()) {
                fileContents.append(scanner.nextLine() + lineSeparator);
            }
            return fileContents.toString();
        } finally {
            scanner.close();
        }
    }

    private void deletefile(String file) {
        File f = new File(file);
        boolean success = f.delete();
        if (!success) {
            _logger.info("Unable to delete file :" + file);
        } else {
            _logger.info("Successfully deleted file :" + file);
        }
    }

    private void deletefile(File f) {
        String fname = f.getAbsolutePath();
        boolean success = f.delete();
        if (!success) {
            _logger.info("Unable to delete file :" + fname);
        } else {
            _logger.info("Successfully deleted file :" + fname);
        }
    }

    private void createAndWriteToFile(String file, String content) throws Exception {
        Writer output = null;
        output = new BufferedWriter(new FileWriter(file));
        output.write(content);
        output.close();
        _logger.info("Created file:" + file);
    }

    
}

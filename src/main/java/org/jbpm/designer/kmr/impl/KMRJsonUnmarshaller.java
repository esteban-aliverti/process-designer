package org.jbpm.designer.kmr.impl;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.codehaus.jackson.JsonFactory;
import org.eclipse.bpmn2.Activity;
import org.eclipse.bpmn2.Bpmn2Factory;
import org.eclipse.bpmn2.CatchEvent;
import org.eclipse.bpmn2.CompensateEventDefinition;
import org.eclipse.bpmn2.ConditionalEventDefinition;
import org.eclipse.bpmn2.Definitions;
import org.eclipse.bpmn2.Error;
import org.eclipse.bpmn2.ErrorEventDefinition;
import org.eclipse.bpmn2.Escalation;
import org.eclipse.bpmn2.EscalationEventDefinition;
import org.eclipse.bpmn2.EventDefinition;
import org.eclipse.bpmn2.FlowElement;
import org.eclipse.bpmn2.FormalExpression;
import org.eclipse.bpmn2.ItemDefinition;
import org.eclipse.bpmn2.Message;
import org.eclipse.bpmn2.MessageEventDefinition;
import org.eclipse.bpmn2.Process;
import org.eclipse.bpmn2.RootElement;
import org.eclipse.bpmn2.Signal;
import org.eclipse.bpmn2.SignalEventDefinition;
import org.eclipse.bpmn2.TextAnnotation;
import org.eclipse.emf.ecore.util.FeatureMap;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jackson.JsonGenerator;
import org.eclipse.bpmn2.BaseElement;
import org.eclipse.bpmn2.FlowElementsContainer;
import org.jbpm.designer.bpmn2.impl.Bpmn2JsonUnmarshaller;

/**
 * @author Esteban Aliverti
 *
 * an unmarshaller to transform JSON into BPMN 2.0 elements for KMR.
 *
 */
public class KMRJsonUnmarshaller extends Bpmn2JsonUnmarshaller {

    private Map<String,Map<String,String>> cohortTypes = new HashMap<String, Map<String, String>>();
    
    public KMRJsonUnmarshaller() {
    }

    @Override
    public void setCatchEventsInfo(FlowElementsContainer container, Definitions def, List<Signal> toAddSignals, Set<Error> toAddErrors,
            Set<Escalation> toAddEscalations, Set<Message> toAddMessages, Set<ItemDefinition> toAddItemDefinitions) {
        List<FlowElement> flowElements = container.getFlowElements();
        for (FlowElement fe : flowElements) {
            if (fe instanceof CatchEvent) {
                if (((CatchEvent) fe).getEventDefinitions().size() > 0) {
                    EventDefinition ed = ((CatchEvent) fe).getEventDefinitions().get(0);
                    if (ed instanceof SignalEventDefinition) {
//                                Signal signal = Bpmn2Factory.eINSTANCE.createSignal();
//                                Iterator<FeatureMap.Entry> iter = ed.getAnyAttribute().iterator();
//                                while(iter.hasNext()) {
//                                    FeatureMap.Entry entry = iter.next();
//                                    if(entry.getEStructuralFeature().getName().equals("signalrefname")) {
//                                        signal.setName((String) entry.getValue());
//                                    }
//                                }
//                                toAddSignals.add(signal);
//                                ((SignalEventDefinition) ed).setSignalRef(signal);
                    } else if (ed instanceof ErrorEventDefinition) {
                        String errorCode = null;
                        String errorId = null;
                        Iterator<FeatureMap.Entry> iter = ed.getAnyAttribute().iterator();
                        while (iter.hasNext()) {
                            FeatureMap.Entry entry = iter.next();
                            if (entry.getEStructuralFeature().getName().equals("erefname")) {
                                errorId = (String) entry.getValue();
                                errorCode = (String) entry.getValue();
                            }
                        }

                        Error err = this._errors.get(errorCode);
                        if (err == null) {
                            err = Bpmn2Factory.eINSTANCE.createError();
                            err.setId(errorId);
                            err.setErrorCode(errorCode);
                            this._errors.put(errorCode, err);
                        }

                        toAddErrors.add(err);
                        ((ErrorEventDefinition) ed).setErrorRef(err);

                    } else if (ed instanceof EscalationEventDefinition) {
                        String escalationCode = null;
                        Iterator<FeatureMap.Entry> iter = ed.getAnyAttribute().iterator();
                        while (iter.hasNext()) {
                            FeatureMap.Entry entry = iter.next();
                            if (entry.getEStructuralFeature().getName().equals("esccode")) {
                                escalationCode = (String) entry.getValue();
                                break;
                            }
                        }

                        Escalation escalation = this._escalations.get(escalationCode);
                        if (escalation == null) {
                            escalation = Bpmn2Factory.eINSTANCE.createEscalation();
                            escalation.setEscalationCode(escalationCode);
                            this._escalations.put(escalationCode, escalation);
                        }
                        toAddEscalations.add(escalation);
                        ((EscalationEventDefinition) ed).setEscalationRef(escalation);
                    } else if (ed instanceof MessageEventDefinition) {
                        String idefId = null;
                        String msgId = null;

                        Iterator<FeatureMap.Entry> iter = ed.getAnyAttribute().iterator();
                        while (iter.hasNext()) {
                            FeatureMap.Entry entry = iter.next();
                            if (entry.getEStructuralFeature().getName().equals("msgref")) {
                                msgId = (String) entry.getValue();
                                idefId = (String) entry.getValue() + "Type";
                            }
                        }

                        ItemDefinition idef = _itemDefinitions.get(idefId);
                        if (idef == null) {
                            idef = Bpmn2Factory.eINSTANCE.createItemDefinition();
                            idef.setId(idefId);
                            _itemDefinitions.put(idefId, idef);
                        }

                        Message msg = _messages.get(msgId);
                        if (msg == null) {
                            msg = Bpmn2Factory.eINSTANCE.createMessage();
                            msg.setId(msgId);
                            msg.setItemRef(idef);
                            _messages.put(msgId, msg);
                        }


                        toAddMessages.add(msg);
                        toAddItemDefinitions.add(idef);
                        ((MessageEventDefinition) ed).setMessageRef(msg);
                    } else if (ed instanceof CompensateEventDefinition) {
                        Iterator<FeatureMap.Entry> iter = ed.getAnyAttribute().iterator();
                        while (iter.hasNext()) {
                            FeatureMap.Entry entry = iter.next();
                            if (entry.getEStructuralFeature().getName().equals("actrefname")) {
                                String activityNameRef = (String) entry.getValue();
                                // we have to iterate again through all flow elements
                                // in order to find our activity name
                                List<RootElement> re = def.getRootElements();
                                for (RootElement r : re) {
                                    if (r instanceof Process) {
                                        Process p = (Process) r;
                                        List<FlowElement> fes = p.getFlowElements();
                                        for (FlowElement f : fes) {
                                            if (f instanceof Activity && ((Activity) f).getName().equals(activityNameRef)) {
                                                ((CompensateEventDefinition) ed).setActivityRef((Activity) f);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (ed instanceof ConditionalEventDefinition) {
                        ConditionalEventDefinition conditionalEventDefinition = (ConditionalEventDefinition) ed;
                        if (conditionalEventDefinition.getCondition() != null
                                && ((FormalExpression) conditionalEventDefinition.getCondition()).getBody() != null
                                && !((FormalExpression) conditionalEventDefinition.getCondition()).getBody().trim().equals("")) {
                            String body = ((FormalExpression) conditionalEventDefinition.getCondition()).getBody();

                            String processId = def.getRootElements().get(0).getId();

                            StringBuilder ruleHeader = new StringBuilder();
                            StringBuilder defeaterRules = new StringBuilder();
                            for (Entry<String, Map<String, String>> entry : cohortTypes.entrySet()) {
                                ruleHeader.append("@");
                                ruleHeader.append(entry.getKey());
                                ruleHeader.append("([");

                                defeaterRules.append("rule \"");
                                defeaterRules.append(processId);
                                defeaterRules.append(" defeater ");
                                defeaterRules.append(entry.getKey());
                                defeaterRules.append("\"\n");
                                defeaterRules.append("@activationListener('direct')\n");
                                defeaterRules.append("when\n");
                                defeaterRules.append("\t$a : Activation(rule.name == \"RuleFlow-Start-");
                                defeaterRules.append(processId);
                                defeaterRules.append("\")\n");
                                defeaterRules.append("\tnot ");
                                defeaterRules.append(entry.getKey());
                                defeaterRules.append("( ");

                                String separator = "";
                                for (Entry<String, String> property : entry.getValue().entrySet()) {
                                    if (!property.getKey().toLowerCase().startsWith("cohortproperty_") || property.getValue().trim().equals("")) {
                                        continue;
                                    }

                                    String propertyName = property.getKey().substring("cohortproperty_".length());
                                    //since designer lowercase the id of the properties, we ned to get the real field name from another hidden property
                                    propertyName = entry.getValue().get(propertyName + "_name");

                                    ruleHeader.append("\"");
                                    ruleHeader.append(propertyName);
                                    ruleHeader.append("\" : \"");
                                    ruleHeader.append(property.getValue());
                                    ruleHeader.append("\", ");

                                    defeaterRules.append(separator);
                                    defeaterRules.append(propertyName);
                                    defeaterRules.append(" == \"");
                                    defeaterRules.append(property.getValue());
                                    defeaterRules.append("\"");


                                    if (separator.equals("")) {
                                        separator = ", ";
                                    }
                                }

                                //append also the process Id
                                ruleHeader.append("\"processId\" : \"");
                                ruleHeader.append(processId);
                                ruleHeader.append("\"");
                                ruleHeader.append("])\n");

                                defeaterRules.append(")\n");
                                defeaterRules.append("then\n");
                                defeaterRules.append("\tkcontext.cancelActivation( $a );\n");
                                defeaterRules.append("end\n\n");
                            }
                            body = "|-- auto-generated --|\n" + defeaterRules.toString() + "\n------\n" + ruleHeader.toString() + body;
                            ((FormalExpression) conditionalEventDefinition.getCondition()).setBody(body);
                        }
                    }
                }
            } else if (fe instanceof FlowElementsContainer) {
                setCatchEventsInfo((FlowElementsContainer) fe, def, toAddSignals, toAddErrors, toAddEscalations, toAddMessages, toAddItemDefinitions);
            }
        }
    }

    @Override
    protected void applyTextAnnotationProperties(TextAnnotation ta, Map<String, String> properties) {
        String text = properties.get("text");
        //if ta has a 'modelentity' property means that is one of our
        //model_entities
        if (properties.get("modelentity") != null) {
            //Set custom text as a concatenation of modelentity and the property
            //indicated by fieldconstraint
            text = "KMRCustom--" + properties.get("modelentity") + "--" + properties.get("fieldconstraint") + "--" + properties.get(properties.get("fieldconstraint").toLowerCase());
        }

        //if ta has a 'cohortentity' property means that is one of our
        //cohort entities.
        if (properties.get("cohortentity") != null) { 
            //Set custom text as a concatenation of modelentity and the property
            //indicated by fieldconstraint
            text = "KMRCustomCohort--";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                JsonGenerator jsonGenerator = new JsonFactory().createJsonGenerator(new OutputStreamWriter(out));

                //convert properties to json
                jsonGenerator.writeStartObject();
                for (Entry<String, String> entry : properties.entrySet()) {
                    jsonGenerator.writeStringField(entry.getKey(), entry.getValue());
                }
                jsonGenerator.writeEndObject();
                jsonGenerator.flush();
                jsonGenerator.close();
                text += out.toString();

                cohortTypes.put(properties.get("cohortentity"), properties);
            } catch (IOException ex) {
                Logger.getLogger(KMRJsonUnmarshaller.class.getName()).log(Level.SEVERE, null, ex);
                text += "ERROR: " + ex.getMessage();
            }

        }

        if (text != null) {
            ta.setText(text);
        } else {
            ta.setText("");
        }
        // default
        ta.setTextFormat("text/plain");
    }
    
    @Override
    protected BaseElement createBaseElement(String stencil, String taskType, boolean customElement){
        return KMRStencil.createElement(stencil, taskType, customElement);
    }
    
    
    @Override
    protected String wrapInCDATABlock(String value) {
    	//return "<![CDATA[" + value + "]]>";
        return value;
    }

}

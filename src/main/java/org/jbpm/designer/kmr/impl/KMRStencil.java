package org.jbpm.designer.kmr.impl;

import org.eclipse.bpmn2.BaseElement;
import org.jbpm.designer.bpmn2.impl.Bpmn20Stencil;

/**
 * the mapping to stencil ids to BPMN 2.0 metamodel classes
 *
 */


public class KMRStencil {
    
    public static BaseElement createElement(String stencilId, String taskType, boolean customElement ) {
        
        BaseElement element;
        
        //check if it is one of 'our' elements
        //Fact Model
        if (stencilId.startsWith("Model_")){
            stencilId="TextAnnotation";
            taskType=null;
        }
        //Cohort
        if (stencilId.startsWith("Cohort_")){
            stencilId="TextAnnotation";
            taskType=null;
        }
        
        //lets go to Bpmn20Stencil
        element = Bpmn20Stencil.createElement(stencilId, taskType, customElement);
        
        return element;
    }
}

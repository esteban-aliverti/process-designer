/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intalio.web.preprocessing.impl;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author esteban
 */
public class CohortDefinition {
    private String name;
    private String iconSource;
    private String iconName;
    public Set<CohortFieldDefinition> fields = new HashSet<CohortFieldDefinition>();

    public Set<CohortFieldDefinition> getFields() {
        return fields;
    }

    public void setFields(Set<CohortFieldDefinition> fields) {
        this.fields = fields;
    }

    public void addField(CohortFieldDefinition field){
        this.fields.add(field);
    }
    
    public String getIconSource() {
        return iconSource;
    }

    public void setIconSource(String iconSource) {
        this.iconSource = iconSource;
    }

    public String getIconName() {
        return iconName;
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    
}

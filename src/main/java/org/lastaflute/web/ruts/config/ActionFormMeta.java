/*
 * Copyright 2014-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.lastaflute.web.ruts.config;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dbflute.helper.message.ExceptionMessageBuilder;
import org.dbflute.optional.OptionalThing;
import org.dbflute.util.DfReflectionUtil;
import org.lastaflute.di.helper.beans.BeanDesc;
import org.lastaflute.di.helper.beans.PropertyDesc;
import org.lastaflute.di.helper.beans.factory.BeanDescFactory;
import org.lastaflute.web.exception.ActionFormCreateFailureException;
import org.lastaflute.web.ruts.VirtualActionForm;
import org.lastaflute.web.ruts.VirtualActionForm.RealFormSupplier;
import org.lastaflute.web.util.LaActionExecuteUtil;

/**
 * @author modified by jflute (originated in Seasar)
 */
public class ActionFormMeta {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final String formKey; // not null
    protected final Class<?> formType; // not null
    protected final OptionalThing<Parameter> listFormParameter; // not null
    protected final Map<String, ActionFormProperty> propertyMap; // not null

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public ActionFormMeta(String formKey, Class<?> formType, OptionalThing<Parameter> listFormParameter) {
        this.formKey = formKey;
        this.formType = formType;
        this.listFormParameter = listFormParameter;
        this.propertyMap = setupProperties(formType);
    }

    protected Map<String, ActionFormProperty> setupProperties(Class<?> formType) {
        final BeanDesc beanDesc = BeanDescFactory.getBeanDesc(formType);
        final int propertyDescSize = beanDesc.getPropertyDescSize();
        final Map<String, ActionFormProperty> map = new HashMap<String, ActionFormProperty>(propertyDescSize);
        for (int i = 0; i < propertyDescSize; i++) {
            final PropertyDesc pd = beanDesc.getPropertyDesc(i);
            if (pd.isReadable()) {
                final ActionFormProperty property = newActionFormProperty(pd);
                addProperty(map, property);
            }
        }
        return map;
    }

    protected ActionFormProperty newActionFormProperty(PropertyDesc pd) {
        return new ActionFormProperty(pd);
    }

    protected void addProperty(Map<String, ActionFormProperty> map, ActionFormProperty property) {
        map.put(property.getPropertyName(), property);
    }

    // ===================================================================================
    //                                                                   Property Handling
    //                                                                   =================
    public Collection<ActionFormProperty> properties() {
        return propertyMap.values();
    }

    public boolean hasProperty(String propertyName) {
        return getProperty(propertyName) != null;
    }

    public ActionFormProperty getProperty(String propertyName) {
        return propertyMap.get(propertyName);
    }

    // ===================================================================================
    //                                                                        Virtual Form
    //                                                                        ============
    public VirtualActionForm createActionForm() {
        return newVirtualActionForm(getActionFormSupplier(), this);
    }

    protected VirtualActionForm newVirtualActionForm(RealFormSupplier formSupplier, ActionFormMeta formMeta) {
        return new VirtualActionForm(formSupplier, formMeta);
    }

    protected RealFormSupplier getActionFormSupplier() {
        return () -> {
            try {
                checkInstantiatedFormType();
                return formType.newInstance();
            } catch (Exception e) {
                throwActionFormCreateFailureException(e);
                return null; // unreachable
            }
        };
    }

    protected void checkInstantiatedFormType() {
        if (List.class.isAssignableFrom(formType)) { // e.g. List<SeaForm>, JSON body of list type
            String msg = "Cannot instantiate the form because of list type, should not come here:" + formType;
            throw new IllegalStateException(msg);
        }
    }

    protected void throwActionFormCreateFailureException(Exception cause) {
        final ExceptionMessageBuilder br = new ExceptionMessageBuilder();
        br.addNotice("Failed to create the action form for the type.");
        br.addItem("Advice");
        br.addElement("Confirm your action form definition,");
        br.addElement("e.g. action form should be located under 'web' package");
        br.addElement("and the name should end with 'Form'.");
        if (LaActionExecuteUtil.hasActionExecute()) { // just in case
            br.addItem("Action Execute");
            br.addElement(LaActionExecuteUtil.getActionExecute());
        }
        br.addItem("Form Type");
        br.addElement(formType);
        final String msg = br.buildExceptionMessage();
        throw new ActionFormCreateFailureException(msg, cause);
    }

    // ===================================================================================
    //                                                                      Basic Override
    //                                                                      ==============
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("formMeta:{").append(formKey);
        sb.append(", ").append(listFormParameter.map(pm -> {
            return pm.getParameterizedType().getTypeName();
        }).orElse(formType.getName()));
        sb.append(", props=").append(propertyMap.size());
        return sb.toString();
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public String getFormKey() {
        return formKey;
    }

    public Class<?> getFormType() {
        return formType;
    }

    // -----------------------------------------------------
    //                                             List Form
    //                                             ---------
    public OptionalThing<Parameter> getListFormParameter() {
        return listFormParameter;
    }

    public OptionalThing<Class<?>> getListFormParameterGenericType() {
        return listFormParameter.map(pm -> {
            /* always exists, already checked in romantic action customizer */
            return DfReflectionUtil.getGenericFirstClass(pm.getParameterizedType());
        });
    }

    public OptionalThing<ParameterizedType> getListFormParameterParameterizedType() {
        return listFormParameter.map(pm -> {
            /* always parameterized, already checked in romantic action customizer */
            return (ParameterizedType) pm.getParameterizedType();
        });
    }
}
package com.workspace.core.models.impl;

import com.workspace.core.models.FormField;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

@Model(
    adaptables = Resource.class,
    adapters = FormField.class,
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class FormFieldImpl implements FormField {

    @ValueMapValue
    private String fieldLabel;

    @ValueMapValue
    private String fieldName;

    @ValueMapValue
    private String fieldType;

    @ValueMapValue
    private String placeholder;

    @ValueMapValue
    private boolean required;

    @Override
    public String getFieldLabel() {
        return fieldLabel;
    }

    @Override
    public String getFieldName() {
        return fieldName;
    }

    @Override
    public String getFieldType() {
        return fieldType;
    }

    @Override
    public String getPlaceholder() {
        return placeholder;
    }

    @Override
    public boolean isRequired() {
        return required;
    }
}
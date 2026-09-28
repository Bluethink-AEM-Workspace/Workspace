package com.workspace.core.models;

public interface FormField {
    String getFieldLabel();
    String getFieldName();
    String getFieldType();
    String getPlaceholder();
    boolean isRequired();
}
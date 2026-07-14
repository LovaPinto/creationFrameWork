package com.lovapinto;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModelAndView {

    private String viewName;
    private final Map<String, Object> model = new LinkedHashMap<>();
    private Model modelContainer;

    public ModelAndView() {
    }

    public ModelAndView(String viewName) {
        this.viewName = viewName;
    }

    public String getViewName() {
        return viewName;
    }

    public String getView() {
        return viewName;
    }

    public ModelAndView setViewName(String viewName) {
        this.viewName = viewName;
        return this;
    }

    public ModelAndView setView(String viewName) {
        this.viewName = viewName;
        return this;
    }

    public ModelAndView addObject(String name, Object value) {
        model.put(name, value);
        return this;
    }

    public ModelAndView addAllObjects(Map<String, Object> attributes) {
        model.putAll(attributes);
        return this;
    }

    public Map<String, Object> getModel() {
        return model;
    }

    public Map<String, Object> getData() {
        return model;
    }

    public Model getModelContainer() {
        return modelContainer;
    }

    public ModelAndView setModelContainer(Model modelContainer) {
        this.modelContainer = modelContainer;
        if (modelContainer != null) {
            addAllObjects(modelContainer.asMap());
        }
        return this;
    }
}

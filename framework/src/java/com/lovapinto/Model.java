package com.lovapinto;

import java.util.LinkedHashMap;
import java.util.Map;

public class Model {

    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public Model setAttribute(String name, Object value) {
        attributes.put(name, value);
        return this;
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public Map<String, Object> asMap() {
        return attributes;
    }
}

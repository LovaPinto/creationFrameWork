package Dto;

import java.util.List;

public class ApiInfo {

    private String framework;
    private String version;
    private List<String> endpoints;

    public ApiInfo(String framework, String version, List<String> endpoints) {
        this.framework = framework;
        this.version = version;
        this.endpoints = endpoints;
    }

    public String getFramework() {
        return framework;
    }

    public String getVersion() {
        return version;
    }

    public List<String> getEndpoints() {
        return endpoints;
    }
}

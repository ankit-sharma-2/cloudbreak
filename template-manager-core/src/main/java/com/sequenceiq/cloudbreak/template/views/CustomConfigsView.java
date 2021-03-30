package com.sequenceiq.cloudbreak.template.views;

import java.util.Set;

public class CustomConfigsView {

    private String name;

    private String crn;

    private String runtimeVersion;

    private Set<CustomConfigPropertyView> configurations;

    public CustomConfigsView(String name, String crn, String runtimeVersion, Set<CustomConfigPropertyView> configurations) {
        this.name = name;
        this.crn = crn;
        this.runtimeVersion = runtimeVersion;
        this.configurations = configurations;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCrn() {
        return crn;
    }

    public void setCrn(String crn) {
        this.crn = crn;
    }

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    public Set<CustomConfigPropertyView> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(Set<CustomConfigPropertyView> configurations) {
        this.configurations = configurations;
    }

}

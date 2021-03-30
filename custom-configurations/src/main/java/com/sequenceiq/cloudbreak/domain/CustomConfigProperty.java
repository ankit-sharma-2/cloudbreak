package com.sequenceiq.cloudbreak.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "customconfigs_properties")
public class CustomConfigProperty implements Serializable {

    @Id
    @SequenceGenerator(name = "custom_config_property_generator", sequenceName = "custom_config_property_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "custom_config_property_generator")
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String value;

    @Column
    private String roleType;

    @Column(nullable = false)
    private String serviceType;

    @ManyToOne
    private CustomConfigs customConfigs;

    public CustomConfigProperty(String name, String value, String roleType, String serviceType) {
        this.name = name;
        this.value = value;
        this.roleType = roleType;
        this.serviceType = serviceType;
    }

    public CustomConfigProperty() {
    }

    @JsonIgnore
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getRoleType() {
        return roleType;
    }

    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public CustomConfigs getCustomConfigs() {
        return customConfigs;
    }

    public void setCustomConfigs(CustomConfigs customConfigs) {
        this.customConfigs = customConfigs;
    }

    @Override
    public String toString() {
        return "CustomConfigProperty{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", value='" + value + '\'' +
                ", roleType='" + roleType + '\'' +
                ", serviceType='" + serviceType + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CustomConfigProperty)) {
            return false;
        }
        CustomConfigProperty property = (CustomConfigProperty) o;
        return getName().equals(property.getName()) && getValue().equals(property.getValue()) &&
                Objects.equals(getRoleType(), property.getRoleType()) && getServiceType().equals(property.getServiceType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getValue(), getRoleType(), getServiceType());
    }
}

package com.sequenceiq.cloudbreak.domain;

import java.io.Serializable;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"name", "crn"})
)
public class CustomConfigs implements Serializable {
    @Id
    @SequenceGenerator(
            name = "custom_configs_generator",
            sequenceName = "custom_configs_id_seq",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "custom_configs_generator"
    )
    @Column
    private Long id;

    @Column
    private String name;

    @Column
    private String crn;

    @OneToMany(mappedBy = "customConfigs", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<CustomConfigProperty> configurations;

    @Column
    private String runtimeVersion;

    @Column
    private String account;

    @Column
    private Long created = System.currentTimeMillis();

    public CustomConfigs(String name, String crn, Set<CustomConfigProperty> configurations, String runtimeVersion, String account, Long created) {
        this.name = name;
        this.crn = crn;
        this.configurations = configurations;
        this.runtimeVersion = runtimeVersion;
        this.account = account;
        this.created = created;
    }

    public CustomConfigs(CustomConfigs existingCustomConfigs) {
        this.name = existingCustomConfigs.getName();
        this.configurations = Set.copyOf(existingCustomConfigs.getConfigurations());
        this.runtimeVersion = existingCustomConfigs.getRuntimeVersion();
    }

    public CustomConfigs() {
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

    public String getCrn() {
        return crn;
    }

    public void setCrn(String crn) {
        this.crn = crn;
    }

    public Set<CustomConfigProperty> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(Set<CustomConfigProperty> configurations) {
        this.configurations = configurations;
    }

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    @Override
    public String toString() {
        return "CustomConfigs{" +
                "name='" + name + '\'' +
                ", crn='" + crn + '\'' +
                ", configurations='" + configurations + '\'' +
                ", runtimeVersion='" + runtimeVersion + '\'' +
                ", created=" + created +
                '}';
    }
}

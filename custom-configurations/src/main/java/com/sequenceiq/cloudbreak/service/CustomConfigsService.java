package com.sequenceiq.cloudbreak.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cloudera.api.swagger.model.ApiClusterTemplateConfig;
import com.cloudera.api.swagger.model.ApiClusterTemplateRoleConfigGroup;
import com.sequenceiq.authorization.resource.AuthorizationResourceType;
import com.sequenceiq.authorization.service.ResourcePropertyProvider;
import com.sequenceiq.cloudbreak.api.endpoint.v4.dto.NameOrCrn;
import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.auth.crn.Crn;
import com.sequenceiq.cloudbreak.auth.crn.CrnResourceDescriptor;
import com.sequenceiq.cloudbreak.auth.crn.RegionAwareCrnGenerator;
import com.sequenceiq.cloudbreak.common.exception.NotFoundException;
import com.sequenceiq.cloudbreak.domain.CustomConfigProperty;
import com.sequenceiq.cloudbreak.domain.CustomConfigs;
import com.sequenceiq.cloudbreak.exception.CustomConfigsException;
import com.sequenceiq.cloudbreak.repository.CustomConfigsRepository;

@Service
public class CustomConfigsService implements ResourcePropertyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomConfigsService.class);

    @Inject
    private CustomConfigsRepository customConfigsRepository;

    @Inject
    private CustomConfigsValidator validator;

    @Inject
    private RegionAwareCrnGenerator regionAwareCrnGenerator;

    public void initializeCrnForCustomConfigs(CustomConfigs customConfigs, String accountId) {
        customConfigs.setCrn(regionAwareCrnGenerator.generateCrnStringWithUuid(CrnResourceDescriptor.CUSTOM_CONFIGURATIONS, accountId));
    }

    public List<CustomConfigs> getAll(String accountId) {
        return customConfigsRepository.findCustomConfigsByAccountId(accountId);
    }

    public CustomConfigs getByNameOrCrn(NameOrCrn nameOrCrn) {
        return nameOrCrn.hasName() ? getByName(nameOrCrn.getName(), ThreadBasedUserCrnProvider.getAccountId()) : getByCrn(nameOrCrn.getCrn());
    }

    private CustomConfigs getByCrn(String crn) {
        return customConfigsRepository.findByCrn(crn);
    }

    private List<CustomConfigProperty> getCustomServiceConfigs(Set<CustomConfigProperty> configs) {
        return configs.stream()
                .filter(config -> config.getRoleType() == null)
                .collect(Collectors.toList());
    }

    private List<CustomConfigProperty> getCustomRoleConfigs(Set<CustomConfigProperty> configs) {
        return configs.stream()
                .filter(config -> config.getRoleType() != null)
                .collect(Collectors.toList());
    }

    private void validate(CustomConfigs customConfigs) {
        validator.validateIfAccountIsEntitled(ThreadBasedUserCrnProvider.getAccountId());
        validator.validateServiceNames(customConfigs);
    }

    public Map<String, List<ApiClusterTemplateConfig>> getCustomServiceConfigsMap(Set<CustomConfigProperty> configProperties) {
        Map<String, List<ApiClusterTemplateConfig>> serviceConfigsMap = new HashMap<>();
        List<CustomConfigProperty> serviceConfigs = getCustomServiceConfigs(configProperties);
        serviceConfigs.forEach(serviceConfig -> {
            serviceConfigsMap.computeIfAbsent(serviceConfig.getServiceType(), k -> new ArrayList<>());
            serviceConfigsMap.get(serviceConfig.getServiceType()).add(new ApiClusterTemplateConfig()
                    .name(serviceConfig.getName()).value(serviceConfig.getValue()));
        });
        return serviceConfigsMap;
    }

    public Map<String, List<ApiClusterTemplateRoleConfigGroup>> getCustomRoleConfigsMap(Set<CustomConfigProperty> configProperties) {
        Map<String, List<ApiClusterTemplateRoleConfigGroup>> roleConfigsMap = new HashMap<>();
        List<CustomConfigProperty> roleConfigGroups = getCustomRoleConfigs(configProperties);
        roleConfigGroups.forEach(roleConfigProperty -> {
            String name = roleConfigProperty.getName();
            String value = roleConfigProperty.getValue();
            String service = roleConfigProperty.getServiceType();
            String role = roleConfigProperty.getRoleType();
            roleConfigsMap.computeIfAbsent(service, k -> new ArrayList<>());
            Optional<ApiClusterTemplateRoleConfigGroup> roleConfigGroup = filterRCGsByRoleType(roleConfigsMap.get(service), role);
            if (roleConfigGroup.isPresent()) {
                roleConfigGroup.get().getConfigs().add(new ApiClusterTemplateConfig().name(name).value(value));
            } else {
                ApiClusterTemplateRoleConfigGroup roleToAdd =
                        new ApiClusterTemplateRoleConfigGroup().roleType(role)
                                .addConfigsItem(new ApiClusterTemplateConfig().name(name).value(value));
                roleConfigsMap.get(service).add(roleToAdd);
            }
        });
        return roleConfigsMap;
    }

    private Optional<ApiClusterTemplateRoleConfigGroup> filterRCGsByRoleType(List<ApiClusterTemplateRoleConfigGroup> roleConfigs, String roleType) {
        return roleConfigs.stream().filter(rcg -> roleType.equalsIgnoreCase(rcg.getRoleType())).findFirst();
    }

    private CustomConfigs getByName(String name, String accountId) {
        return customConfigsRepository.findByNameAndAccountId(name, accountId)
                .orElseThrow(NotFoundException.notFound("Custom Configurations", name));
    }

    public CustomConfigs create(CustomConfigs customConfigs, String accountId) {
        validate(customConfigs);
        customConfigsRepository
                .findByNameAndAccountId(customConfigs.getName(), accountId)
                .ifPresent(retrievedCustomConfigs -> {
                    throw new CustomConfigsException("Custom Configurations with name " + retrievedCustomConfigs.getName()
                        + " exists. Provide a different name"); });
        initializeCrnForCustomConfigs(customConfigs, accountId);
        customConfigs.setAccount(accountId);
        customConfigs.getConfigurations().forEach(config -> config.setCustomConfigs(customConfigs));
        customConfigsRepository.save(customConfigs);
        return customConfigs;
    }

    public CustomConfigs clone(NameOrCrn nameOrCrn, String newName, String newVersion, String accountId) {
        return nameOrCrn.hasName()
                ? cloneByName(nameOrCrn.getName(), newName, newVersion, accountId)
                : cloneByCrn(nameOrCrn.getCrn(), newName, newVersion, accountId);
    }

    public CustomConfigs cloneByName(String name, String newName, String newRuntimeVersion, String accountId) {
        CustomConfigs customConfigsByName = getByName(name, accountId);
        CustomConfigs newCustomConfigs = cloneCustomConfigs(customConfigsByName, newName, newRuntimeVersion);
        return create(newCustomConfigs, accountId);
    }

    private CustomConfigs cloneCustomConfigs(CustomConfigs existingCustomConfigs, String newName, String newRuntimeVersion) {
        CustomConfigs clone = new CustomConfigs(existingCustomConfigs);
        Set<CustomConfigProperty> newConfigSet = existingCustomConfigs.getConfigurations()
                .stream()
                .map(config -> new CustomConfigProperty(
                        config.getName(),
                        config.getValue(),
                        config.getRoleType(),
                        config.getServiceType()))
                .collect(Collectors.toSet());
        clone.setConfigurations(newConfigSet);
        clone.setName(newName);
        clone.setRuntimeVersion(newRuntimeVersion);
        return clone;
    }

    public CustomConfigs cloneByCrn(String crn, String newName, String newRuntimeVersion, String accountId) {
        CustomConfigs customConfigsByCrn = getByCrn(crn);
        CustomConfigs newCustomConfigs = cloneCustomConfigs(customConfigsByCrn, newName, newRuntimeVersion);
        return create(newCustomConfigs, accountId);
    }

    public CustomConfigs deleteByCrn(String crn) {
        CustomConfigs customConfigsByCrn = getByCrn(crn);
        customConfigsRepository.deleteById(customConfigsByCrn.getId());
        return customConfigsByCrn;
    }

    public CustomConfigs deleteByName(String name, String accountId) {
        CustomConfigs customConfigsByName = getByName(name, accountId);
        customConfigsRepository.deleteById(customConfigsByName.getId());
        return customConfigsByName;
    }

    @Override
    public List<String> getResourceCrnListByResourceNameList(List<String> resourceNames) {
        return customConfigsRepository.findResourceCrnsByNamesAndAccountId(ThreadBasedUserCrnProvider.getAccountId(), resourceNames);
    }

    @Override
    public Optional<AuthorizationResourceType> getSupportedAuthorizationResourceType() {
        return Optional.of(AuthorizationResourceType.CUSTOM_CONFIGURATIONS);
    }

    @Override
    public EnumSet<Crn.ResourceType> getSupportedCrnResourceTypes() {
        return EnumSet.of(Crn.ResourceType.CUSTOM_CONFIGURATIONS);
    }

    @Override
    public Map<String, Optional<String>> getNamesByCrns(Collection<String> crns) {
        return customConfigsRepository.findResourceNamesByCrnsAndAccountId(ThreadBasedUserCrnProvider.getAccountId(), crns)
                .stream()
                .collect(Collectors.toMap(k -> k.getCrn(), v -> Optional.of(v.getName())));
    }

    @Override
    public String getResourceCrnByResourceName(String resourceName) {
        return customConfigsRepository.findResourceCrnByNameAndAccountId(resourceName, ThreadBasedUserCrnProvider.getAccountId())
                .orElseThrow(NotFoundException.notFound("Custom Configurations", resourceName));
    }
}

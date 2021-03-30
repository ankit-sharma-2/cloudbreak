package com.sequenceiq.cloudbreak.service;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.sequenceiq.cloudbreak.auth.altus.EntitlementService;
import com.sequenceiq.cloudbreak.common.exception.BadRequestException;
import com.sequenceiq.cloudbreak.domain.CustomConfigs;
import com.sequenceiq.cloudbreak.exception.ServiceTypeNotFoundException;

@Component
public class CustomConfigsValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomConfigsValidator.class);

    @Inject
    private EntitlementService entitlementService;

    public void validateServiceNames(CustomConfigs customConfigs) {
        LOGGER.info("Validating service names for Custom Configs " + customConfigs.getName());
        customConfigs.getConfigurations().forEach(config -> {
            try {
                AllServiceTypes.valueOf(config.getServiceType().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ServiceTypeNotFoundException("Service name with " + config.getServiceType() + " does not exist.");
            }
        });
    }

    public void validateIfAccountIsEntitled(String accountId) {
        if (!entitlementService.datahubCustomConfigsEnabled(accountId)) {
            throw new BadRequestException("Custom configs not enabled for account");
        }
    }
}


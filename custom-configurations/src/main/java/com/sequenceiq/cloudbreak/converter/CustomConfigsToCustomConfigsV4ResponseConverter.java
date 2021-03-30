package com.sequenceiq.cloudbreak.converter;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.api.endpoint.v4.responses.CustomConfigsV4Response;
import com.sequenceiq.cloudbreak.domain.CustomConfigs;

@Component
public class CustomConfigsToCustomConfigsV4ResponseConverter extends AbstractConversionServiceAwareConverter<CustomConfigs, CustomConfigsV4Response> {

    @Override
    public CustomConfigsV4Response convert(CustomConfigs source) {
        CustomConfigsV4Response response = new CustomConfigsV4Response();
        response.setName(source.getName());
        response.setCreated(source.getCreated());
        response.setCrn(source.getCrn());
        response.setConfigurations(source.getConfigurations().stream().map(CustomConfigPropertyConverter::convertTo).collect(Collectors.toSet()));
        response.setAccount(source.getAccount());
        response.setRuntimeVersion(source.getRuntimeVersion());
        return response;
    }

}

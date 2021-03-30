package com.sequenceiq.cloudbreak.converter;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.api.endpoint.v4.requests.CustomConfigsV4Request;
import com.sequenceiq.cloudbreak.domain.CustomConfigs;

@Component
public class CustomConfigsV4RequestToCustomConfigsConverter extends AbstractConversionServiceAwareConverter<CustomConfigsV4Request, CustomConfigs> {

    @Override
    public CustomConfigs convert(CustomConfigsV4Request source) {
        CustomConfigs customConfigs = new CustomConfigs();
        customConfigs.setName(source.getName());
        customConfigs.setConfigurations(source.getConfigurations().stream().map(CustomConfigPropertyConverter::convertFrom)
                .collect(Collectors.toSet()));
        customConfigs.setRuntimeVersion(source.getRuntimeVersion());
        return customConfigs;
    }
}

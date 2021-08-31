package com.sequenceiq.cloudbreak.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sequenceiq.cloudbreak.api.endpoint.v4.responses.CustomConfigsV4Response;
import com.sequenceiq.cloudbreak.api.model.CustomConfigPropertyParameters;
import com.sequenceiq.cloudbreak.domain.CustomConfigProperty;
import com.sequenceiq.cloudbreak.domain.CustomConfigs;

class CustomConfigsToCustomConfigsV4ResponseConverterTest {

    private CustomConfigsToCustomConfigsV4ResponseConverter underTest;

    private CustomConfigs customConfigs = new CustomConfigs("test",
            "crn:cdp:resource:us-west-1:tenant:customconfigs:c7da2918-dd14-49ed-9b43-33ff55bd6309",
            Set.of(new CustomConfigProperty("property1", "value1", "role1", "service1")),
            "7.2.8",
            "accid",
            System.currentTimeMillis()
            );

    @BeforeEach
    void setUp() {
        underTest = new CustomConfigsToCustomConfigsV4ResponseConverter();
    }

    @Test
    void testConvert() {
        customConfigs.setAccount("testAccount");
        CustomConfigsV4Response response = underTest.convert(customConfigs);
        assertEquals(customConfigs.getName(), response.getName());
        assertEquals(customConfigs.getConfigurations(), response.getConfigurations()
                .stream()
                .map(CustomConfigPropertyConverter::convertFrom)
                .collect(Collectors.toSet()));
        assertEquals(customConfigs.getRuntimeVersion(), response.getRuntimeVersion());
        assertEquals(customConfigs.getCrn(), response.getCrn());
        assertEquals(customConfigs.getAccount(), response.getAccount());
        assertEquals(customConfigs.getCreated(), response.getCreated());
    }

    @Test
    void testConvertThrowsExceptionIfConfigsAreNull() {
        customConfigs.setConfigurations(null);
        assertThrows(NullPointerException.class, () -> underTest.convert(customConfigs));
    }

    @Test
    void testConvertResponseContainsCorrectCustomConfigsProperties() {
        Set<CustomConfigPropertyParameters> properties = customConfigs.getConfigurations().stream()
                .map(CustomConfigPropertyConverter::convertTo).collect(Collectors.toSet());
        CustomConfigsV4Response response = underTest.convert(customConfigs);
        Set<CustomConfigPropertyParameters> result = response.getConfigurations();
        assertEquals(properties, result);
    }
}
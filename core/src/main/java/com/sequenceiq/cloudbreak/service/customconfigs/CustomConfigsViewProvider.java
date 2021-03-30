package com.sequenceiq.cloudbreak.service.customconfigs;

import com.sequenceiq.cloudbreak.domain.CustomConfigProperty;
import com.sequenceiq.cloudbreak.domain.CustomConfigs;
import com.sequenceiq.cloudbreak.template.views.CustomConfigPropertyView;
import com.sequenceiq.cloudbreak.template.views.CustomConfigsView;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CustomConfigsViewProvider {

    public CustomConfigsView getCustomConfigsView(@Nonnull CustomConfigs customConfigs) {
        Set<CustomConfigPropertyView> configsView = customConfigs.getConfigurations()
                .stream()
                .map(this::getCustomConfigPropertyView)
                .collect(Collectors.toSet());
        return new CustomConfigsView(customConfigs.getName(), customConfigs.getCrn(),
                customConfigs.getRuntimeVersion(), configsView);
    }

    public CustomConfigPropertyView getCustomConfigPropertyView(@Nonnull CustomConfigProperty customConfigProperty) {
        return new CustomConfigPropertyView(customConfigProperty.getName(), customConfigProperty.getValue(),
                customConfigProperty.getRoleType(), customConfigProperty.getServiceType());
    }
}

package com.sequenceiq.cloudbreak.cmtemplate;

import com.sequenceiq.cloudbreak.auth.altus.model.Entitlement;
import com.sequenceiq.cloudbreak.cmtemplate.configproviders.cruisecontrol.CruiseControlRoles;
import com.sequenceiq.cloudbreak.common.type.Versioned;

import java.util.Objects;
import java.util.Optional;

public enum BlackListedDownScaleRole implements EntitledForServiceScale {
    KAFKA_BROKER(Entitlement.DATAHUB_STREAMING_SCALING, "7.2.12", CruiseControlRoles.CRUISECONTROL),
    NIFI_NODE(Entitlement.DATAHUB_FLOW_SCALING),
    ZEPPELIN_SERVER(Entitlement.DATAHUB_DEFAULT_SCALING),
    NAMENODE(Entitlement.DATAHUB_DEFAULT_SCALING);

    private final Entitlement entitledFor;

    private final Optional<String> blockedUntilCDPVersion;

    private final Optional<String> requiredService;

    BlackListedDownScaleRole(Entitlement entitledFor) {
        this(entitledFor, null);
    }

    BlackListedDownScaleRole(Entitlement entitledFor, String blockedUntilCDPVersion) {
        this(entitledFor, blockedUntilCDPVersion, null);
    }

    BlackListedDownScaleRole(Entitlement entitledFor, String blockedUntilCDPVersion, String requiredService) {
        this.entitledFor = Objects.requireNonNull(entitledFor);
        this.blockedUntilCDPVersion = Optional.ofNullable(blockedUntilCDPVersion);
        this.requiredService = Optional.ofNullable(requiredService);
    }

    @Override
    public Entitlement getEntitledFor() {
        return entitledFor;
    }

    @Override
    public Optional<String> getBlockedUntilCDPVersion() {
        return blockedUntilCDPVersion;
    }

    @Override
    public Optional<String> getRequiredService() {
        return requiredService;
    }

    public Versioned getBlockedUntilCDPVersionAsVersion() {
        return () -> blockedUntilCDPVersion.orElse(null);
    }

}

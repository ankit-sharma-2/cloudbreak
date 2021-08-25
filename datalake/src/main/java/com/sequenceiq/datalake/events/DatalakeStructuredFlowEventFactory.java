package com.sequenceiq.datalake.events;

import static com.sequenceiq.cloudbreak.structuredevent.event.StructuredEventType.FLOW;

import javax.inject.Inject;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.common.service.Clock;
import com.sequenceiq.cloudbreak.event.ResourceEvent;
import com.sequenceiq.cloudbreak.structuredevent.event.CloudbreakEventService;
import com.sequenceiq.cloudbreak.structuredevent.event.FlowDetails;
import com.sequenceiq.cloudbreak.structuredevent.event.cdp.CDPOperationDetails;
import com.sequenceiq.cloudbreak.structuredevent.event.cdp.CDPStructuredFlowEvent;
import com.sequenceiq.cloudbreak.structuredevent.service.CDPStructuredFlowEventFactory;
import com.sequenceiq.datalake.entity.SdxCluster;
import com.sequenceiq.datalake.service.sdx.SdxService;
import com.sequenceiq.flow.ha.NodeConfig;

@Component
public class DatalakeStructuredFlowEventFactory implements CDPStructuredFlowEventFactory {

    @Inject
    private Clock clock;

    @Inject
    private NodeConfig nodeConfig;

    @Inject
    private SdxService sdxService;

    @Inject
    private SdxClusterDtoConverter sdxClusterDtoConverter;

    @Value("${info.app.version:}")
    private String serviceVersion;

    @Override
    public CDPStructuredFlowEvent<SdxClusterDto> createStructuredFlowEvent(Long resourceId, FlowDetails flowDetails, Boolean detailed) {
        return createStructuredFlowEvent(resourceId, flowDetails, detailed, null);
    }

    @Override
    public CDPStructuredFlowEvent<SdxClusterDto> createStructuredFlowEvent(Long resourceId, FlowDetails flowDetails, Boolean detailed, Exception exception) {
        SdxCluster sdxCluster = sdxService.getById(resourceId);
        String resourceType = CloudbreakEventService.DATALAKE_RESOURCE_TYPE; // turning this value into an enum will make the builder more useful

        // todo: make a CDPOperationDetails Builder
        CDPOperationDetails operationDetails = new CDPOperationDetails();
        operationDetails.setTimestamp(clock.getCurrentTimeMillis());
        operationDetails.setEventType(FLOW);
        operationDetails.setResourceId(resourceId);
        operationDetails.setResourceName(sdxCluster.getName());
        operationDetails.setResourceType(resourceType);
        operationDetails.setCloudbreakId(nodeConfig.getId());
        operationDetails.setCloudbreakVersion(serviceVersion);
        operationDetails.setResourceCrn(sdxCluster.getResourceCrn());
        operationDetails.setUserCrn(ThreadBasedUserCrnProvider.getUserCrn());
        operationDetails.setAccountId(sdxCluster.getAccountId());
        operationDetails.setEnvironmentCrn(sdxCluster.getEnvCrn());
        operationDetails.setResourceEvent(ResourceEvent.DATALAKE_DATABASE_BACKUP.name());

        SdxClusterDto sdxClusterDto = sdxClusterDtoConverter.sdxClusterToDto(sdxCluster);

        // todo: look up the correct place to provide "cluster status" and "reason for cluster status"
        // todo: the environment CRN isn't correct here
        CDPStructuredFlowEvent<SdxClusterDto> event = new CDPStructuredFlowEvent<>(operationDetails, flowDetails, sdxClusterDto, "cluster status", "reason for cluster status");
        if (exception != null) {
            event.setException(ExceptionUtils.getStackTrace(exception));
        }
        return event;
    }
}

package com.sequenceiq.datalake.events;

import static com.sequenceiq.cloudbreak.structuredevent.event.StructuredEventType.FLOW;

import javax.inject.Inject;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.common.service.Clock;
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
        String resourceType = CloudbreakEventService.DATALAKE_RESOURCE_TYPE;

        // todo: make a CDPOperationDetails Builder
        CDPOperationDetails operationDetails = new CDPOperationDetails(clock.getCurrentTimeMillis(), FLOW, resourceType, sdxCluster.getId(),
                sdxCluster.getName(), nodeConfig.getId(), serviceVersion, sdxCluster.getAccountId(), sdxCluster.getResourceCrn(), ThreadBasedUserCrnProvider.getUserCrn(),
                sdxCluster.getResourceCrn(), null);

        SdxClusterDto sdxClusterDto = sdxClusterDtoConverter.sdxClusterToDto(sdxCluster);

        // todo: look up the correct place to provide "cluster status" and "reason for cluster status"
        CDPStructuredFlowEvent<SdxClusterDto> event = new CDPStructuredFlowEvent<>(operationDetails, flowDetails, sdxClusterDto, "cluster status", "reason for cluster status");
        if (exception != null) {
            event.setException(ExceptionUtils.getStackTrace(exception));
        }
        return event;
    }
}

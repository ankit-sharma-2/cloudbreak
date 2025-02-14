package com.sequenceiq.cloudbreak.core.flow2.stack.instance.termination;

import static com.sequenceiq.cloudbreak.cloud.model.AvailabilityZone.availabilityZone;
import static com.sequenceiq.cloudbreak.cloud.model.Location.location;
import static com.sequenceiq.cloudbreak.cloud.model.Region.region;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.springframework.statemachine.StateContext;

import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.event.InstancePayload;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.CloudInstance;
import com.sequenceiq.cloudbreak.cloud.model.CloudResource;
import com.sequenceiq.cloudbreak.cloud.model.CloudStack;
import com.sequenceiq.cloudbreak.cloud.model.Location;
import com.sequenceiq.cloudbreak.converter.spi.InstanceMetaDataToCloudInstanceConverter;
import com.sequenceiq.cloudbreak.converter.spi.ResourceToCloudResourceConverter;
import com.sequenceiq.cloudbreak.converter.spi.StackToCloudStackConverter;
import com.sequenceiq.cloudbreak.core.flow2.AbstractStackAction;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.common.exception.NotFoundException;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.cloudbreak.reactor.api.event.StackFailureEvent;
import com.sequenceiq.cloudbreak.service.resource.ResourceService;
import com.sequenceiq.cloudbreak.service.stack.InstanceMetaDataService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.cloudbreak.util.StackUtil;
import com.sequenceiq.flow.core.FlowParameters;

abstract class AbstractInstanceTerminationAction<P extends InstancePayload>
        extends AbstractStackAction<InstanceTerminationState, InstanceTerminationEvent, InstanceTerminationContext, P> {

    @Inject
    private StackService stackService;

    @Inject
    private InstanceMetaDataService instanceMetaDataService;

    @Inject
    private StackToCloudStackConverter cloudStackConverter;

    @Inject
    private ResourceToCloudResourceConverter cloudResourceConverter;

    @Inject
    private InstanceMetaDataToCloudInstanceConverter metadataConverter;

    @Inject
    private StackUtil stackUtil;

    @Inject
    private ResourceService resourceService;

    protected AbstractInstanceTerminationAction(Class<P> payloadClass) {
        super(payloadClass);
    }

    @Override
    protected InstanceTerminationContext createFlowContext(FlowParameters flowParameters,
            StateContext<InstanceTerminationState, InstanceTerminationEvent> stateContext, P payload) {
        Stack stack = stackService.getByIdWithListsInTransaction(payload.getResourceId());
        stack.setResources(new HashSet<>(resourceService.getAllByStackId(payload.getResourceId())));
        MDCBuilder.buildMdcContext(stack);
        Location location = location(region(stack.getRegion()), availabilityZone(stack.getAvailabilityZone()));
        CloudContext cloudContext = CloudContext.Builder.builder()
                .withId(stack.getId())
                .withName(stack.getName())
                .withCrn(stack.getResourceCrn())
                .withPlatform(stack.getCloudPlatform())
                .withVariant(stack.getPlatformVariant())
                .withLocation(location)
                .withWorkspaceId(stack.getWorkspace().getId())
                .withAccountId(stack.getTenant().getId())
                .build();
        CloudCredential cloudCredential = stackUtil.getCloudCredential(stack);
        Set<String> instanceIds = payload.getInstanceIds();
        CloudStack cloudStack = cloudStackConverter.convert(stack, instanceIds);
        List<CloudResource> cloudResources = cloudResourceConverter.convert(stack.getResources());
        List<InstanceMetaData> instanceMetaDataList = new ArrayList<>();
        List<CloudInstance> cloudInstances = new ArrayList<>();
        for (String instanceId : instanceIds) {
            InstanceMetaData instanceMetaData = instanceMetaDataService.findByStackIdAndInstanceId(stack.getId(), instanceId)
                    .orElseThrow(NotFoundException.notFound("instanceMetadata", instanceId));
            CloudInstance cloudInstance = metadataConverter.convert(instanceMetaData, stack.getEnvironmentCrn(), stack.getStackAuthentication());
            instanceMetaDataList.add(instanceMetaData);
            cloudInstances.add(cloudInstance);
        }
        return new InstanceTerminationContext(flowParameters, stack, cloudContext, cloudCredential, cloudStack, cloudResources, cloudInstances,
                instanceMetaDataList);
    }

    @Override
    protected Object getFailurePayload(P payload, Optional<InstanceTerminationContext> flowContext, Exception ex) {
        return new StackFailureEvent(payload.getResourceId(), ex);
    }

}

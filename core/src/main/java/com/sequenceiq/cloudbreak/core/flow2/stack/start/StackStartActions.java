package com.sequenceiq.cloudbreak.core.flow2.stack.start;

import static com.sequenceiq.cloudbreak.cloud.model.AvailabilityZone.availabilityZone;
import static com.sequenceiq.cloudbreak.cloud.model.Location.location;
import static com.sequenceiq.cloudbreak.cloud.model.Region.region;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

import com.sequenceiq.cloudbreak.api.util.ConverterUtil;
import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.event.instance.CollectMetadataRequest;
import com.sequenceiq.cloudbreak.cloud.event.instance.CollectMetadataResult;
import com.sequenceiq.cloudbreak.cloud.event.instance.StartInstancesRequest;
import com.sequenceiq.cloudbreak.cloud.event.instance.StartInstancesResult;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.CloudInstance;
import com.sequenceiq.cloudbreak.cloud.model.CloudResource;
import com.sequenceiq.cloudbreak.cloud.model.Location;
import com.sequenceiq.cloudbreak.common.event.Payload;
import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.cloudbreak.converter.spi.InstanceMetaDataToCloudInstanceConverter;
import com.sequenceiq.cloudbreak.converter.spi.StackToCloudStackConverter;
import com.sequenceiq.cloudbreak.core.flow2.AbstractStackAction;
import com.sequenceiq.cloudbreak.core.flow2.stack.AbstractStackFailureAction;
import com.sequenceiq.cloudbreak.core.flow2.stack.StackFailureContext;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.cloudbreak.reactor.api.event.StackEvent;
import com.sequenceiq.cloudbreak.reactor.api.event.StackFailureEvent;
import com.sequenceiq.cloudbreak.service.metrics.MetricType;
import com.sequenceiq.cloudbreak.service.stack.InstanceMetaDataService;
import com.sequenceiq.cloudbreak.service.resource.ResourceService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.cloudbreak.util.StackUtil;
import com.sequenceiq.flow.core.FlowParameters;

@Configuration
public class StackStartActions {
    private static final Logger LOGGER = LoggerFactory.getLogger(StackStartActions.class);

    @Inject
    private StackToCloudStackConverter cloudStackConverter;

    @Inject
    private StackStartStopService stackStartStopService;

    @Inject
    private InstanceMetaDataToCloudInstanceConverter instanceMetaDataToCloudInstanceConverter;

    @Inject
    private ConverterUtil converterUtil;

    @Bean(name = "START_STATE")
    public Action<?, ?> stackStartAction() {
        return new AbstractStackStartAction<>(StackEvent.class) {
            @Override
            protected void doExecute(StackStartStopContext context, StackEvent payload, Map<Object, Object> variables) {
                stackStartStopService.startStackStart(context);
                sendEvent(context);
            }

            @Override
            protected Selectable createRequest(StackStartStopContext context) {
                Stack stack = context.getStack();
                LOGGER.debug("Assembling start request for stack: {}", stack);
                List<CloudInstance> cloudInstances = instanceMetaDataToCloudInstanceConverter.convert(stack.getNotDeletedInstanceMetaDataList(),
                        stack.getEnvironmentCrn(), stack.getStackAuthentication());
                List<CloudResource> resources = converterUtil.convertAll(stack.getResources(), CloudResource.class);
                cloudInstances.forEach(instance -> context.getStack().getParameters().forEach(instance::putParameter));
                return new StartInstancesRequest(context.getCloudContext(), context.getCloudCredential(), resources, cloudInstances);
            }
        };
    }

    @Bean(name = "COLLECTING_METADATA")
    public Action<?, ?> collectingMetadataAction() {
        return new AbstractStackStartAction<>(StartInstancesResult.class) {
            @Override
            protected void doExecute(StackStartStopContext context, StartInstancesResult payload, Map<Object, Object> variables) {
                stackStartStopService.validateStackStartResult(context, payload);
                sendEvent(context);
            }

            @Override
            protected Selectable createRequest(StackStartStopContext context) {
                List<CloudInstance> cloudInstances = cloudStackConverter.buildInstances(context.getStack());
                List<CloudResource> cloudResources = converterUtil.convertAll(context.getStack().getResources(), CloudResource.class);
                return new CollectMetadataRequest(context.getCloudContext(), context.getCloudCredential(), cloudResources, cloudInstances, cloudInstances);
            }
        };
    }

    @Bean(name = "START_FINISHED_STATE")
    public Action<?, ?> startFinishedAction() {
        return new AbstractStackStartAction<>(CollectMetadataResult.class) {
            @Override
            protected void doExecute(StackStartStopContext context, CollectMetadataResult payload, Map<Object, Object> variables) {
                stackStartStopService.finishStackStart(context, payload.getResults());
                getMetricService().incrementMetricCounter(MetricType.STACK_START_SUCCESSFUL, context.getStack());
                sendEvent(context);
            }

            @Override
            protected Selectable createRequest(StackStartStopContext context) {
                return new StackEvent(StackStartEvent.START_FINALIZED_EVENT.event(), context.getStack().getId());
            }
        };
    }

    @Bean(name = "START_FAILED_STATE")
    public Action<?, ?> stackStartFailedAction() {
        return new AbstractStackFailureAction<StackStartState, StackStartEvent>() {
            @Override
            protected void doExecute(StackFailureContext context, StackFailureEvent payload, Map<Object, Object> variables) {
                stackStartStopService.handleStackStartError(context.getStackView(), payload);
                getMetricService().incrementMetricCounter(MetricType.STACK_START_FAILED, context.getStackView(), payload.getException());
                sendEvent(context);
            }

            @Override
            protected Selectable createRequest(StackFailureContext context) {
                return new StackEvent(StackStartEvent.START_FAIL_HANDLED_EVENT.event(), context.getStackView().getId());
            }
        };
    }

    private abstract static class AbstractStackStartAction<P extends Payload>
            extends AbstractStackAction<StackStartState, StackStartEvent, StackStartStopContext, P> {
        private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStackStartAction.class);

        @Inject
        private StackService stackService;

        @Inject
        private InstanceMetaDataService instanceMetaDataService;

        @Inject
        private StackUtil stackUtil;

        @Inject
        private ResourceService resourceService;

        protected AbstractStackStartAction(Class<P> payloadClass) {
            super(payloadClass);
        }

        @Override
        protected StackStartStopContext createFlowContext(FlowParameters flowParameters, StateContext<StackStartState, StackStartEvent> stateContext,
                P payload) {
            Long stackId = payload.getResourceId();
            Stack stack = stackService.getByIdWithListsInTransaction(stackId);
            stack.setResources(new HashSet<>(resourceService.getAllByStackId(payload.getResourceId())));
            MDCBuilder.buildMdcContext(stack);
            List<InstanceMetaData> instances = new ArrayList<>(instanceMetaDataService.findNotTerminatedForStack(stackId));
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
            return new StackStartStopContext(flowParameters, stack, instances, cloudContext, cloudCredential);
        }

        @Override
        protected Object getFailurePayload(P payload, Optional<StackStartStopContext> flowContext, Exception ex) {
            return new StackFailureEvent(payload.getResourceId(), ex);
        }
    }
}

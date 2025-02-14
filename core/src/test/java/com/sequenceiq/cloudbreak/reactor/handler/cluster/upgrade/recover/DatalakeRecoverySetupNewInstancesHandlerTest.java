package com.sequenceiq.cloudbreak.reactor.handler.cluster.upgrade.recover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status;
import com.sequenceiq.cloudbreak.cloud.model.CloudInstance;
import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.cloudbreak.common.exception.NotFoundException;
import com.sequenceiq.cloudbreak.controller.StackCreatorService;
import com.sequenceiq.cloudbreak.core.flow2.stack.upscale.StackUpscaleService;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceGroup;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.upgrade.recovery.bringup.DatalakeRecoverySetupNewInstancesFailedEvent;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.upgrade.recovery.bringup.DatalakeRecoverySetupNewInstancesRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.upgrade.recovery.bringup.DatalakeRecoverySetupNewInstancesSuccess;
import com.sequenceiq.cloudbreak.service.cluster.ClusterService;
import com.sequenceiq.cloudbreak.service.stack.InstanceMetaDataService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.flow.event.EventSelectorUtil;
import com.sequenceiq.flow.reactor.api.handler.HandlerEvent;

@ExtendWith(MockitoExtension.class)
class DatalakeRecoverySetupNewInstancesHandlerTest {

    private static final String EXCEPTION_MESSAGE = "Not found";

    private static final long STACK_ID = 1L;

    @InjectMocks
    private DatalakeRecoverySetupNewInstancesHandler underTest;

    @Mock
    private StackService stackService;

    @Mock
    private StackCreatorService stackCreatorService;

    @Mock
    private StackUpscaleService stackUpscaleService;

    @Mock
    private InstanceMetaDataService instanceMetaDataService;

    @Mock
    private ClusterService clusterService;

    @Mock
    private Stack stack;

    @Test
    void testDoAcceptWhenSuccess() {
        List<CloudInstance> cloudInstances = new ArrayList<>();
        cloudInstances.add(setupCloudInstance());
        cloudInstances.add(setupCloudInstance());
        InstanceGroup instanceGroup1 = setupInstanceGroup(1L);
        InstanceGroup instanceGroup2 = setupInstanceGroup(2L);
        List<InstanceGroup> instanceGroups = List.of(instanceGroup1, instanceGroup2);

        when(stackService.getByIdWithClusterInTransaction(STACK_ID)).thenReturn(stack);
        when(stackCreatorService.sortInstanceGroups(stack)).thenReturn(instanceGroups);
        when(stackUpscaleService.buildNewInstances(eq(stack), any(), eq(0))).thenReturn(cloudInstances);

        Selectable nextFlowStepSelector = underTest.doAccept(getHandlerEvent());

        assertEquals(EventSelectorUtil.selector(DatalakeRecoverySetupNewInstancesSuccess.class), nextFlowStepSelector.selector());
        verify(stackService).getByIdWithClusterInTransaction(STACK_ID);
        verify(clusterService).updateClusterStatusByStackId(STACK_ID, Status.REQUESTED);
        verify(stackCreatorService).sortInstanceGroups(stack);
        verify(stackUpscaleService, times(2)).buildNewInstances(eq(stack), any(), eq(0));
        verify(instanceMetaDataService, times(2)).saveInstanceAndGetUpdatedStack(eq(stack), any(), eq(true), eq(Collections.emptySet()), eq(false));
    }

    @Test
    void testDoAcceptWhenExceptionThenFailure() {

        when(stackService.getByIdWithClusterInTransaction(STACK_ID)).thenThrow(new NotFoundException(EXCEPTION_MESSAGE));

        Selectable nextFlowStepSelector = underTest.doAccept(getHandlerEvent());

        assertEquals(EventSelectorUtil.selector(DatalakeRecoverySetupNewInstancesFailedEvent.class), nextFlowStepSelector.selector());
        DatalakeRecoverySetupNewInstancesFailedEvent failureEvent = (DatalakeRecoverySetupNewInstancesFailedEvent) nextFlowStepSelector;
        assertEquals(EXCEPTION_MESSAGE, failureEvent.getException().getMessage());
    }

    private HandlerEvent<DatalakeRecoverySetupNewInstancesRequest> getHandlerEvent() {
        DatalakeRecoverySetupNewInstancesRequest datalakeRecoverySetupNewInstancesRequest =
                new DatalakeRecoverySetupNewInstancesRequest(STACK_ID);
        HandlerEvent<DatalakeRecoverySetupNewInstancesRequest> handlerEvent = mock(HandlerEvent.class);
        when(handlerEvent.getData()).thenReturn(datalakeRecoverySetupNewInstancesRequest);
        return handlerEvent;
    }

    private CloudInstance setupCloudInstance() {
        return mock(CloudInstance.class);
    }

    private InstanceGroup setupInstanceGroup(Long id) {
        InstanceGroup instanceGroup = new InstanceGroup();
        instanceGroup.setId(id);
        instanceGroup.setInitialNodeCount(0);
        instanceGroup.setGroupName("group" + id);
        return instanceGroup;
    }
}
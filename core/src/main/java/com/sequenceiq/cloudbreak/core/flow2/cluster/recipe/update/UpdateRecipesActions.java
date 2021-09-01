package com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.AVAILABLE;
import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.UPDATE_FAILED;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesHandlerSelectors.UPDATE_RECIPES_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesStateSelectors.FINALIZE_UPDATE_RECIPES_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesStateSelectors.HANDLED_FAILED_UPDATE_RECIPES_EVENT;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

import com.sequenceiq.cloudbreak.cloud.scheduler.PollGroup;
import com.sequenceiq.cloudbreak.cloud.store.InMemoryStateStore;
import com.sequenceiq.cloudbreak.common.event.ResourceCrnPayload;
import com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesEvent;
import com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesFailureEvent;
import com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesStateSelectors;
import com.sequenceiq.cloudbreak.event.ResourceEvent;
import com.sequenceiq.cloudbreak.logger.MdcContext;
import com.sequenceiq.cloudbreak.structuredevent.event.CloudbreakEventService;
import com.sequenceiq.flow.core.AbstractAction;
import com.sequenceiq.flow.core.CommonContext;
import com.sequenceiq.flow.core.FlowParameters;

@Configuration
public class UpdateRecipesActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateRecipesActions.class);

    @Inject
    private CloudbreakEventService cloudbreakEventService;

    @Bean("UPDATE_RECIPES_START_STATE")
    public Action<?, ?> updateRecipesStartAction() {
        return new UpdateRecipesActions.AbstractUpdateRecipesActions<>(UpdateRecipesEvent.class) {
            @Override
            protected void doExecute(CommonContext context, UpdateRecipesEvent payload, Map<Object, Object> variables) throws Exception {
                Long resourceId = payload.getResourceId();
                String resourceCrn = payload.getResourceCrn();
                boolean refreshClusterOnly = payload.isRefreshClusterOnly();
                Map<String, Set<String>> recipesToAttach = payload.getRecipesToAttach();
                Map<String, Set<String>> recipesToDetach = payload.getRecipesToDetach();
                LOGGER.debug("Flow entered into UPDATE_RECIPES_START_STATE. resourceCrn: '{}', recipesToAttach: {}, recipesToDetach: {}",
                        resourceCrn, recipesToAttach, recipesToDetach);
                if (refreshClusterOnly) {
                    String attachMessage = createMessageFromMap(recipesToAttach);
                    String detachMessage = createMessageFromMap(recipesToDetach);
                    cloudbreakEventService.fireCloudbreakEvent(resourceId, AVAILABLE.name(), ResourceEvent.CLUSTER_UPDATE_RECIPE_STARTED,
                            List.of(attachMessage, detachMessage));
                } else {
                    cloudbreakEventService.fireCloudbreakEvent(resourceId, AVAILABLE.name(), ResourceEvent.CLUSTER_UPDATE_RECIPE_ONLY_REFRESH_STARTED);
                }
                InMemoryStateStore.putStack(resourceId, PollGroup.POLLABLE);
                sendEvent(context, new UpdateRecipesEvent(UPDATE_RECIPES_EVENT.selector(), resourceId, resourceCrn,
                        payload.isRefreshClusterOnly(), recipesToAttach, recipesToDetach));
            }
        };
    }

    @Bean("UPDATE_RECIPES_FINISHED_STATE")
    public Action<?, ?> updateRecipesFinishedAction() {
        return new UpdateRecipesActions.AbstractUpdateRecipesActions<>(UpdateRecipesEvent.class) {
            @Override
            protected void doExecute(CommonContext context, UpdateRecipesEvent payload, Map<Object, Object> variables) throws Exception {
                Long resourceId = payload.getResourceId();
                String resourceCrn = payload.getResourceCrn();
                LOGGER.debug("Flow entered into UPDATE_RECIPES_FINISHED_STATE. resourceCrn: '{}'", resourceCrn);
                cloudbreakEventService.fireCloudbreakEvent(resourceId, AVAILABLE.name(), ResourceEvent.CLUSTER_UPDATE_RECIPE_FINISHED);
                InMemoryStateStore.deleteStack(resourceId);
                sendEvent(context, new UpdateRecipesEvent(FINALIZE_UPDATE_RECIPES_EVENT.selector(), resourceId, resourceCrn,
                        payload.isRefreshClusterOnly(), payload.getRecipesToAttach(), payload.getRecipesToDetach()));
            }
        };
    }

    @Bean("UPDATE_RECIPES_FAILED_STATE")
    public Action<?, ?> updateRecipesFailedAction() {
        return new UpdateRecipesActions.AbstractUpdateRecipesActions<>(UpdateRecipesFailureEvent.class) {
            @Override
            protected void doExecute(CommonContext context, UpdateRecipesFailureEvent payload, Map<Object, Object> variables) throws Exception {
                Long resourceId = payload.getResourceId();
                String resourceCrn = payload.getResourceCrn();
                LOGGER.debug("Flow entered into UPDATE_RECIPES_FAILED_STATE. resourceCrn: '{}'", resourceCrn);
                cloudbreakEventService.fireCloudbreakEvent(resourceId, UPDATE_FAILED.name(), ResourceEvent.CLUSTER_UPDATE_RECIPE_FAILED,
                        List.of(payload.getException().getMessage()));
                InMemoryStateStore.deleteStack(resourceId);
                sendEvent(context, new UpdateRecipesEvent(HANDLED_FAILED_UPDATE_RECIPES_EVENT.selector(), resourceId, resourceCrn,
                        payload.isRefreshClusterOnly(), payload.getRecipesToAttach(), payload.getRecipesToDetach()));
            }
        };
    }

    private String createMessageFromMap(Map<String, Set<String>> map) {
        if (map.isEmpty()) {
            return "";
        } else {
            return map.entrySet().stream()
                    .map(x -> x.getKey() + ": " + x.getValue().stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(", ")))
                    .collect(Collectors.joining("; "));
        }
    }

    private abstract class AbstractUpdateRecipesActions<P extends ResourceCrnPayload>
            extends AbstractAction<UpdateRecipesState, UpdateRecipesStateSelectors, CommonContext, P> {

        protected AbstractUpdateRecipesActions(Class<P> payloadClass) {
            super(payloadClass);
        }

        @Override
        protected CommonContext createFlowContext(FlowParameters flowParameters,
                StateContext<UpdateRecipesState, UpdateRecipesStateSelectors> stateContext, P payload) {
            return new CommonContext(flowParameters);
        }

        @Override
        protected Object getFailurePayload(P payload, Optional<CommonContext> flowContext, Exception ex) {
            return payload;
        }

        @Override
        protected void prepareExecution(P payload, Map<Object, Object> variables) {
            if (payload != null) {
                MdcContext.builder().resourceCrn(payload.getResourceCrn()).buildMdc();
            } else {
                LOGGER.warn("Payload was null in prepareExecution so resourceCrn cannot be added to the MdcContext!");
            }
        }
    }

}

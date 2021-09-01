package com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.handler;

import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesHandlerSelectors.UPDATE_RECIPES_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesStateSelectors.FINISH_UPDATE_RECIPES_EVENT;

import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesEvent;
import com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesFailureEvent;
import com.sequenceiq.cloudbreak.service.cluster.flow.recipe.RecipeEngine;
import com.sequenceiq.flow.reactor.api.event.EventSender;
import com.sequenceiq.flow.reactor.api.handler.EventSenderAwareHandler;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class UpdateRecipesHandler extends EventSenderAwareHandler<UpdateRecipesEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateRecipesHandler.class);

    @Inject
    private EventBus eventBus;

    @Inject
    private RecipeEngine recipeEngine;

    public UpdateRecipesHandler(EventSender eventSender) {
        super(eventSender);
    }

    @Override
    public void accept(Event<UpdateRecipesEvent> event) {
        UpdateRecipesEvent data = event.getData();
        Long resourceId = data.getResourceId();
        String resourceCrn = data.getResourceCrn();
        boolean refreshClusterOnly = data.isRefreshClusterOnly();
        Map<String, Set<String>> recipesToAttach = data.getRecipesToAttach();
        Map<String, Set<String>> recipesToDetach = data.getRecipesToDetach();
        try {
            recipeEngine.uploadRecipes(resourceId, "UpdateRecipesHandler");
            UpdateRecipesEvent updateRecipeEvent = new UpdateRecipesEvent(FINISH_UPDATE_RECIPES_EVENT.selector(), resourceId, resourceCrn,
                    refreshClusterOnly, recipesToAttach, recipesToDetach);
            eventSender().sendEvent(updateRecipeEvent, event.getHeaders());
        } catch (Exception e) {
            LOGGER.debug("Cluster recipes update failed. resourceCrn: '{}'.", resourceCrn, e);
            UpdateRecipesFailureEvent failureEvent = new UpdateRecipesFailureEvent(resourceId, resourceCrn,
                    refreshClusterOnly, recipesToAttach, recipesToDetach, e);
            eventBus.notify(failureEvent.selector(), new Event<>(event.getHeaders(), failureEvent));
        }
    }

    @Override
    public String selector() {
        return UPDATE_RECIPES_EVENT.selector();
    }
}

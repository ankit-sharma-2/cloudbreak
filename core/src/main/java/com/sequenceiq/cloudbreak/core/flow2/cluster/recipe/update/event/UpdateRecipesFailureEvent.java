package com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event;

import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesStateSelectors.FAILED_UPDATE_RECIPES_EVENT;

import java.util.Map;
import java.util.Set;

public class UpdateRecipesFailureEvent extends UpdateRecipesEvent {

    private final Exception exception;

    public UpdateRecipesFailureEvent(Long resourceId, String resourceCrn, boolean refreshClusterOnly, Map<String, Set<String>> recipesToAttach,
            Map<String, Set<String>> recipesToDetach, Exception exception) {
        super(FAILED_UPDATE_RECIPES_EVENT.name(), resourceId, resourceCrn, refreshClusterOnly, recipesToAttach, recipesToDetach);
        this.exception = exception;
    }

    public Exception getException() {
        return exception;
    }

    @Override
    public String selector() {
        return FAILED_UPDATE_RECIPES_EVENT.name();
    }

}

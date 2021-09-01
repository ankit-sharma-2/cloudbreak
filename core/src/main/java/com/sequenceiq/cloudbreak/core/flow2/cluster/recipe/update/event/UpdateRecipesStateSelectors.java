package com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event;

import com.sequenceiq.flow.core.FlowEvent;

public enum UpdateRecipesStateSelectors implements FlowEvent {
    START_UPDATE_RECIPES_EVENT,
    FINISH_UPDATE_RECIPES_EVENT,
    FINALIZE_UPDATE_RECIPES_EVENT,
    FAILED_UPDATE_RECIPES_EVENT,
    HANDLED_FAILED_UPDATE_RECIPES_EVENT;

    @Override
    public String event() {
        return name();
    }
}

package com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event;

import com.sequenceiq.flow.core.FlowEvent;

public enum UpdateRecipesHandlerSelectors implements FlowEvent {
    UPDATE_RECIPES_EVENT;

    @Override
    public String event() {
        return name();
    }
}

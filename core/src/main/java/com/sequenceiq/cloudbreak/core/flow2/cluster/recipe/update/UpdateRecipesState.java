package com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update;

import com.sequenceiq.cloudbreak.core.flow2.restart.FillInMemoryStateStoreRestartAction;
import com.sequenceiq.flow.core.FlowState;
import com.sequenceiq.flow.core.RestartAction;

public enum UpdateRecipesState implements FlowState {
    INIT_STATE,
    UPDATE_RECIPES_START_STATE,
    UPDATE_RECIPES_FAILED_STATE,
    UPDATE_RECIPES_FINISHED_STATE,
    FINAL_STATE;

    @Override
    public Class<? extends RestartAction> restartAction() {
        return FillInMemoryStateStoreRestartAction.class;
    }
}

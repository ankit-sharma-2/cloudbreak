package com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.config;

import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.UpdateRecipesState.UPDATE_RECIPES_FINISHED_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.UpdateRecipesState.UPDATE_RECIPES_START_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesStateSelectors.FAILED_UPDATE_RECIPES_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesStateSelectors.FINALIZE_UPDATE_RECIPES_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesStateSelectors.FINISH_UPDATE_RECIPES_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesStateSelectors.HANDLED_FAILED_UPDATE_RECIPES_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesStateSelectors.START_UPDATE_RECIPES_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.UpdateRecipesState.UPDATE_RECIPES_FAILED_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.UpdateRecipesState.FINAL_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.UpdateRecipesState.INIT_STATE;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.UpdateRecipesState;
import com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event.UpdateRecipesStateSelectors;

import com.sequenceiq.flow.core.config.AbstractFlowConfiguration;
import com.sequenceiq.flow.core.config.RetryableFlowConfiguration;

@Component
public class UpdateRecipesFlowConfig extends AbstractFlowConfiguration<UpdateRecipesState, UpdateRecipesStateSelectors>
        implements RetryableFlowConfiguration<UpdateRecipesStateSelectors> {

    private static final List<Transition<UpdateRecipesState, UpdateRecipesStateSelectors>> TRANSITIONS
            = new Transition.Builder<UpdateRecipesState, UpdateRecipesStateSelectors>()
            .defaultFailureEvent(FAILED_UPDATE_RECIPES_EVENT)

            .from(INIT_STATE).to(UPDATE_RECIPES_START_STATE)
            .event(START_UPDATE_RECIPES_EVENT)
            .failureState(UPDATE_RECIPES_FAILED_STATE)
            .defaultFailureEvent()

            .from(UPDATE_RECIPES_START_STATE).to(UPDATE_RECIPES_FINISHED_STATE)
            .event(FINISH_UPDATE_RECIPES_EVENT)
            .failureState(UPDATE_RECIPES_FAILED_STATE)
            .defaultFailureEvent()

            .from(UPDATE_RECIPES_FINISHED_STATE).to(FINAL_STATE)
            .event(FINALIZE_UPDATE_RECIPES_EVENT)
            .failureState(UPDATE_RECIPES_FAILED_STATE)
            .defaultFailureEvent()

            .build();

    private static final FlowEdgeConfig<UpdateRecipesState, UpdateRecipesStateSelectors> EDGE_CONFIG =
            new FlowEdgeConfig<>(INIT_STATE, FINAL_STATE, UPDATE_RECIPES_FAILED_STATE, HANDLED_FAILED_UPDATE_RECIPES_EVENT);

    protected UpdateRecipesFlowConfig() {
        super(UpdateRecipesState.class, UpdateRecipesStateSelectors.class);
    }

    @Override
    protected List<Transition<UpdateRecipesState, UpdateRecipesStateSelectors>> getTransitions() {
        return TRANSITIONS;
    }

    @Override
    protected FlowEdgeConfig<UpdateRecipesState, UpdateRecipesStateSelectors> getEdgeConfig() {
        return EDGE_CONFIG;
    }

    @Override
    public UpdateRecipesStateSelectors[] getEvents() {
        return UpdateRecipesStateSelectors.values();
    }

    @Override
    public UpdateRecipesStateSelectors[] getInitEvents() {
        return new UpdateRecipesStateSelectors[] {
                START_UPDATE_RECIPES_EVENT
        };
    }

    @Override
    public String getDisplayName() {
        return "Refresh cluster recipes";
    }

    @Override
    public UpdateRecipesStateSelectors getRetryableEvent() {
        return HANDLED_FAILED_UPDATE_RECIPES_EVENT;
    }
}

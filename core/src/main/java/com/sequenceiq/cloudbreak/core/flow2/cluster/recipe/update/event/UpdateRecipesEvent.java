package com.sequenceiq.cloudbreak.core.flow2.cluster.recipe.update.event;

import java.util.Map;
import java.util.Set;

import com.sequenceiq.flow.reactor.api.event.BaseFlowEvent;

public class UpdateRecipesEvent extends BaseFlowEvent {

    private boolean refreshClusterOnly;

    private Map<String, Set<String>> recipesToAttach;

    private Map<String, Set<String>> recipesToDetach;

    public UpdateRecipesEvent(String selector, Long resourceId, String resourceCrn, boolean refreshClusterOnly,
            Map<String, Set<String>> recipesToAttach, Map<String, Set<String>> recipesToDetach) {
        super(selector, resourceId, resourceCrn);
        this.refreshClusterOnly = refreshClusterOnly;
        this.recipesToAttach = recipesToAttach;
        this.recipesToDetach = recipesToDetach;
    }

    public Map<String, Set<String>> getRecipesToAttach() {
        return recipesToAttach;
    }

    public void setRecipesToAttach(Map<String, Set<String>> recipesToAttach) {
        this.recipesToAttach = recipesToAttach;
    }

    public Map<String, Set<String>> getRecipesToDetach() {
        return recipesToDetach;
    }

    public void setRecipesToDetach(Map<String, Set<String>> recipesToDetach) {
        this.recipesToDetach = recipesToDetach;
    }

    public boolean isRefreshClusterOnly() {
        return refreshClusterOnly;
    }

    public void setRefreshClusterOnly(boolean refreshClusterOnly) {
        this.refreshClusterOnly = refreshClusterOnly;
    }
}

package com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.recipe;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.base.UpdateHostGroupRecipes;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.base.UpdateRecipesOperationType;
import com.sequenceiq.common.model.JsonEntity;

import io.swagger.annotations.ApiModel;

@ApiModel
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateRecipesV4Request implements JsonEntity {

    private UpdateRecipesOperationType updateRecipesOperationType = UpdateRecipesOperationType.PERSIST_AND_CLUSTER_REFRESH;

    private List<UpdateHostGroupRecipes> hostGroupRecipes = new ArrayList<>();

    public List<UpdateHostGroupRecipes> getHostGroupRecipes() {
        return hostGroupRecipes;
    }

    public void setHostGroupRecipes(List<UpdateHostGroupRecipes> hostGroupRecipes) {
        this.hostGroupRecipes = hostGroupRecipes;
    }

    public UpdateRecipesOperationType getUpdateRecipesOperationType() {
        return updateRecipesOperationType;
    }

    public void setUpdateRecipesOperationType(UpdateRecipesOperationType updateRecipesOperationType) {
        this.updateRecipesOperationType = updateRecipesOperationType;
    }

    @Override
    public String toString() {
        return "UpdateRecipesV4Request{" +
                "updateRecipesOperationType=" + updateRecipesOperationType +
                ", hostGroupRecipes=" + hostGroupRecipes +
                '}';
    }
}

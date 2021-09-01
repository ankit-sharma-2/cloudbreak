package com.sequenceiq.cloudbreak.service.cluster.flow.recipe;

import static com.sequenceiq.cloudbreak.common.model.recipe.RecipeType.POST_CLOUDERA_MANAGER_START;
import static com.sequenceiq.cloudbreak.common.model.recipe.RecipeType.PRE_CLOUDERA_MANAGER_START;
import static com.sequenceiq.cloudbreak.common.model.recipe.RecipeType.PRE_TERMINATION;
import static com.sequenceiq.cloudbreak.util.Benchmark.checkedMeasure;
import static com.sequenceiq.cloudbreak.util.Benchmark.measure;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.common.model.recipe.RecipeType;
import com.sequenceiq.cloudbreak.domain.Recipe;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostGroup;
import com.sequenceiq.cloudbreak.ldap.LdapConfigService;
import com.sequenceiq.cloudbreak.service.CloudbreakException;
import com.sequenceiq.cloudbreak.service.hostgroup.HostGroupService;
import com.sequenceiq.cloudbreak.service.resource.ResourceService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.common.api.type.InstanceGroupType;

@Component
public class RecipeEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeEngine.class);

    @Inject
    private OrchestratorRecipeExecutor orchestratorRecipeExecutor;

    @Inject
    private LdapConfigService ldapConfigService;

    @Inject
    private StackService stackService;

    @Inject
    private HostGroupService hostGroupService;

    @Inject
    private ResourceService resourceService;

    public void uploadRecipes(Long stackId, String caller) throws CloudbreakException {
        LOGGER.info("Upload recipes started for {} stack", stackId);
        Stack stack = measure(() -> stackService.getByIdWithListsInTransaction(stackId), LOGGER,
                "stackService.getByIdWithListsInTransaction() took {} ms in {}", caller);
        stack.setResources(measure(() -> resourceService.getNotInstanceRelatedByStackId(stackId), LOGGER,
                "resourceService.getNotInstanceRelatedByStackId() took {} ms in {}", caller));
        Set<HostGroup> hostGroups = measure(() -> hostGroupService.getByClusterWithRecipes(stack.getCluster().getId()), LOGGER,
                "hostGroupService.getByClusterWithRecipes() took {} ms in {}", caller);
        uploadRecipesOnHostGroups(stack, hostGroups);
        LOGGER.info("Upload recipes finished successfully for {} stack by {}", stackId, caller);
    }

    private void uploadRecipesOnHostGroups(Stack stack, Set<HostGroup> hostGroups) throws CloudbreakException {
        boolean recipesFound = recipesFound(hostGroups);
        if (recipesFound) {
            checkedMeasure(() -> orchestratorRecipeExecutor.uploadRecipes(stack, hostGroups), LOGGER, "Upload recipes took {} ms");
        }
    }

    public void uploadUpscaleRecipes(Stack stack, HostGroup hostGroup, Set<HostGroup> hostGroups)
            throws CloudbreakException {
        Set<HostGroup> hgs = Collections.singleton(hostGroup);
        if (recipesFound(hgs)) {
            if (hostGroup.getInstanceGroup().getInstanceGroupType() == InstanceGroupType.GATEWAY) {
                orchestratorRecipeExecutor.uploadRecipes(stack, hostGroups);
            }
        }
    }

    public void executePreClusterManagerRecipes(Stack stack, Collection<Recipe> recipes) throws CloudbreakException {
        if (shouldExecuteRecipeOnStack(recipes, PRE_CLOUDERA_MANAGER_START)) {
            orchestratorRecipeExecutor.preClusterManagerStartRecipes(stack);
        }
    }

    // note: executed when LDAP config is present, because later the LDAP sync is hooked for this salt state in the top.sls.
    public void executePostAmbariStartRecipes(Stack stack, Collection<Recipe> recipes) throws CloudbreakException {
        if ((stack.getCluster() != null && ldapConfigService.isLdapConfigExistsForEnvironment(stack.getEnvironmentCrn(), stack.getName()))
                || recipesFound(recipes, POST_CLOUDERA_MANAGER_START)) {
            orchestratorRecipeExecutor.postClusterManagerStartRecipes(stack);
        }
    }

    public void executePostInstallRecipes(Stack stack) throws CloudbreakException {
        orchestratorRecipeExecutor.postClusterInstall(stack);
    }

    public void executePreTerminationRecipes(Stack stack, Collection<Recipe> recipes, boolean forced) throws CloudbreakException {
        if (shouldExecuteRecipeOnStack(recipes, PRE_TERMINATION)) {
            orchestratorRecipeExecutor.preTerminationRecipes(stack, forced);
        }
    }

    public void executePreTerminationRecipes(Stack stack, Collection<Recipe> recipes, Set<String> hostNames) throws CloudbreakException {
        if (shouldExecuteRecipeOnStack(recipes, PRE_TERMINATION)) {
            orchestratorRecipeExecutor.preTerminationRecipes(stack, hostNames);
        }
    }

    private boolean shouldExecuteRecipeOnStack(Collection<Recipe> recipes, RecipeType recipeType) {
        return recipesFound(recipes, recipeType);
    }

    private boolean recipesFound(Iterable<HostGroup> hostGroups) {
        for (HostGroup hostGroup : hostGroups) {
            if (!hostGroup.getRecipes().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean recipesFound(Collection<Recipe> recipes, RecipeType recipeType) {
        return recipes.stream().anyMatch(recipe -> recipeType.equals(recipe.getRecipeType()));
    }
}

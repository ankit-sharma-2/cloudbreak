package com.sequenceiq.cloudbreak.service.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.common.base.Joiner;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.base.UpdateHostGroupRecipes;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.base.UpdateRecipesOperationType;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.recipe.UpdateRecipesV4Response;
import com.sequenceiq.cloudbreak.common.exception.BadRequestException;
import com.sequenceiq.cloudbreak.common.service.TransactionService;
import com.sequenceiq.cloudbreak.core.flow2.service.ReactorFlowManager;
import com.sequenceiq.cloudbreak.domain.Recipe;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostGroup;
import com.sequenceiq.cloudbreak.repository.RecipeRepository;
import com.sequenceiq.cloudbreak.service.hostgroup.HostGroupService;
import com.sequenceiq.flow.api.model.FlowIdentifier;

@Service
public class UpdateRecipeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateRecipeService.class);

    private final RecipeRepository recipeRepository;

    private final HostGroupService hostGroupService;

    private final TransactionService transactionService;

    private final ReactorFlowManager flowManager;

    public UpdateRecipeService(RecipeRepository recipeRepository, HostGroupService hostGroupService,
            TransactionService transactionService, ReactorFlowManager flowManager) {
        this.recipeRepository = recipeRepository;
        this.hostGroupService = hostGroupService;
        this.transactionService = transactionService;
        this.flowManager = flowManager;
    }

    /**
     * Updating recipes for an existing cluster. The input should contain host group - recipes mapping and an operation type.
     * If a host group key from the mappings is missing from the input, that is not going to be updated.
     * The operation type will decide that recipes needs to be updated only in the db or only a cluster refresh flow needs to be triggered
     * (or both - that is the default). Output is the newly attached/detached recipes in db and the cluster refresh flow id.
     */
    public UpdateRecipesV4Response refreshRecipesForCluster(Long workspaceId, Stack stack, List<UpdateHostGroupRecipes> recipesPerHostGroup,
            UpdateRecipesOperationType operationType) throws TransactionService.TransactionExecutionException {
        List<UpdateHostGroupRecipes> attachRecipesList = new ArrayList<>();
        List<UpdateHostGroupRecipes> detachRecipesList = new ArrayList<>();
        boolean persistsRecipe = UpdateRecipesOperationType.PERSIST_ONLY.equals(operationType)
                || UpdateRecipesOperationType.PERSIST_AND_CLUSTER_REFRESH.equals(operationType);
        if (persistsRecipe) {
            Set<String> recipesToFind = new HashSet<>();
            Map<String, Set<String>> recipesToUpdate = new HashMap<>();
            recipesPerHostGroup.forEach(r -> recipesToFind.addAll(r.getRecipeNames()));
            recipesPerHostGroup.forEach(r -> recipesToUpdate.put(r.getHostGroupName(), r.getRecipeNames()));
            LOGGER.debug("Update recipes {}", recipesToUpdate);
            Set<Recipe> recipes = transactionService.required(() -> recipeRepository.findByNameInAndWorkspaceId(recipesToFind, workspaceId));
            Set<String> existingRecipesNames = recipes
                    .stream().map(Recipe::getName).collect(Collectors.toSet());
            LOGGER.debug("Recipes found in database: {}, expected update related recipes: {}", existingRecipesNames, recipesToFind);
            Set<String> invalidRecipes = recipesToFind.stream().filter(r -> !existingRecipesNames.contains(r))
                    .collect(Collectors.toSet());
            if (!invalidRecipes.isEmpty()) {
                throw new BadRequestException(String.format("Following recipes do not exist in workspace: %s", Joiner.on(",").join(invalidRecipes)));
            }
            Set<HostGroup> hostGroups = transactionService.required(() -> hostGroupService.getByClusterWithRecipes(stack.getCluster().getId()));
            updateRecipesForHostGroups(recipesToUpdate, recipes, hostGroups, attachRecipesList, detachRecipesList);
        }
        UpdateRecipesV4Response result = new UpdateRecipesV4Response();
        boolean recipesModified = false;
        if (!attachRecipesList.isEmpty()) {
            result.setRecipesAttached(attachRecipesList);
            LOGGER.debug("Attached recipes per host group: {}", attachRecipesList);
            recipesModified = true;
        }
        if (!detachRecipesList.isEmpty()) {
            result.setRecipesDetached(detachRecipesList);
            LOGGER.debug("Detaches recipes per host group: {}", detachRecipesList);
            recipesModified = true;
        }
        if (!UpdateRecipesOperationType.PERSIST_ONLY.equals(operationType) && recipesModified) {
            Map<String, Set<String>> recipesToAttach = new HashMap<>();
            attachRecipesList.forEach(a -> recipesToAttach.put(a.getHostGroupName(), a.getRecipeNames()));
            Map<String, Set<String>> recipesToDetach = new HashMap<>();
            detachRecipesList.forEach(a -> recipesToDetach.put(a.getHostGroupName(), a.getRecipeNames()));
            FlowIdentifier flowIdentifier = flowManager.triggerRecipesRefresh(stack.getId(), stack.getResourceCrn(),
                    false, recipesToAttach, recipesToDetach);
            result.setFlowId(flowIdentifier.getPollableId());
        } else if (UpdateRecipesOperationType.CLUSTER_REFRESH_ONLY.equals(operationType)) {
            FlowIdentifier flowIdentifier = flowManager.triggerRecipesRefresh(stack.getId(), stack.getResourceCrn(),
                    true, new HashMap<>(), new HashMap<>());
            result.setFlowId(flowIdentifier.getPollableId());
        }
        return result;
    }

    private void updateRecipesForHostGroups(Map<String, Set<String>> recipesToUpdate, Set<Recipe> recipes, Set<HostGroup> hostGroups,
            List<UpdateHostGroupRecipes> attachRecipesList, List<UpdateHostGroupRecipes> detachRecipesList)
            throws TransactionService.TransactionExecutionException {
        for (HostGroup hostGroup : hostGroups) {
            String hostGroupName = hostGroup.getName();
            if (recipesToUpdate.containsKey(hostGroupName)) {
                LOGGER.debug("Checking host group '{}' needs any refresh.", hostGroupName);
                Set<String> recipesForHostGroup = recipesToUpdate.get(hostGroupName);
                Set<Recipe> existingRecipes = hostGroup.getRecipes();
                Set<String> existingRecipeNames = existingRecipes
                        .stream().map(Recipe::getName)
                        .collect(Collectors.toSet());
                LOGGER.debug("Current recipes: {}", existingRecipes);
                boolean allExists = existingRecipes.stream().allMatch(r -> recipesForHostGroup.contains(r.getName()));
                if (allExists && recipesForHostGroup.size() == existingRecipes.size()) {
                    LOGGER.debug("No need for any recipe update for '{}' host group", hostGroupName);
                } else {
                    Set<Recipe> updateRecipes = recipes.stream()
                            .filter(r -> recipesForHostGroup.contains(r.getName()))
                            .collect(Collectors.toSet());
                    Set<String> recipesToDeleteFromHostGroup = existingRecipes.stream()
                            .map(Recipe::getName)
                            .filter(name -> !recipesForHostGroup.contains(name))
                            .collect(Collectors.toSet());
                    Set<String> recipesToAddToHostGroup = updateRecipes.stream()
                            .map(Recipe::getName)
                            .filter(r -> !existingRecipeNames.contains(r))
                            .collect(Collectors.toSet());
                    if (!recipesToAddToHostGroup.isEmpty()) {
                        UpdateHostGroupRecipes attachHostGroupRecipes = new UpdateHostGroupRecipes();
                        attachHostGroupRecipes.setHostGroupName(hostGroupName);
                        attachHostGroupRecipes.setRecipeNames(recipesToAddToHostGroup);
                        attachRecipesList.add(attachHostGroupRecipes);
                        LOGGER.debug("Recipes attached to '{}' host group: {}", hostGroupName, attachHostGroupRecipes);
                    }
                    if (!recipesToDeleteFromHostGroup.isEmpty()) {
                        UpdateHostGroupRecipes detachHostGroupRecipes = new UpdateHostGroupRecipes();
                        detachHostGroupRecipes.setHostGroupName(hostGroupName);
                        detachHostGroupRecipes.setRecipeNames(recipesToDeleteFromHostGroup);
                        detachRecipesList.add(detachHostGroupRecipes);
                        LOGGER.debug("Recipes detached from '{}' host group: {}", hostGroupName, detachHostGroupRecipes);
                    }
                    hostGroup.setRecipes(updateRecipes);
                    transactionService.required(() -> hostGroupService.save(hostGroup));
                }
            }
        }
    }
}

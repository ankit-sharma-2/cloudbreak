package com.sequenceiq.cloudbreak.cloud.azure.upscale;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.collections4.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.resources.Deployment;
import com.sequenceiq.cloudbreak.cloud.azure.AzureCloudResourceService;
import com.sequenceiq.cloudbreak.cloud.azure.AzureInstanceTemplateOperation;
import com.sequenceiq.cloudbreak.cloud.azure.AzureResourceGroupMetadataProvider;
import com.sequenceiq.cloudbreak.cloud.azure.AzureTerminationHelperService;
import com.sequenceiq.cloudbreak.cloud.azure.AzureUtils;
import com.sequenceiq.cloudbreak.cloud.azure.client.AzureClient;
import com.sequenceiq.cloudbreak.cloud.azure.connector.resource.AzureComputeResourceService;
import com.sequenceiq.cloudbreak.cloud.azure.template.AzureTemplateDeploymentService;
import com.sequenceiq.cloudbreak.cloud.azure.view.AzureInstanceView;
import com.sequenceiq.cloudbreak.cloud.azure.view.AzureStackView;
import com.sequenceiq.cloudbreak.cloud.context.AuthenticatedContext;
import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.exception.CloudConnectorException;
import com.sequenceiq.cloudbreak.cloud.exception.RolledbackResourcesException;
import com.sequenceiq.cloudbreak.cloud.model.CloudInstance;
import com.sequenceiq.cloudbreak.cloud.model.CloudResource;
import com.sequenceiq.cloudbreak.cloud.model.CloudResourceStatus;
import com.sequenceiq.cloudbreak.cloud.model.CloudStack;
import com.sequenceiq.cloudbreak.cloud.model.Group;
import com.sequenceiq.cloudbreak.cloud.model.ResourceStatus;
import com.sequenceiq.cloudbreak.cloud.notification.ResourceNotifier;
import com.sequenceiq.cloudbreak.cloud.transform.CloudResourceHelper;
import com.sequenceiq.common.api.type.CommonStatus;
import com.sequenceiq.common.api.type.ResourceType;

@Component
public class AzureUpscaleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureUpscaleService.class);

    @Inject
    private AzureUtils azureUtils;

    @Inject
    private CloudResourceHelper cloudResourceHelper;

    @Inject
    private AzureTerminationHelperService azureTerminationHelperService;

    @Inject
    private AzureComputeResourceService azureComputeResourceService;

    @Inject
    private AzureTemplateDeploymentService azureTemplateDeploymentService;

    @Inject
    private AzureResourceGroupMetadataProvider azureResourceGroupMetadataProvider;

    @Inject
    private ResourceNotifier resourceNotifier;

    @Inject
    private AzureCloudResourceService azureCloudResourceService;

    public List<CloudResourceStatus> upscale(AuthenticatedContext ac, CloudStack stack, List<CloudResource> resources, AzureStackView azureStackView,
            AzureClient client) {
        CloudContext cloudContext = ac.getCloudContext();
        String stackName = azureUtils.getStackName(cloudContext);
        String resourceGroupName = azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, stack);

        List<CloudResource> newInstances = new ArrayList<>();
        List<CloudResource> templateResources = new ArrayList<>();
        List<CloudResource> osDiskResources = new ArrayList<>();

        try {
            List<Group> scaledGroups = cloudResourceHelper.getScaledGroups(stack);
            CloudResource armTemplate = getArmTemplate(resources, stackName);
            purgeExistingInstances(azureStackView);
            Deployment templateDeployment =
                    azureTemplateDeploymentService.getTemplateDeployment(client, stack, ac, azureStackView, AzureInstanceTemplateOperation.UPSCALE);
            LOGGER.info("Created template deployment for upscale: {}", templateDeployment.exportTemplate().template());

            templateResources.addAll(azureCloudResourceService.getDeploymentCloudResources(templateDeployment));
            newInstances.addAll(azureCloudResourceService.getInstanceCloudResources(stackName, templateResources, scaledGroups, resourceGroupName));
            if (!newInstances.isEmpty()) {
                osDiskResources.addAll(azureCloudResourceService.getAttachedOsDiskResources(newInstances, resourceGroupName, client));
            } else {
                LOGGER.warn("Skipping OS disk collection as there was no VM instance found amongst cloud resources for {}!", stackName);
            }

            azureCloudResourceService.saveCloudResources(resourceNotifier, cloudContext, ListUtils.union(templateResources, osDiskResources));

            List<CloudResource> reattachableVolumeSets = getReattachableVolumeSets(resources, newInstances);
            List<CloudResource> networkResources = azureCloudResourceService.getNetworkResources(resources);

            azureComputeResourceService.buildComputeResourcesForUpscale(ac, stack, scaledGroups, newInstances, reattachableVolumeSets, networkResources);

            return Collections.singletonList(new CloudResourceStatus(armTemplate, ResourceStatus.IN_PROGRESS));
        } catch (CloudException e) {
            throw azureUtils.convertToCloudConnectorException(e, "Stack upscale");
        } catch (RolledbackResourcesException e) {
            rollbackInstances(ac, stack, resources, newInstances, templateResources, osDiskResources);
            throw new CloudConnectorException(String.format("Could not upscale Azure infrastructure, infrastructure was rolled back with resources: %s, %s",
                    stackName, e.getMessage()), e);
        } catch (Exception e) {
            rollbackInstances(ac, stack, resources, newInstances, templateResources, osDiskResources);
            throw new CloudConnectorException(String.format("Could not upscale Azure infrastructure, infrastructure was rolled back: %s, %s", stackName,
                    e.getMessage()), e);
        }
    }

    public void rollbackInstances(AuthenticatedContext ac, CloudStack stack, List<CloudResource> resources, List<CloudResource> newInstances,
            List<CloudResource> templateResources, List<CloudResource> osDiskResources) {
        List<CloudInstance> newCloudInstances = getNewInstances(newInstances);
        List<CloudResource> allRemovableResource = new ArrayList<>();
        allRemovableResource.addAll(templateResources);
        allRemovableResource.addAll(osDiskResources);
        azureTerminationHelperService.downscale(ac, stack, newCloudInstances, resources, allRemovableResource);
    }

    private List<CloudInstance> getNewInstances(List<CloudResource> newInstances) {
        List<CloudInstance> newCloudInstances = newInstances.stream()
                .map(cloudResource -> new CloudInstance(
                        cloudResource.getInstanceId(),
                        null,
                        null,
                        null,
                        null,
                        cloudResource.getParameters()))
                .collect(Collectors.toList());
        LOGGER.debug("Created instances to be removed {}", newCloudInstances.toString());
        return newCloudInstances;
    }

    private void purgeExistingInstances(AzureStackView azureStackView) {
        azureStackView.getGroups().forEach((key, value) -> value.removeIf(AzureInstanceView::hasRealInstanceId));
        azureStackView.getGroups().entrySet().removeIf(group -> group.getValue() == null || group.getValue().isEmpty());
    }

    private CloudResource getArmTemplate(List<CloudResource> resources, String stackName) {
        return resources.stream().filter(r -> r.getType() == ResourceType.ARM_TEMPLATE).findFirst()
                .orElseThrow(() -> new CloudConnectorException(String.format("Arm Template not found for: %s  ", stackName)));
    }

    private List<CloudResource> getReattachableVolumeSets(List<CloudResource> resources, List<CloudResource> newInstances) {
        return resources.stream()
                .filter(cloudResource -> ResourceType.AZURE_VOLUMESET.equals(cloudResource.getType()))
                .filter(cloudResource -> CommonStatus.DETACHED.equals(cloudResource.getStatus())
                        || newInstances.stream().anyMatch(inst -> inst.getInstanceId().equals(cloudResource.getInstanceId())))
                .collect(Collectors.toList());
    }
}

package com.sequenceiq.cloudbreak.cloud.openstack.nativ.compute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import javax.inject.Inject;

import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.OS4JException;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.storage.block.Volume;
import org.openstack4j.model.storage.block.Volume.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.cloud.context.AuthenticatedContext;
import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.model.CloudInstance;
import com.sequenceiq.cloudbreak.cloud.model.CloudResource;
import com.sequenceiq.cloudbreak.cloud.model.CloudStack;
import com.sequenceiq.cloudbreak.cloud.model.Group;
import com.sequenceiq.cloudbreak.cloud.model.Image;
import com.sequenceiq.cloudbreak.cloud.model.InstanceTemplate;
import com.sequenceiq.cloudbreak.cloud.openstack.common.OpenStackConstants;
import com.sequenceiq.cloudbreak.cloud.openstack.nativ.OpenStackResourceException;
import com.sequenceiq.cloudbreak.cloud.openstack.nativ.context.OpenStackContext;
import com.sequenceiq.cloudbreak.cloud.openstack.nativ.service.OpenStackResourceNameService;
import com.sequenceiq.cloudbreak.cloud.openstack.view.CinderVolumeView;
import com.sequenceiq.cloudbreak.cloud.openstack.view.NovaInstanceView;
import com.sequenceiq.common.api.type.ResourceType;

@Service
public class OpenStackAttachedDiskResourceBuilder extends AbstractOpenStackComputeResourceBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenStackAttachedDiskResourceBuilder.class);

    private static final String VOLUME_VIEW = "volumeView";

    @Inject
    private OpenStackResourceNameService resourceNameService;

    @Inject
    @Qualifier("intermediateBuilderExecutor")
    private AsyncTaskExecutor intermediateBuilderExecutor;

    @Override
    public List<CloudResource> create(OpenStackContext context, CloudInstance instance, long privateId, AuthenticatedContext auth, Group group, Image image) {
        List<CloudResource> cloudResources = new ArrayList<>();
        InstanceTemplate template = getInstanceTemplate(group, privateId);
        NovaInstanceView instanceView = new NovaInstanceView(context.getName(), template, group.getType(), group.getLoginUserName());
        String groupName = group.getName();
        String stackName = getUtils().getStackName(auth);
        for (int i = 0; i < instanceView.getVolumes().size(); i++) {
            String resourceName = resourceNameService.resourceName(resourceType(), stackName, groupName, privateId, i);
            CloudResource resource = createNamedResource(resourceType(), groupName, resourceName);
            resource.putParameter(VOLUME_VIEW, instanceView.getVolumes().get(i));
            cloudResources.add(resource);
        }
        return cloudResources;
    }

    @Override
    public List<CloudResource> build(OpenStackContext context, CloudInstance instance, long privateId, AuthenticatedContext auth, Group group,
            List<CloudResource> buildableResource, CloudStack cloudStack) throws Exception {
        List<CloudResource> resources = new ArrayList<>();
        List<CloudResource> syncedResources = Collections.synchronizedList(resources);
        Collection<Future<Void>> futures = new ArrayList<>();
        for (CloudResource cloudResource : buildableResource) {
            Future<Void> submit = intermediateBuilderExecutor.submit(() -> {
                CinderVolumeView volumeView = cloudResource.getParameter(VOLUME_VIEW, CinderVolumeView.class);
                Volume osVolume = Builders.volume().name(cloudResource.getName())
                        .size(volumeView.getSize()).build();
                try {
                    OSClient<?> osClient = createOSClient(auth);
                    osVolume = osClient.blockStorage().volumes().create(osVolume);
                    CloudResource newRes = createPersistedResource(cloudResource, group.getName(), osVolume.getId());
                    newRes.putParameter(OpenStackConstants.VOLUME_MOUNT_POINT, volumeView.getDevice());
                    syncedResources.add(newRes);
                } catch (OS4JException ex) {
                    throw new OpenStackResourceException("Volume creation failed", resourceType(), cloudResource.getName(), ex);
                }
                return null;
            });
            futures.add(submit);
        }
        for (Future<Void> future : futures) {
            future.get();
        }
        return resources;
    }

    @Override
    public CloudResource delete(OpenStackContext context, AuthenticatedContext auth, CloudResource resource) {
        try {
            OSClient<?> osClient = createOSClient(auth);
            LOGGER.info("About to delete volume: [{}]", resource);
            ActionResponse response = osClient.blockStorage().volumes().delete(resource.getReference());
            return checkDeleteResponse(response, resourceType(), auth, resource, "Volume deletion failed");
        } catch (OS4JException ex) {
            throw new OpenStackResourceException("Volume deletion failed", resourceType(), resource.getName(), ex);
        }
    }

    @Override
    public ResourceType resourceType() {
        return ResourceType.OPENSTACK_ATTACHED_DISK;
    }

    @Override
    protected boolean checkStatus(OpenStackContext context, AuthenticatedContext auth, CloudResource resource) {
        CloudContext cloudContext = auth.getCloudContext();
        OSClient<?> osClient = createOSClient(auth);
        Volume osVolume = osClient.blockStorage().volumes().get(resource.getReference());
        if (osVolume != null && context.isBuild()) {
            Status volumeStatus = osVolume.getStatus();
            if (Status.ERROR == volumeStatus || Status.ERROR_DELETING == volumeStatus
                    || Status.ERROR_RESTORING == osVolume.getStatus()) {
                throw new OpenStackResourceException("Volume in failed state", resource.getType(), resource.getName(), cloudContext.getId(),
                        volumeStatus.name());
            }
            return volumeStatus == Status.AVAILABLE;
        } else {
            return osVolume == null && !context.isBuild();
        }
    }
}

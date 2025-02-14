package com.sequenceiq.cloudbreak.cloud.azure;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.sequenceiq.cloudbreak.cloud.azure.loadbalancer.AzureLoadBalancer;
import com.sequenceiq.cloudbreak.cloud.azure.loadbalancer.AzureLoadBalancingRule;
import com.sequenceiq.cloudbreak.cloud.model.CloudLoadBalancer;
import com.sequenceiq.cloudbreak.cloud.model.CloudStack;
import com.sequenceiq.cloudbreak.cloud.model.Group;
import com.sequenceiq.cloudbreak.cloud.model.TargetGroupPortPair;
import com.sequenceiq.common.api.type.LoadBalancerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class AzureLoadBalancerModelBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(AzureLoadBalancerModelBuilder.class);

    private final CloudStack cloudStack;

    private final String stackName;

    public AzureLoadBalancerModelBuilder(CloudStack cloudStack, String stackName) {
        this.cloudStack = cloudStack;
        this.stackName = stackName;
    }

    /**
     * Returns a map containing {@code loadBalancers} and {@code loadBalancerMapping} keys.
     * The values in the returned map are based on the Cloud Stack and stack name provided at construction.
     *
     * @return A map containing load balancers and load balancer mappings
     */
    public Map<String, Object> buildModel() {
        List<AzureLoadBalancer> azureLoadBalancers = buildAzureLoadBalancerModel(cloudStack, stackName);
        Map<String, Collection<AzureLoadBalancer>> instanceGroupToLoadBalancers = createTargetInstanceGroupMapping(azureLoadBalancers);

        return Map.of("loadBalancers", azureLoadBalancers,
                "loadBalancerMapping", instanceGroupToLoadBalancers);
    }

    /**
     * Create a model of Azure load balancers from stack information.
     *
     * @param cloudStack containing CloudLoadBalancers to base the AzureLoadBalancers off of
     * @param stackName  part of the load balancer name, usually a stack name
     * @return a list of models representing Azure load balancers
     */
    private List<AzureLoadBalancer> buildAzureLoadBalancerModel(CloudStack cloudStack, String stackName) {
        return cloudStack.getLoadBalancers().stream()
                .map(lb -> convertCloudLoadBalancerToAzureLoadBalancer(lb, stackName))
                .collect(toList());
    }

    private AzureLoadBalancer convertCloudLoadBalancerToAzureLoadBalancer(CloudLoadBalancer cloudLoadBalancer, String stackName) {
        Set<String> instanceGroupNames = collectInstanceGroupNames(cloudLoadBalancer);
        List<AzureLoadBalancingRule> rules = collectLoadBalancingRules(cloudLoadBalancer);
        return buildAzureLb(cloudLoadBalancer.getType(), instanceGroupNames, rules, stackName);
    }

    private Set<String> collectInstanceGroupNames(CloudLoadBalancer cloudLoadBalancer) {
        return cloudLoadBalancer.getPortToTargetGroupMapping().values().stream()
                .flatMap(Collection::stream)
                .map(Group::getName)
                .collect(Collectors.toSet());
    }

    private List<AzureLoadBalancingRule> collectLoadBalancingRules(CloudLoadBalancer cloudLoadBalancer) {
        return cloudLoadBalancer.getPortToTargetGroupMapping()
            .entrySet().stream()
            .map(entry -> {
                TargetGroupPortPair pair = entry.getKey();
                Group firstGroup = entry.getValue().stream().findFirst().orElse(null);
                return new AzureLoadBalancingRule(pair, firstGroup);
            })
            .collect(toList());
    }

    private AzureLoadBalancer buildAzureLb(LoadBalancerType type, Set<String> instanceGroupNames, List<AzureLoadBalancingRule> rules, String stackName) {
        return new AzureLoadBalancer.Builder()
                .setType(type)
                .setInstanceGroupNames(instanceGroupNames)
                .setRules(rules)
                .setStackName(stackName)
                .createAzureLoadBalancer();

    }

    /**
     * Creates a mapping between the instances groups and the load balancers.
     * Each instance group is mapped to a list of all the load balancers that route to it.
     * <p>
     * While this information is available via the AzureLoadBalancer#getInstanceGroupName method, this map is used to simplify the ARM template
     * logic because it shows which load balancers are associated with which instances groups, without having to iterate
     * over the entire list of load balancers in the template.
     *
     * @return A map of instance group names to the load balancers associated with them
     */
    private Map<String, Collection<AzureLoadBalancer>> createTargetInstanceGroupMapping(List<AzureLoadBalancer> azureLoadBalancers) {
        ListMultimap<String, AzureLoadBalancer> mapping = MultimapBuilder.hashKeys().arrayListValues().build();
        for (AzureLoadBalancer lb : azureLoadBalancers) {
            for (String group : lb.getInstanceGroupNames()) {
                mapping.put(group, lb);
            }
        }
        Map<String, Collection<AzureLoadBalancer>> map = mapping.asMap();

        LOGGER.debug("InstanceGroup to LoadBalancer mapping result: {}", map);
        return map;
    }
}


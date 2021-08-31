package com.sequenceiq.cloudbreak.controller;

import java.util.List;

import javax.inject.Inject;


import com.sequenceiq.authorization.annotation.CheckPermissionByAccount;
import com.sequenceiq.authorization.annotation.CheckPermissionByResourceCrn;
import com.sequenceiq.authorization.annotation.CheckPermissionByResourceName;
import com.sequenceiq.authorization.annotation.DisableCheckPermissions;
import com.sequenceiq.authorization.annotation.ResourceCrn;
import com.sequenceiq.authorization.annotation.ResourceName;
import com.sequenceiq.authorization.resource.AuthorizationResourceAction;
import org.springframework.stereotype.Controller;

import com.sequenceiq.cloudbreak.api.endpoint.v4.CustomConfigsV4Endpoint;
import com.sequenceiq.cloudbreak.api.endpoint.v4.requests.CloneCustomConfigsV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.requests.CustomConfigsV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.responses.CustomConfigsV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.responses.CustomConfigsV4Responses;
import com.sequenceiq.cloudbreak.api.endpoint.v4.dto.NameOrCrn;
import com.sequenceiq.cloudbreak.api.util.ConverterUtil;
import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.domain.CustomConfigs;
import com.sequenceiq.cloudbreak.service.CustomConfigsService;

@Controller
public class CustomConfigsV4Controller implements CustomConfigsV4Endpoint {

    @Inject
    private CustomConfigsService customConfigsService;

    @Inject
    private ConverterUtil converterUtil;

    @Override
    @DisableCheckPermissions
    public CustomConfigsV4Responses list() {
        List<CustomConfigs> customConfigsList = customConfigsService.getAll(ThreadBasedUserCrnProvider.getAccountId());
        return new CustomConfigsV4Responses(converterUtil.convertAll(customConfigsList, CustomConfigsV4Response.class));
    }

    @Override
    @CheckPermissionByResourceCrn(action = AuthorizationResourceAction.DESCRIBE_CUSTOM_CONFIGS)
    public CustomConfigsV4Response getByCrn(@ResourceCrn String crn) {
        return converterUtil.convert(customConfigsService.getByNameOrCrn(NameOrCrn.ofCrn(crn)), CustomConfigsV4Response.class);
    }

    @Override
    @CheckPermissionByResourceName(action = AuthorizationResourceAction.DESCRIBE_CUSTOM_CONFIGS)
    public CustomConfigsV4Response getByName(@ResourceName String name) {
        return converterUtil.convert(customConfigsService.getByNameOrCrn(NameOrCrn.ofName(name)), CustomConfigsV4Response.class);
    }

    @Override
    @CheckPermissionByAccount(action = AuthorizationResourceAction.CREATE_CUSTOM_CONFIGS)
    public CustomConfigsV4Response post(CustomConfigsV4Request request) {
        CustomConfigs customConfigs = converterUtil.convert(request, CustomConfigs.class);
        return converterUtil.convert(customConfigsService.create(customConfigs, ThreadBasedUserCrnProvider.getAccountId()), CustomConfigsV4Response.class);
    }

    @Override
    @CheckPermissionByAccount(action = AuthorizationResourceAction.CREATE_CUSTOM_CONFIGS)
    public CustomConfigsV4Response cloneByName(@ResourceName String name, CloneCustomConfigsV4Request request) {
        return converterUtil.convert(customConfigsService.clone(NameOrCrn.ofName(name),
                request.getName(), request.getRuntimeVersion(), ThreadBasedUserCrnProvider.getAccountId()), CustomConfigsV4Response.class);
    }

    @Override
    @CheckPermissionByAccount(action = AuthorizationResourceAction.CREATE_CUSTOM_CONFIGS)
    public CustomConfigsV4Response cloneByCrn(@ResourceCrn String crn, CloneCustomConfigsV4Request request) {
        return converterUtil.convert(customConfigsService.clone(NameOrCrn.ofCrn(crn),
                request.getName(), request.getRuntimeVersion(), ThreadBasedUserCrnProvider.getAccountId()), CustomConfigsV4Response.class);
    }

    @Override
    @CheckPermissionByResourceCrn(action = AuthorizationResourceAction.DELETE_CUSTOM_CONFIGS)
    public CustomConfigsV4Response deleteByCrn(@ResourceCrn String crn) {
        return converterUtil.convert(customConfigsService.deleteByCrn(crn), CustomConfigsV4Response.class);
    }

    @Override
    @CheckPermissionByResourceName(action = AuthorizationResourceAction.DELETE_CUSTOM_CONFIGS)
    public CustomConfigsV4Response deleteByName(@ResourceName String name) {
        return converterUtil.convert(customConfigsService.deleteByName(name, ThreadBasedUserCrnProvider.getAccountId()), CustomConfigsV4Response.class);
    }
}

package com.sequenceiq.cloudbreak.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sequenceiq.authorization.service.model.projection.ResourceCrnAndNameView;
import com.sequenceiq.cloudbreak.domain.CustomConfigs;
import com.sequenceiq.cloudbreak.workspace.repository.EntityType;
import org.springframework.data.repository.query.Param;

@EntityType(entityClass = CustomConfigs.class)
@Transactional(Transactional.TxType.REQUIRED)
public interface CustomConfigsRepository extends JpaRepository<CustomConfigs, Long> {

    @Query("SELECT c FROM CustomConfigs c LEFT JOIN FETCH c.configurations WHERE c.name = :customConfigsName AND c.account = :accountId")
    Optional<CustomConfigs> findByNameAndAccountId(@Param("customConfigsName") String customConfigsName, @Param("accountId") String accountId);

    @Query("SELECT c FROM CustomConfigs c LEFT JOIN FETCH c.configurations WHERE c.crn = :resourceCrn")
    CustomConfigs findByCrn(@Param("resourceCrn") String resourceCrn);

    @Query("SELECT DISTINCT c FROM CustomConfigs c LEFT JOIN FETCH c.configurations")
    List<CustomConfigs> getAllCustomConfigs();

    @Query("SELECT c.crn FROM CustomConfigs c WHERE c.account = :accountId AND c.name IN (:resourceNames)")
    List<String> findResourceCrnsByNamesAndAccountId(@Param("accountId") String accountId, @Param("resourceNames") Collection<String> resourceNames);

    @Query("SELECT c.name FROM CustomConfigs c WHERE c.account = :accountId AND c.crn IN (:resourceCrns)")
    List<ResourceCrnAndNameView> findResourceNamesByCrnsAndAccountId(
            @Param("accountId") String accountId, @Param("resourceCrns") Collection<String> resourceCrns);

    @Query("SELECT DISTINCT c FROM CustomConfigs c LEFT JOIN FETCH c.configurations WHERE c.account = :accountId")
    List<CustomConfigs> findCustomConfigsByAccountId(@Param("accountId") String accountId);

    @Query("SELECT c.crn FROM CustomConfigs c WHERE c.name = :name AND c.account = :accountId")
    Optional<String> findResourceCrnByNameAndAccountId(@Param("name") String name, @Param("accountId") String accountId);

}

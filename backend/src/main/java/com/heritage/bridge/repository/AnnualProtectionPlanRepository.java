package com.heritage.bridge.repository;

import com.heritage.bridge.entity.AnnualProtectionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnnualProtectionPlanRepository extends JpaRepository<AnnualProtectionPlan, Long> {

    List<AnnualProtectionPlan> findByPlanYearOrderByPriorityRankingAsc(Integer planYear);

    List<AnnualProtectionPlan> findByPlanYearAndStatusOrderByPriorityRankingAsc(Integer planYear, String status);

    List<AnnualProtectionPlan> findByBridgeIdOrderByPlanYearDesc(Long bridgeId);

    Optional<AnnualProtectionPlan> findByPlanYearAndBridgeId(Integer planYear, Long bridgeId);

    List<AnnualProtectionPlan> findByPlanYearAndPriorityRankingLessThanEqualOrderByPriorityRankingAsc(
            @Param("planYear") Integer planYear,
            @Param("maxRanking") Integer maxRanking);
}

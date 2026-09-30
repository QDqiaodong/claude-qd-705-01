package com.brewery.craft.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.brewery.craft.domain.BatchRecipeSnapshot;

public interface BatchRecipeSnapshotRepository extends JpaRepository<BatchRecipeSnapshot, Long> {

    Optional<BatchRecipeSnapshot> findByBatchId(Long batchId);

    List<BatchRecipeSnapshot> findAllByOrderByBatchIdAsc();
}

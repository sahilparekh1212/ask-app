package com.askapp.audit.repository;

import com.askapp.audit.model.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {

	/** All flags in a stable order — the SPA reads the whole set once at startup. */
	List<FeatureFlag> findAllByOrderByFlagKeyAsc();
}

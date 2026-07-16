package com.askapp.audit.repository;

import com.askapp.audit.model.SecurityMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SecurityMasterRepository
		extends JpaRepository<SecurityMaster, Long>, JpaSpecificationExecutor<SecurityMaster> {

	/** Point lookup by the unique business key (backs {@code GET /securities/{instrumentId}}). */
	Optional<SecurityMaster> findByInstrumentId(String instrumentId);

	/** True if the business key already exists — lets ingestion skip duplicates idempotently. */
	boolean existsByInstrumentId(String instrumentId);
}

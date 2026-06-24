package com.aisandbox.transaction.repository;

import com.aisandbox.transaction.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

	/** Active (not soft-deleted) transactions. */
	List<Transaction> findByDeletedFalse();
}

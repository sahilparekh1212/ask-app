package com.aisandbox.transaction.controller;

import com.aisandbox.transaction.exception.ResourceNotFoundException;
import com.aisandbox.transaction.model.Transaction;
import com.aisandbox.transaction.ratelimit.TransactionalRequestExecutor;
import com.aisandbox.transaction.repository.TransactionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Transaction CRUD operations")
public class TransactionController {

	private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

	private final TransactionRepository transactionRepository;
	// Wraps mutations so a superseding request rolls the work back transactionally.
	private final TransactionalRequestExecutor txExecutor;

	public TransactionController(TransactionRepository transactionRepository, TransactionalRequestExecutor txExecutor) {
		this.transactionRepository = transactionRepository;
		this.txExecutor = txExecutor;
	}

	@GetMapping
	@Operation(summary = "List transactions (excludes soft-deleted unless includeDeleted=true)")
	public List<Transaction> findAll(@RequestParam(defaultValue = "false") boolean includeDeleted) {
		log.info("Fetching transactions includeDeleted={}", includeDeleted);
		return includeDeleted ? transactionRepository.findAll() : transactionRepository.findByDeletedFalse();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get transaction by ID")
	public Transaction findById(@PathVariable Long id) {
		log.info("Fetching transaction id={}", id);
		return transactionRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a new transaction")
	public Transaction create(@RequestBody Transaction transaction) {
		Transaction saved = txExecutor.run(() -> transactionRepository.save(transaction));
		log.info("Created transaction id={}", saved.getId());
		return saved;
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update an existing transaction")
	public Transaction update(@PathVariable Long id, @RequestBody Transaction payload) {
		Transaction saved = txExecutor.run(() -> {
			Transaction transaction = transactionRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
			transaction.setAccountId(payload.getAccountId());
			transaction.setAmount(payload.getAmount());
			transaction.setType(payload.getType());
			return transactionRepository.save(transaction);
		});
		log.info("Updated transaction id={}", id);
		return saved;
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a transaction")
	public void delete(@PathVariable Long id) {
		txExecutor.run(() -> {
			Transaction transaction = transactionRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
			transaction.setDeleted(true);
			transactionRepository.save(transaction);
			return null;
		});
		log.info("Soft-deleted transaction id={}", id);
	}

	@GetMapping("/health")
	@Operation(summary = "Health check")
	public ResponseEntity<String> health() {
		return ResponseEntity.ok("transaction-service OK");
	}

}

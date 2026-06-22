package com.aisandbox.transaction.controller;

import com.aisandbox.transaction.model.Transaction;
import com.aisandbox.transaction.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

	private final TransactionRepository transactionRepository;

	public TransactionController(TransactionRepository transactionRepository) {
		this.transactionRepository = transactionRepository;
	}

	@GetMapping
	public List<Transaction> findAll() {
		return transactionRepository.findAll();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Transaction create(@RequestBody Transaction transaction) {
		return transactionRepository.save(transaction);
	}

}

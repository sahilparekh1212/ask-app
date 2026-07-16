package com.askapp.audit.service;

import com.askapp.audit.model.SecurityMaster;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefDataGeneratorTest {

	private final RefDataGenerator generator = new RefDataGenerator();

	@Test
	void generate_producesDeterministicRecordsWithReferenceDataShape() {
		List<SecurityMaster> records = generator.generate(4);

		assertThat(records).extracting(SecurityMaster::getInstrumentId)
			.containsExactly("SEC-000000", "SEC-000001", "SEC-000002", "SEC-000003");
		assertThat(records).extracting(SecurityMaster::getAssetClass)
			.containsExactly("EQUITY", "BOND", "ETF", "FX");

		SecurityMaster first = records.get(0);
		assertThat(first.getIsin()).isEqualTo("US0000000000");
		assertThat(first.getCusip()).isEqualTo("CUSIP0000");
		assertThat(first.getSedol()).isEqualTo("SED0000");
		assertThat(first.getCurrency()).isEqualTo("USD");
		assertThat(first.getName()).isEqualTo("Synthetic EQUITY SEC-000000");
		assertThat(first.getPrice()).isEqualByComparingTo("10.00");
		assertThat(first.getAsOfDate()).isNotNull();
	}

	@Test
	void generate_zeroReturnsNoRecords() {
		assertThat(generator.generate(0)).isEmpty();
	}
}

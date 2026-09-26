package com.ethioloadai.carrier.company;

import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Executes the actual V57 SQL, including constraints which Hibernate-generated test DDL omits. */
class CarrierCompanyMigrationTest {
    JdbcTemplate db;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:carrier-migration-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(source);
        db.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        db.update("INSERT INTO users(id) VALUES (1), (2)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V57__create_carrier_companies.sql"))
            .execute(source);
    }

    @AfterEach void cleanup() { db.execute("DROP ALL OBJECTS"); }

    @Test void migrationCreatesDraftAndAllowsMultipleUnidentifiedDrafts() {
        db.update("INSERT INTO carrier_companies(company_name, owner_id) VALUES ('One', 1), ('Two', 1)");
        assertThat(db.queryForObject("SELECT count(*) FROM carrier_companies WHERE verification_status = 'DRAFT'", Integer.class))
            .isEqualTo(2);
    }

    @Test void legalIdentifiersAreUniqueAndCanonical() {
        db.update("INSERT INTO carrier_companies(company_name, owner_id, tin_number, business_registration_number) VALUES ('One', 1, 'TIN1', 'REG1')");
        assertInvalid("INSERT INTO carrier_companies(company_name, owner_id, tin_number) VALUES ('Two', 2, 'TIN1')");
        assertInvalid("INSERT INTO carrier_companies(company_name, owner_id, business_registration_number) VALUES ('Two', 2, 'REG1')");
        assertInvalid("INSERT INTO carrier_companies(company_name, owner_id, tin_number) VALUES ('Two', 2, 'tin1')");
    }

    @Test void ownershipReviewAndStateConstraintsRejectInvalidRecords() {
        assertInvalid("INSERT INTO carrier_companies(company_name, owner_id) VALUES ('One', 999)");
        assertInvalid("INSERT INTO carrier_companies(company_name, owner_id, verification_status) VALUES ('One', 1, 'APPROVED')");
        assertInvalid("INSERT INTO carrier_companies(company_name, owner_id, verification_status) VALUES ('One', 1, 'ARBITRARY')");
        assertInvalid("INSERT INTO carrier_companies(company_name, owner_id, verification_status, submitted_at, reviewed_by, reviewed_at) VALUES ('One', 1, 'REJECTED', CURRENT_TIMESTAMP, 2, CURRENT_TIMESTAMP)");
        assertInvalid("INSERT INTO carrier_companies(company_name, owner_id, verification_status, submitted_at, reviewed_by, reviewed_at) VALUES ('One', 1, 'APPROVED', CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP)");
        assertInvalid("INSERT INTO carrier_companies(company_name, owner_id, verification_status, submitted_at, reviewed_by, reviewed_at) VALUES ('One', 1, 'APPROVED', CURRENT_TIMESTAMP, 999, CURRENT_TIMESTAMP)");
    }

    @Test void documentForeignKeysAndUniqueCompanyTypeProtectAssociations() {
        db.update("INSERT INTO carrier_companies(id, company_name, owner_id) VALUES (1, 'One', 1)");
        String prefix = "INSERT INTO carrier_company_documents(carrier_company_id, document_type, storage_key, original_name, content_type, file_size) VALUES ";
        db.update(prefix + "(1, 'TIN_CERTIFICATE', 'company/1/a.pdf', 'a.pdf', 'application/pdf', 100)");
        assertInvalid(prefix + "(1, 'TIN_CERTIFICATE', 'company/1/b.pdf', 'b.pdf', 'application/pdf', 100)");
        assertInvalid(prefix + "(999, 'BUSINESS_LICENSE', 'company/999/a.pdf', 'a.pdf', 'application/pdf', 100)");
        assertInvalid(prefix + "(1, 'OTHER', 'company/1/a.pdf', 'a.pdf', 'application/pdf', 100)");
        assertInvalid(prefix + "(1, 'OTHER', 'company/1/b.pdf', 'a.pdf', 'text/html', 100)");
        assertInvalid("DELETE FROM carrier_companies WHERE id = 1");
        assertInvalid("DELETE FROM users WHERE id = 1");
    }

    void assertInvalid(String sql) {
        assertThatThrownBy(() -> db.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
    }
}

package com.ethioloadai.carrier.team;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CompanyOwnerMigrationTest {
 JdbcTemplate db;
 DriverManagerDataSource source;
 @BeforeEach void setup() {
  source=new DriverManagerDataSource("jdbc:h2:mem:team-migration-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
  db=new JdbcTemplate(source);
  db.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
  db.execute("CREATE TABLE carrier_companies(id BIGINT PRIMARY KEY, owner_id BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
  db.update("INSERT INTO users VALUES(1),(2)");
  db.update("INSERT INTO carrier_companies(id,owner_id) VALUES(1,1),(2,1)");
  migrate("V58__create_company_memberships.sql");
  migrate("V59__backfill_company_owner_memberships.sql");
 }
 void migrate(String file) { new ResourceDatabasePopulator(new ClassPathResource("db/migration/"+file)).execute(source); }
 @AfterEach void cleanup() { db.execute("DROP ALL OBJECTS"); }
 @Test void allExistingOwnersBackfilled() {
  assertThat(db.queryForObject("select count(*) from company_memberships where user_id=1 and role='CARRIER_OWNER' and status='ACTIVE'",Integer.class)).isEqualTo(2);
 }
 @Test void ownerBackfillIsIdempotent() {
  migrate("V59__backfill_company_owner_memberships.sql");
  assertThat(db.queryForObject("select count(*) from company_memberships",Integer.class)).isEqualTo(2);
 }
 @Test void membershipUniquenessAndForeignKeysAreEnforced() {
  invalid("INSERT INTO company_memberships(carrier_company_id,user_id,role,status,joined_at) VALUES(1,1,'DISPATCHER','ACTIVE',CURRENT_TIMESTAMP)");
  invalid("INSERT INTO company_memberships(carrier_company_id,user_id,role,status,joined_at) VALUES(99,2,'DISPATCHER','ACTIVE',CURRENT_TIMESTAMP)");
  invalid("INSERT INTO company_memberships(carrier_company_id,user_id,role,status,joined_at) VALUES(1,99,'DISPATCHER','ACTIVE',CURRENT_TIMESTAMP)");
 }
 @Test void roleStatusAndOwnerChecksAreEnforced() {
  invalid("UPDATE company_memberships SET role='ADMIN'");
  invalid("UPDATE company_memberships SET status='PENDING'");
  invalid("UPDATE company_memberships SET status='INACTIVE'");
 }
 void invalid(String sql) { assertThatThrownBy(()->db.update(sql)).isInstanceOf(DataIntegrityViolationException.class); }
}


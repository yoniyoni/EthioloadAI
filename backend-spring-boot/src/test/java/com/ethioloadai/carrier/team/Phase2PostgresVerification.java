package com.ethioloadai.carrier.team;

import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Explicitly run by verify-carrier-phase2.ps1; no skipped tests in the default H2 suite. */
class Phase2PostgresVerification {
 SingleConnectionDataSource source;
 JdbcTemplate db;
 String schema;
 @BeforeEach void setup() throws Exception {
  String url=System.getenv("SPRING_DATASOURCE_URL");
  assertThat(url != null && url.startsWith("jdbc:postgresql:")).isTrue();
  source=new SingleConnectionDataSource(DriverManager.getConnection(url,System.getenv("SPRING_DATASOURCE_USERNAME"),
    System.getenv("SPRING_DATASOURCE_PASSWORD")),true);
  db=new JdbcTemplate(source);
  schema="phase2_check_"+UUID.randomUUID().toString().replace("-","");
  db.execute("CREATE SCHEMA "+schema);
  db.execute("SET search_path TO "+schema);
  db.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
  db.update("INSERT INTO users VALUES(1),(2),(3)");
  migrate("V57__create_carrier_companies.sql");
  db.update("INSERT INTO carrier_companies(id,company_name,owner_id) VALUES(1,'Migration owner',1)");
  migrate("V58__create_company_memberships.sql");
  migrate("V59__backfill_company_owner_memberships.sql");
  migrate("V60__create_company_invitations.sql");
 }
 @AfterEach void cleanup() {
  if(db!=null && schema!=null && schema.matches("phase2_check_[a-f0-9]{32}")) db.execute("DROP SCHEMA "+schema+" CASCADE");
  if(source!=null) source.destroy();
 }
 void migrate(String file) { new ResourceDatabasePopulator(new ClassPathResource("db/migration/"+file)).execute(source); }
 @Test void postgresOwnerBackfillIsCompleteAndIdempotent() {
  migrate("V59__backfill_company_owner_memberships.sql");
  assertThat(db.queryForObject("SELECT count(*) FROM company_memberships WHERE role='CARRIER_OWNER' AND status='ACTIVE'",Integer.class)).isEqualTo(1);
 }
 @Test void postgresMembershipUniquenessForeignKeysAndEnums() {
  invalid("INSERT INTO company_memberships(carrier_company_id,user_id,role,status,joined_at) VALUES(1,1,'DISPATCHER','ACTIVE',CURRENT_TIMESTAMP)");
  invalid("INSERT INTO company_memberships(carrier_company_id,user_id,role,status,joined_at) VALUES(99,2,'DISPATCHER','ACTIVE',CURRENT_TIMESTAMP)");
  invalid("INSERT INTO company_memberships(carrier_company_id,user_id,role,status,joined_at) VALUES(1,99,'DISPATCHER','ACTIVE',CURRENT_TIMESTAMP)");
  invalid("UPDATE company_memberships SET status='PENDING'");
  invalid("UPDATE company_memberships SET role='ADMIN'");
  invalid("UPDATE company_memberships SET status='REMOVED'");
 }
 String insert(String email,String hash) {
  return "INSERT INTO company_invitations(carrier_company_id,invited_email,invited_role,token_hash,status,invited_by,expires_at) VALUES(1,'"
   +email+"','DISPATCHER','"+hash+"','PENDING',1,CURRENT_TIMESTAMP+INTERVAL '1 day')";
 }
 @Test void postgresOnlyOnePendingEmailButHistoricalInvitationsAreRetained() {
  db.update(insert("employee@example.test","a".repeat(64)));
  invalid(insert("employee@example.test","b".repeat(64)));
  db.update("UPDATE company_invitations SET status='REVOKED',revoked_at=CURRENT_TIMESTAMP,revoked_by=1");
  db.update(insert("employee@example.test","b".repeat(64)));
  assertThat(db.queryForObject("SELECT count(*) FROM company_invitations",Integer.class)).isEqualTo(2);
 }
 @Test void postgresTokenUniquenessAndEmailNormalization() {
  db.update(insert("employee@example.test","a".repeat(64)));
  invalid(insert("another@example.test","a".repeat(64)));
  invalid(insert("Employee@Example.test","b".repeat(64)));
  invalid(insert(" employee@example.test ","c".repeat(64)));
  invalid(insert("other@example.test","not-a-hash"));
 }
 @Test void postgresInvitationReferencesAndDecisionConstraints() {
  db.update(insert("employee@example.test","a".repeat(64)));
  invalid("UPDATE company_invitations SET carrier_company_id=99");
  invalid("UPDATE company_invitations SET invited_by=99");
  invalid("UPDATE company_invitations SET invited_role='CARRIER_OWNER'");
  invalid("UPDATE company_invitations SET status='ACTIVE'");
  invalid("UPDATE company_invitations SET status='ACCEPTED'");
  invalid("UPDATE company_invitations SET status='REVOKED'");
  invalid("UPDATE company_invitations SET expires_at=created_at");
 }
 void invalid(String sql) { assertThatThrownBy(()->db.update(sql)).isInstanceOf(DataIntegrityViolationException.class); }

 @Test void postgresCompanyIdentityLifecycleAndOwnerConstraints() {
  db.update("UPDATE carrier_companies SET tin_number='TIN-ONE',business_registration_number='REG-ONE' WHERE id=1");
  invalid("INSERT INTO carrier_companies(id,company_name,owner_id,tin_number) VALUES(2,'Duplicate',2,'TIN-ONE')");
  invalid("INSERT INTO carrier_companies(id,company_name,owner_id,business_registration_number) VALUES(3,'Duplicate',2,'REG-ONE')");
  invalid("UPDATE carrier_companies SET owner_id=999");
  invalid("UPDATE carrier_companies SET tin_number='lowercase'");
  invalid("UPDATE carrier_companies SET business_registration_number=' whitespace '");
  invalid("UPDATE carrier_companies SET verification_status='UNKNOWN'");
  invalid("UPDATE carrier_companies SET verification_status='PENDING'");
  invalid("UPDATE carrier_companies SET verification_status='APPROVED',submitted_at=CURRENT_TIMESTAMP,reviewed_at=CURRENT_TIMESTAMP,reviewed_by=1");
  invalid("UPDATE carrier_companies SET verification_status='REJECTED',submitted_at=CURRENT_TIMESTAMP,reviewed_at=CURRENT_TIMESTAMP,reviewed_by=2");
  assertThat(db.queryForObject("SELECT verification_status FROM carrier_companies WHERE id=1",String.class)).isEqualTo("DRAFT");
 }

 @Test void postgresDocumentPrivacyMetadataAndRelationshipConstraints() {
  db.update("INSERT INTO carrier_company_documents(carrier_company_id,document_type,storage_key,original_name,content_type,file_size) VALUES(1,'BUSINESS_LICENSE','private/test.pdf','document.pdf','application/pdf',20)");
  invalid("UPDATE carrier_company_documents SET carrier_company_id=999");
  invalid("UPDATE carrier_company_documents SET document_type='UNKNOWN'");
  invalid("UPDATE carrier_company_documents SET content_type='text/html'");
  invalid("UPDATE carrier_company_documents SET file_size=0");
  invalid("UPDATE carrier_company_documents SET file_size=5242881");
  invalid("UPDATE carrier_company_documents SET verification_status='APPROVED'");
  invalid("UPDATE carrier_company_documents SET reviewed_by=999");
  invalid("INSERT INTO carrier_company_documents(carrier_company_id,document_type,storage_key,original_name,content_type,file_size) VALUES(1,'BUSINESS_LICENSE','private/other.pdf','document.pdf','application/pdf',20)");
  invalid("INSERT INTO carrier_company_documents(carrier_company_id,document_type,storage_key,original_name,content_type,file_size) VALUES(1,'OTHER','private/test.pdf','document.pdf','application/pdf',20)");
  assertThat(db.queryForObject("SELECT count(*) FROM carrier_company_documents",Integer.class)).isEqualTo(1);
 }

 @Test void postgresExpectedIndexesAndForeignKeysExist() {
  assertThat(db.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname=current_schema()",String.class))
   .contains("uq_carrier_tin","uq_carrier_registration","uq_carrier_document_type","uq_company_membership",
    "uq_company_pending_invitation","idx_carrier_owner_created","idx_carrier_status_created",
    "idx_membership_user","idx_membership_company_role_status","idx_invitation_company_created",
    "idx_invitation_email","idx_invitation_status_expiry");
  assertThat(db.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND contype='f'",Integer.class)).isEqualTo(13);
  assertThat(db.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND NOT convalidated",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname=current_schema() AND indexname='uq_company_pending_invitation'",String.class))
   .contains("UNIQUE", "WHERE", "PENDING");
 }
}

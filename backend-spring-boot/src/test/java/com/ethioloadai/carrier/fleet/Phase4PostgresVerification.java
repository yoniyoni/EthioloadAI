package com.ethioloadai.carrier.fleet;

import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Phase 4 PostgreSQL constraint and schema verification.
 * Runs against a real PostgreSQL instance (requires SPRING_DATASOURCE_* env vars).
 * Verifies V61–V63 migrations, FK constraints, partial unique indexes, and backfill.
 */
class Phase4PostgresVerification {

    SingleConnectionDataSource source;
    JdbcTemplate db;
    String schema;

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        assertThat(url).as("SPRING_DATASOURCE_URL must be set").startsWith("jdbc:postgresql:");

        source = new SingleConnectionDataSource(
                DriverManager.getConnection(url,
                        System.getenv("SPRING_DATASOURCE_USERNAME"),
                        System.getenv("SPRING_DATASOURCE_PASSWORD")),
                true);
        db = new JdbcTemplate(source);

        schema = "phase4_v_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        db.execute("CREATE SCHEMA " + schema);
        db.execute("SET search_path TO " + schema);

        // Bootstrap minimal tables needed by Phase 4 migrations
        db.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, role VARCHAR(20))");
        db.execute("INSERT INTO users VALUES(1,'SHIPPER'),(2,'DRIVER'),(3,'ADMIN'),(4,'FLEET_OWNER'),(5,'DRIVER')");

        db.execute("CREATE TABLE carrier_companies(id BIGINT PRIMARY KEY, company_name VARCHAR(255), owner_id BIGINT REFERENCES users(id))");
        db.execute("INSERT INTO carrier_companies VALUES(1,'ABC Logistics',1),(2,'XYZ Transport',2)");

        db.execute("CREATE TABLE company_memberships(id BIGSERIAL PRIMARY KEY, carrier_company_id BIGINT REFERENCES carrier_companies(id), user_id BIGINT REFERENCES users(id), role VARCHAR(30), status VARCHAR(20))");
        db.execute("INSERT INTO company_memberships(carrier_company_id,user_id,role,status) VALUES(1,1,'CARRIER_OWNER','ACTIVE')");
        db.execute("INSERT INTO company_memberships(carrier_company_id,user_id,role,status) VALUES(2,2,'CARRIER_OWNER','ACTIVE')");

        db.execute("CREATE TABLE vehicles(id BIGSERIAL PRIMARY KEY, user_id BIGINT, fleet_owner_id BIGINT, plate_number VARCHAR(255) UNIQUE, capacity DOUBLE PRECISION, availability_status VARCHAR(50) DEFAULT 'available', approval_status VARCHAR(20) DEFAULT 'UNDER_REVIEW')");

        migrate("V61__add_carrier_company_to_vehicles.sql");
        migrate("V62__create_carrier_drivers.sql");
        migrate("V63__create_vehicle_driver_assignments.sql");
    }

    @AfterEach
    void cleanup() {
        if (db != null && schema != null) {
            try { db.execute("DROP SCHEMA " + schema + " CASCADE"); } catch (Exception ignored) {}
        }
        if (source != null) source.destroy();
    }

    void migrate(String file) {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/" + file)).execute(source);
    }

    // ── V61 — carrier_company_id on vehicles ──────────────────────────────────

    @Test
    void v61_carrierCompanyIdColumnExists() {
        db.execute("INSERT INTO vehicles(user_id, fleet_owner_id, plate_number, capacity, carrier_company_id) VALUES(1,1,'ABC-001',10,1)");
        int count = db.queryForObject(
            "SELECT count(*) FROM vehicles WHERE carrier_company_id = 1", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v61_carrierCompanyIdFkPreventsInvalidCompany() {
        assertThatThrownBy(() ->
            db.execute("INSERT INTO vehicles(user_id,fleet_owner_id,plate_number,capacity,carrier_company_id) VALUES(1,1,'FK-FAIL',10,999)"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v61_carrierCompanyIdIsNullable() {
        // Vehicles without a company (legacy/independent) are still valid
        db.execute("INSERT INTO vehicles(user_id,fleet_owner_id,plate_number,capacity) VALUES(1,1,'NO-COMPANY',5)");
        Integer count = db.queryForObject(
            "SELECT count(*) FROM vehicles WHERE carrier_company_id IS NULL", Integer.class);
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void v61_indexExistsOnCarrierCompanyId() {
        List<String> indexes = db.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema()", String.class);
        assertThat(indexes).contains("idx_vehicles_carrier_company");
    }

    // ── V62 — carrier_drivers ──────────────────────────────────────────────────

    @Test
    void v62_canInsertCarrierDriver() {
        db.update("INSERT INTO carrier_drivers(carrier_company_id,driver_user_id,status,availability) VALUES(1,2,'ACTIVE','AVAILABLE')");
        int count = db.queryForObject(
            "SELECT count(*) FROM carrier_drivers WHERE carrier_company_id=1 AND driver_user_id=2", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v62_uniqueConstraintPreventsDoubleJoin() {
        db.update("INSERT INTO carrier_drivers(carrier_company_id,driver_user_id,status,availability) VALUES(1,2,'ACTIVE','AVAILABLE')");
        assertThatThrownBy(() ->
            db.update("INSERT INTO carrier_drivers(carrier_company_id,driver_user_id,status,availability) VALUES(1,2,'INACTIVE','UNAVAILABLE')"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v62_fkPreventsInvalidCompany() {
        assertThatThrownBy(() ->
            db.update("INSERT INTO carrier_drivers(carrier_company_id,driver_user_id,status,availability) VALUES(999,2,'ACTIVE','AVAILABLE')"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v62_fkPreventsInvalidUser() {
        assertThatThrownBy(() ->
            db.update("INSERT INTO carrier_drivers(carrier_company_id,driver_user_id,status,availability) VALUES(1,999,'ACTIVE','AVAILABLE')"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v62_statusCheckConstraint() {
        assertThatThrownBy(() ->
            db.update("INSERT INTO carrier_drivers(carrier_company_id,driver_user_id,status,availability) VALUES(1,2,'NONEXISTENT','AVAILABLE')"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v62_availabilityCheckConstraint() {
        assertThatThrownBy(() ->
            db.update("INSERT INTO carrier_drivers(carrier_company_id,driver_user_id,status,availability) VALUES(1,2,'ACTIVE','FLYING')"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v62_activeDriverMustNotHaveLeftAt() {
        // ACTIVE with left_at should fail ck_carrier_driver_left_at
        assertThatThrownBy(() ->
            db.update("INSERT INTO carrier_drivers(carrier_company_id,driver_user_id,status,availability,left_at) VALUES(1,2,'ACTIVE','AVAILABLE',CURRENT_TIMESTAMP)"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v62_requiredIndexesExist() {
        List<String> indexes = db.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema()", String.class);
        assertThat(indexes).contains(
            "uq_carrier_driver_active",
            "idx_carrier_driver_company",
            "idx_carrier_driver_user",
            "idx_carrier_driver_avail"
        );
    }

    // ── V63 — vehicle_driver_assignments ──────────────────────────────────────

    @Test
    void v63_canInsertAssignment() {
        db.update("INSERT INTO carrier_drivers(id,carrier_company_id,driver_user_id,status,availability) VALUES(100,1,2,'ACTIVE','AVAILABLE')");
        db.update("INSERT INTO vehicles(id,user_id,fleet_owner_id,plate_number,capacity,carrier_company_id) VALUES(200,1,1,'ASGN-V1',10,1)");
        db.update("INSERT INTO vehicle_driver_assignments(carrier_company_id,carrier_driver_id,vehicle_id,status,assigned_by) VALUES(1,100,200,'ACTIVE',1)");
        int count = db.queryForObject("SELECT count(*) FROM vehicle_driver_assignments", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v63_partialUniqueIndexPreventsDoubleActiveDriverAssignment() {
        db.update("INSERT INTO carrier_drivers(id,carrier_company_id,driver_user_id,status,availability) VALUES(101,1,2,'ACTIVE','AVAILABLE')");
        db.update("INSERT INTO vehicles(id,user_id,fleet_owner_id,plate_number,capacity,carrier_company_id) VALUES(201,1,1,'ASGN-V2',10,1)");
        db.update("INSERT INTO vehicles(id,user_id,fleet_owner_id,plate_number,capacity,carrier_company_id) VALUES(202,1,1,'ASGN-V3',10,1)");
        db.update("INSERT INTO vehicle_driver_assignments(carrier_company_id,carrier_driver_id,vehicle_id,status,assigned_by) VALUES(1,101,201,'ACTIVE',1)");
        assertThatThrownBy(() ->
            db.update("INSERT INTO vehicle_driver_assignments(carrier_company_id,carrier_driver_id,vehicle_id,status,assigned_by) VALUES(1,101,202,'ACTIVE',1)"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v63_partialUniqueIndexPreventsDoubleActiveVehicleAssignment() {
        db.update("INSERT INTO carrier_drivers(id,carrier_company_id,driver_user_id,status,availability) VALUES(102,1,2,'ACTIVE','AVAILABLE')");
        db.update("INSERT INTO carrier_drivers(id,carrier_company_id,driver_user_id,status,availability) VALUES(103,1,5,'ACTIVE','AVAILABLE')");
        db.update("INSERT INTO vehicles(id,user_id,fleet_owner_id,plate_number,capacity,carrier_company_id) VALUES(203,1,1,'ASGN-V4',10,1)");
        db.update("INSERT INTO vehicle_driver_assignments(carrier_company_id,carrier_driver_id,vehicle_id,status,assigned_by) VALUES(1,102,203,'ACTIVE',1)");
        assertThatThrownBy(() ->
            db.update("INSERT INTO vehicle_driver_assignments(carrier_company_id,carrier_driver_id,vehicle_id,status,assigned_by) VALUES(1,103,203,'ACTIVE',1)"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v63_endedAssignmentsNotSubjectToUniqueIndex() {
        // Two ENDED assignments for the same driver are allowed (history)
        db.update("INSERT INTO carrier_drivers(id,carrier_company_id,driver_user_id,status,availability) VALUES(104,1,2,'ACTIVE','AVAILABLE')");
        db.update("INSERT INTO vehicles(id,user_id,fleet_owner_id,plate_number,capacity,carrier_company_id) VALUES(204,1,1,'HIST-V1',10,1)");
        db.update("INSERT INTO vehicles(id,user_id,fleet_owner_id,plate_number,capacity,carrier_company_id) VALUES(205,1,1,'HIST-V2',10,1)");
        db.update("INSERT INTO vehicle_driver_assignments(carrier_company_id,carrier_driver_id,vehicle_id,status,assigned_by,ended_at) VALUES(1,104,204,'ENDED',1,CURRENT_TIMESTAMP)");
        db.update("INSERT INTO vehicle_driver_assignments(carrier_company_id,carrier_driver_id,vehicle_id,status,assigned_by,ended_at) VALUES(1,104,205,'ENDED',1,CURRENT_TIMESTAMP)");
        int count = db.queryForObject(
            "SELECT count(*) FROM vehicle_driver_assignments WHERE carrier_driver_id=104 AND status='ENDED'",
            Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void v63_activeAssignmentMustNotHaveEndedAt() {
        db.update("INSERT INTO carrier_drivers(id,carrier_company_id,driver_user_id,status,availability) VALUES(105,1,2,'ACTIVE','AVAILABLE')");
        db.update("INSERT INTO vehicles(id,user_id,fleet_owner_id,plate_number,capacity,carrier_company_id) VALUES(206,1,1,'ASGN-CK',10,1)");
        assertThatThrownBy(() ->
            db.update("INSERT INTO vehicle_driver_assignments(carrier_company_id,carrier_driver_id,vehicle_id,status,assigned_by,ended_at) VALUES(1,105,206,'ACTIVE',1,CURRENT_TIMESTAMP)"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v63_endedAssignmentMustHaveEndedAt() {
        db.update("INSERT INTO carrier_drivers(id,carrier_company_id,driver_user_id,status,availability) VALUES(106,1,2,'ACTIVE','AVAILABLE')");
        db.update("INSERT INTO vehicles(id,user_id,fleet_owner_id,plate_number,capacity,carrier_company_id) VALUES(207,1,1,'ASGN-ENK',10,1)");
        assertThatThrownBy(() ->
            db.update("INSERT INTO vehicle_driver_assignments(carrier_company_id,carrier_driver_id,vehicle_id,status,assigned_by) VALUES(1,106,207,'ENDED',1)"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v63_requiredIndexesExist() {
        List<String> indexes = db.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema()", String.class);
        assertThat(indexes).contains(
            "uq_active_driver_assignment",
            "uq_active_vehicle_assignment",
            "idx_assignment_company",
            "idx_assignment_vehicle",
            "idx_assignment_driver"
        );
    }

    @Test
    void noOrphanCarrierDriverRows() {
        int orphans = db.queryForObject(
            "SELECT count(*) FROM carrier_drivers d WHERE NOT EXISTS(SELECT 1 FROM carrier_companies c WHERE c.id=d.carrier_company_id)",
            Integer.class);
        assertThat(orphans).isZero();
    }

    @Test
    void noOrphanAssignmentRows() {
        int orphans = db.queryForObject(
            "SELECT count(*) FROM vehicle_driver_assignments a WHERE NOT EXISTS(SELECT 1 FROM carrier_companies c WHERE c.id=a.carrier_company_id)",
            Integer.class);
        assertThat(orphans).isZero();
    }
}

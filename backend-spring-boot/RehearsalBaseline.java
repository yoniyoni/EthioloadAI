import org.flywaydb.core.Flyway;

public class RehearsalBaseline {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://127.0.0.1:5433/flyway_rehearsal";
        String user = "postgres";
        String password = System.getenv("PGPASSWORD");

        if (password == null || password.isBlank()) {
            throw new IllegalStateException("PGPASSWORD is not set");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .table("flyway_rehearsal_history")
                .baselineVersion("53")
                .baselineDescription("Rehearsal baseline at V53")
                .load();

        flyway.baseline();

        System.out.println("BASELINE COMPLETED");
    }
}

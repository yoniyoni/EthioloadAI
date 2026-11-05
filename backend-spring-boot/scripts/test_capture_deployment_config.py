import unittest
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ROOT / "docker" / "docker-compose.e2e-mail-capture.yml"
DOCKERFILE = ROOT / "docker" / "Dockerfile.e2e-mail-capture"
FORWARDER_DOCKERFILE = ROOT / "docker" / "Dockerfile.e2e-tcp-forwarder"
FORWARDER_SOURCE = ROOT / "docker" / "e2e-tcp-forwarder" / "main.go"
HOST_FORWARDER = Path(__file__).with_name("e2e-host-forwarder.mjs")
CAPTURE_SERVER = Path(__file__).with_name("capture-invitation-smtp.py")
VITE_CONFIG = ROOT / "frontend" / "artifacts" / "freight-link" / "vite.config.ts"
RUNBOOK = ROOT / "backend-spring-boot" / "E2E_MAIL_CAPTURE.md"


class CaptureDeploymentConfigTest(unittest.TestCase):
    def test_capture_service_has_no_published_ports_or_non_e2e_network(self):
        config = COMPOSE.read_text(encoding="utf-8")
        capture_service = config.split("  backend:\n", maxsplit=1)[0]
        self.assertIn("container_name: ethioloadai-e2e-mail-capture", capture_service)
        self.assertNotIn("\n    ports:", capture_service)
        self.assertIn('CAPTURE_SMTP_BIND: 0.0.0.0', capture_service)
        self.assertNotIn("CAPTURE_API_BIND", capture_service)
        self.assertIn('      - "12525"', capture_service)
        self.assertIn("      - test-mail-capture-key", capture_service)
        self.assertIn("read_only: true", capture_service)
        self.assertIn("pids_limit: 32", capture_service)
        self.assertIn("no-new-privileges:true", capture_service)
        self.assertIn("ethioloadai-e2e-mail-capture", capture_service)
        for provider_setting in ("SMTP_USER", "SMTP_PASSWORD", "BREVO", "SMTP_HOST"):
            self.assertNotIn(provider_setting, capture_service)
        self.assertNotIn("application", capture_service.lower())
        self.assertRegex(
            capture_service,
            re.compile(
                r"(?m)^    networks:\n"
                r"      e2e:\n"
                r"        ipv4_address: 172\.26\.0\.4\n"
                r"        aliases:\n"
                r"          - ethioloadai-e2e-mail-capture\n"
                r"    healthcheck:$"
            ),
        )
        self.assertNotIn("\n    expose:\n      - \"12526\"", capture_service)

    def test_healthcheck_authenticates_http_cursor_and_uses_mounted_secret(self):
        config = COMPOSE.read_text(encoding="utf-8")
        capture_service = config.split("  backend:\n", maxsplit=1)[0]
        healthcheck = capture_service.split("    healthcheck:\n", maxsplit=1)[1]
        self.assertIn("/run/secrets/test-mail-capture-key", healthcheck)
        self.assertIn("http://127.0.0.1:12526/cursor", healthcheck)
        self.assertIn("X-Test-Key", healthcheck)
        self.assertNotIn("12525", healthcheck)
        self.assertNotIn("print(", healthcheck)
        source = CAPTURE_SERVER.read_text(encoding="utf-8")
        self.assertIn('CaptureAPIServer(("127.0.0.1", api_port)', source)

    def test_image_metadata_does_not_advertise_http_api(self):
        dockerfile = DOCKERFILE.read_text(encoding="utf-8")
        self.assertIn("EXPOSE 12525", dockerfile)
        self.assertNotIn("12526", dockerfile)
        self.assertIn(
            "COPY backend-spring-boot/scripts/capture-invitation-smtp-client.py "
            "/app/capture-invitation-smtp-client.py",
            dockerfile,
        )

    def test_capture_server_contains_no_outbound_smtp_client(self):
        source = CAPTURE_SERVER.read_text(encoding="utf-8")
        self.assertNotRegex(source, r"(?m)^\s*(?:import|from)\s+smtplib\b")
        self.assertNotIn("smtplib.SMTP(", source)
        self.assertNotIn("smtplib.SMTP_SSL(", source)

    def test_backend_and_forwarder_are_e2e_only_and_backend_has_no_published_port(self):
        config = COMPOSE.read_text(encoding="utf-8")
        backend = config.split("  backend:\n", maxsplit=1)[1].split(
            "  forwarder:\n", maxsplit=1
        )[0]
        forwarder = config.split("  forwarder:\n", maxsplit=1)[1].split(
            "\nsecrets:\n", maxsplit=1
        )[0]
        self.assertNotIn("\n    ports:", backend)
        self.assertIn("ipv4_address: 172.26.0.3", backend)
        self.assertIn("mac_address: 82:c1:56:8b:59:3b", backend)
        self.assertIn("        target: JWT_SECRET", backend)
        self.assertIn("        target: SPRING_DATASOURCE_PASSWORD", backend)
        self.assertIn("        target: SPRING_FLYWAY_PASSWORD", backend)
        self.assertIn("E2E_MIGRATION_DB_USER", backend)
        self.assertIn('APP_DEMO_SEED_ENABLED: "false"', backend)
        self.assertIn('APP_DEMO_SEED_ROTATE_ADMIN_PASSWORD: "false"', backend)
        capture = config.split("  backend:\n", maxsplit=1)[0]
        self.assertIn("ipv4_address: 172.26.0.4", capture)
        self.assertIn("ipv4_address: 172.26.0.3", backend)
        self.assertNotIn("ipv4_address: 172.26.0.2", capture + backend + forwarder)
        self.assertNotIn("\n    ports:", capture)
        self.assertIn("docker/Dockerfile.e2e-tcp-forwarder", forwarder)
        self.assertIn("pids_limit: 256", forwarder)
        self.assertIn("mem_limit: 128m", forwarder)
        self.assertNotIn("\n    ports:", forwarder)
        self.assertNotIn("\n    secrets:", forwarder)
        self.assertIn("condition: service_started", forwarder)
        self.assertRegex(forwarder, re.compile(r"(?m)^    networks:\n      - e2e$"))
        network = config.split("\nnetworks:\n", maxsplit=1)[1]
        self.assertIn("    external: true", network)
        self.assertIn(
            "name: ${E2E_NETWORK_NAME:?Set the verified internal E2E network name}",
            network,
        )

    def test_only_host_shim_owns_loopback_backend_port_and_vite_targets_it(self):
        config = COMPOSE.read_text(encoding="utf-8")
        for service in ("mail-capture", "backend", "forwarder"):
            service_body = config.split(f"  {service}:\n", maxsplit=1)[1]
            next_service = re.search(r"(?m)^  [a-z-]+:\n", service_body)
            if next_service:
                service_body = service_body[:next_service.start()]
            self.assertNotRegex(service_body, re.compile(r"(?m)^\s+ports:"))
        shim = HOST_FORWARDER.read_text(encoding="utf-8")
        self.assertIn("export const HOST_ADDRESS = '127.0.0.1';", shim)
        self.assertIn("export const HOST_PORT = 8080;", shim)
        self.assertIn("export const BACKEND_ADDRESS = '172.26.0.3:8080';", shim)
        vite = VITE_CONFIG.read_text(encoding="utf-8")
        self.assertIn('"http://127.0.0.1:8080"', vite)

    def test_address_plan_and_network_identity_match_runtime_preflight(self):
        config = COMPOSE.read_text(encoding="utf-8")
        shim = HOST_FORWARDER.read_text(encoding="utf-8")
        self.assertIn("ipv4_address: 172.26.0.4", config)
        self.assertIn("ipv4_address: 172.26.0.3", config)
        self.assertIn("export const APPROVED_DATABASE_IP = '172.26.0.2';", shim)
        self.assertIn("export const APPROVED_E2E_NETWORK_ID =", shim)
        self.assertIn(
            "'a7321f4f4d63c0d57d279865ea2094b137237588291a53e024a5c323c977b344'",
            shim,
        )
        self.assertIn("internal === true", shim)
        self.assertIn("APPROVED_DATABASE_ALIAS = 'ethioloadai-e2e-db'", shim)
        self.assertIn("APPROVED_DATABASE_IP = '172.26.0.2'", shim)

    def test_runbook_preserves_original_ports_and_orders_cutover_safely(self):
        runbook = re.sub(r"\s+", " ", RUNBOOK.read_text(encoding="utf-8"))
        self.assertIn("complete saved port configuration", runbook)
        self.assertIn("`127.0.0.1:8080` to container `8080/tcp` binding", runbook)
        self.assertIn("`172.27.0.2` / MAC `ba:74:2f:8c:02:d2`", runbook)
        capture_start = runbook.index("Start the capture service and wait until healthy at `.4`")
        backend_start = runbook.index("then start the replacement backend at `.3`", capture_start)
        helper_start = runbook.index("Start the forwarder only after the backend owns `.3`", backend_start)
        shim_start = runbook.index("start the host shim as documented above", helper_start)
        self.assertLess(capture_start, backend_start)
        self.assertLess(backend_start, helper_start)
        self.assertLess(helper_start, shim_start)

    def test_forwarder_image_is_static_non_root_and_has_no_docker_socket(self):
        dockerfile = FORWARDER_DOCKERFILE.read_text(encoding="utf-8")
        source = FORWARDER_SOURCE.read_text(encoding="utf-8")
        self.assertIn("FROM scratch", dockerfile)
        self.assertIn("CGO_ENABLED=0 go build", dockerfile)
        self.assertNotIn("ARG FORWARDER_BACKEND_ADDRESS", dockerfile)
        self.assertNotIn("-X main.backendAddress", dockerfile)
        self.assertIn("USER 10001:10001", dockerfile)
        self.assertNotIn("/var/run/docker.sock", dockerfile)
        self.assertIn('const backendAddress = "172.26.0.3:8080"', source)
        self.assertIn('case "forward":', source)
        self.assertNotRegex(source, re.compile(r'(?m)case\s+"(?:exec|shell|command)"'))
        compose = COMPOSE.read_text(encoding="utf-8")
        forwarder = compose.split("  forwarder:\n", maxsplit=1)[1].split(
            "\nsecrets:\n", maxsplit=1
        )[0]
        self.assertNotIn("args:", forwarder)


if __name__ == "__main__":
    unittest.main()

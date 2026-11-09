package com.aisip.OnO.blackbox.support;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.ContainerNetwork;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;

/**
 * 블랙박스 테스트가 요청을 보낼 앱과 그 앱이 붙는 인프라를 JVM 당 한 번 띄운다.
 *
 * <pre>
 *  테스트 JVM ──(매핑 포트)──▶ gateway(socat) ──▶ app ──▶ mysql, redis, rabbitmq, wiremock, s3mock
 *        └──(매핑 포트)──────────────────────────────────▶ wiremock, s3mock  (가짜 응답 등록, 버킷 준비)
 * </pre>
 *
 * <p><b>앱 컨테이너는 internal 네트워크에만 붙는다.</b> internal 네트워크는 밖으로 나가는 경로가 없어서
 * 앱이 실제 FCM, OpenAI, S3, Discord, 소셜 로그인 서버에 닿을 수 없다. 설정을 잘못 적어도 마찬가지다.
 * 대신 internal 네트워크의 포트는 호스트로 매핑되지 않으므로, 기본 네트워크와 internal 양쪽에 붙은
 * socat 게이트웨이를 앞에 둔다.
 *
 * <p>인프라 컨테이너는 Testcontainers 의 기동 확인이 매핑 포트로 이뤄지기 때문에 기본 네트워크로 띄운 뒤
 * internal 네트워크에 별칭을 붙여 추가로 연결한다. 인프라가 밖에 닿는 것은 상관없다.
 */
public final class BlackboxStack {

    public static final String BUCKET = "blackbox-bucket";
    /** 앱이 presigned URL 을 서명할 때 쓰는 S3 호스트. application-blackbox.yml 의 endpoint 와 같다. */
    public static final String S3_HOST_IN_NETWORK = "s3mock:9090";

    /** 서명 확인을 실제로 거치도록 설정에 넣은 값들. application-blackbox.yml 과 맞춘다. */
    public static final String KAKAO_APP_ID = "1234567";
    public static final String GOOGLE_CLIENT_ID = "blackbox-google-client-id";
    public static final String APPLE_CLIENT_ID = "com.blackbox.ono";
    public static final String ADMIN_IDENTIFIER = "blackbox_admin";
    public static final String ADMIN_PASSWORD = "blackbox_admin_password";

    // Firebase 가 기동 시 읽기만 하는 더미 키. 이 키로는 어떤 발송도 인증되지 않는다.
    private static final String DUMMY_FIREBASE_KEY = """
            {
              "type": "authorized_user",
              "client_id": "blackbox-client-id",
              "client_secret": "blackbox-client-secret",
              "refresh_token": "blackbox-refresh-token"
            }
            """;

    private static final Path LOG_DIR = Path.of("build", "blackbox");

    /**
     * 운영 DB 에 마이그레이션 INSERT 로 들어간 카탈로그(미션 정의, 꾸미기 아이템)를 같은 순서로 넣는다.
     * 블랙박스 DB 는 Flyway 를 못 돌려(V1 이 no-op 이라 빈 DB 에 V2 부터 실패) Hibernate 가 스키마만 만든다.
     * 화이트박스 테스트의 MissionDefinitionSeeder, CosmeticItemSeeder 와 같은 목록이다. 카탈로그를 손대는
     * 마이그레이션을 새로 만들면 세 곳에 같이 더한다.
     */
    private static final List<String> CATALOG_MIGRATIONS = List.of(
            "V30__seed_mission_definitions.sql",
            "V33__refine_mission_definition_wording.sql",
            "V47__use_digits_in_mission_titles.sql",
            "V35__seed_cosmetic_items.sql",
            "V37__seed_cosmetic_items_by_ability.sql",
            "V38__seed_profile_frame_cosmetics.sql",
            "V39__rename_back_slot_item_names.sql",
            "V41__merge_back_slot_into_bag.sql");

    private static volatile BlackboxStack instance;

    private final Network internal;
    private final MySQLContainer<?> mysql;
    private final GenericContainer<?> redis;
    private final RabbitMQContainer rabbitmq;
    private final GenericContainer<?> wiremock;
    private final GenericContainer<?> s3;
    private final GenericContainer<?> app;
    private final GenericContainer<?> gateway;

    private BlackboxStack() {
        internal = Network.builder()
                .createNetworkCmdModifier(cmd -> cmd.withInternal(true))
                .build();

        mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName("ono_blackbox")
                .withUsername("test")
                .withPassword("test")
                .withCommand(
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_unicode_ci",
                        "--skip-character-set-client-handshake",
                        "--default-time-zone=+09:00"
                );
        redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                .withExposedPorts(6379);
        rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
                // guest 계정은 localhost 에서만 접속을 받는다. 앱은 다른 컨테이너라 계정을 따로 만든다.
                .withAdminUser("blackbox")
                .withAdminPassword("blackbox");
        wiremock = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.1"))
                .withExposedPorts(8080)
                .withCommand("--disable-banner", "--global-response-templating")
                // Discord 웹훅은 늘 받아 준다. 시나리오가 알림 수를 세서 서버 오류를 잡는다(BlackboxTest).
                .withCopyToContainer(Transferable.of("""
                        {"priority": 10,
                         "request": {"method": "POST", "urlPath": "/discord-webhook"},
                         "response": {"status": 204}}
                        """), "/home/wiremock/mappings/discord.json")
                // OpenAI 기본 응답. 장애 시나리오는 더 높은 우선순위로 덮어쓴다.
                .withCopyToContainer(Transferable.of(OpenAiStub.defaultAnalysisMapping()),
                        "/home/wiremock/mappings/openai-analysis.json")
                .withCopyToContainer(Transferable.of(OpenAiStub.defaultLearningReportMapping()),
                        "/home/wiremock/mappings/openai-learning-report.json")
                .waitingFor(Wait.forHttp("/__admin/health").forStatusCode(200));
        // MinIO 는 Docker Hub 배포를 멈춰서 받을 수 없다. s3mock 은 서명을 확인하지 않고 S3 API 를 흉내 낸다.
        // 앱이 S3 에 올리고, 읽고(AI 분석), 지우는 요청이 실제로 나가는지는 이것으로 충분히 확인된다.
        s3 = new GenericContainer<>(DockerImageName.parse("adobe/s3mock:4.7.0"))
                .withExposedPorts(9090)
                .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCode(200));

        app = new GenericContainer<>(appImage())
                .withNetwork(internal)
                .withNetworkAliases("app")
                .withEnv("SPRING_PROFILES_ACTIVE", "blackbox")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("application-blackbox.yml"),
                        "/app/config/application-blackbox.yml")
                .withLogConsumer(BlackboxStack::appendAppLog)
                // internal 네트워크라 포트로 기동을 확인할 수 없어 로그로 본다.
                .waitingFor(Wait.forLogMessage(".*Started BackendApplication.*\\n", 1)
                        .withStartupTimeout(Duration.ofMinutes(4)));

        gateway = new GenericContainer<>(DockerImageName.parse("alpine/socat:1.8.0.0"))
                .withExposedPorts(8080)
                .withCommand("TCP-LISTEN:8080,fork,reuseaddr", "TCP:app:8080")
                .waitingFor(Wait.forListeningPort());
    }

    public static BlackboxStack get() {
        if (instance == null) {
            synchronized (BlackboxStack.class) {
                if (instance == null) {
                    BlackboxStack stack = new BlackboxStack();
                    stack.start();
                    instance = stack;
                }
            }
        }
        return instance;
    }

    private void start() {
        resetLog();
        List.of(mysql, redis, rabbitmq, wiremock, s3).parallelStream().forEach(GenericContainer::start);
        connect(mysql, "mysql");
        connect(redis, "redis");
        connect(rabbitmq, "rabbitmq");
        connect(wiremock, "wiremock");
        connect(s3, "s3mock");
        createBucket();

        app.start();
        alignCascadeForeignKeys();
        seedCatalogs();
        gateway.start();
        connect(gateway, "gateway");
    }

    /** 앱에 요청을 보낼 주소. */
    public String appBaseUrl() {
        return "http://" + gateway.getHost() + ":" + gateway.getMappedPort(8080);
    }

    /** 테스트가 가짜 응답을 등록할 WireMock 관리 주소. */
    public String wiremockBaseUrl() {
        return "http://" + wiremock.getHost() + ":" + wiremock.getMappedPort(8080);
    }

    /** presigned URL 의 호스트(s3mock:9090)를 바꾸지 않고 실제로 보낼 주소. */
    public String s3BaseUrl() {
        return "http://" + s3.getHost() + ":" + s3.getMappedPort(9090);
    }

    /** 앱이 S3 에 올린 객체가 있는지 확인한다. s3mock 은 인증을 보지 않아 서명 없이 HEAD 를 보낸다. */
    public boolean objectExists(String key) {
        return s3Request("HEAD", "/" + BUCKET + "/" + key) == 200;
    }

    private static final java.util.regex.Pattern CREATE_TABLE =
            java.util.regex.Pattern.compile("CREATE TABLE (?:IF NOT EXISTS )?`?(\\w+)`?\\s*\\(", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern CASCADE_FK = java.util.regex.Pattern.compile(
            "FOREIGN KEY \\(\\s*`?(\\w+)`?\\s*\\) REFERENCES `?(\\w+)`?\\s*\\(\\s*`?(\\w+)`?\\s*\\) ON DELETE CASCADE",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * 운영 스키마는 마이그레이션이 외래키에 ON DELETE CASCADE 를 걸었고(V6, V10, V11), 방 삭제 같은 기능이 그것에 기댄다.
     * Hibernate 가 만든 스키마에는 CASCADE 가 없어서, 운영에서는 되는 방 삭제가 여기서는 외래키 위반으로 실패한다.
     * 마이그레이션에서 규칙을 읽어 Hibernate 가 만든 같은 (테이블, 컬럼, 참조 테이블) 외래키를 CASCADE 로 바꿔 운영과 맞춘다.
     */
    private void alignCascadeForeignKeys() {
        Path dir = Path.of(requiredProperty("blackbox.migrations"));
        try (var connection = jdbc(); var statement = connection.createStatement();
             var files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).sorted().toList()) {
                String script = Files.readString(file, StandardCharsets.UTF_8);
                var tables = CREATE_TABLE.matcher(script);
                List<int[]> ranges = new java.util.ArrayList<>();
                List<String> names = new java.util.ArrayList<>();
                while (tables.find()) {
                    ranges.add(new int[]{tables.start()});
                    names.add(tables.group(1));
                }
                for (int i = 0; i < names.size(); i++) {
                    int end = i + 1 < ranges.size() ? ranges.get(i + 1)[0] : script.length();
                    var fk = CASCADE_FK.matcher(script.substring(ranges.get(i)[0], end));
                    while (fk.find()) {
                        cascade(connection, statement, names.get(i), fk.group(1), fk.group(2), fk.group(3));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("외래키 CASCADE 맞추기 실패", e);
        }
    }

    private static void cascade(java.sql.Connection connection, java.sql.Statement statement,
                                String table, String column, String referenced, String referencedColumn)
            throws java.sql.SQLException {
        String find = "SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? AND REFERENCED_TABLE_NAME = ?";
        try (var query = connection.prepareStatement(find)) {
            query.setString(1, table);
            query.setString(2, column);
            query.setString(3, referenced);
            try (var rows = query.executeQuery()) {
                // 지금은 없는 테이블(V13 에서 지운 study_session 등)은 건너뛴다.
                while (rows.next()) {
                    String name = rows.getString(1);
                    statement.executeUpdate("ALTER TABLE `" + table + "` DROP FOREIGN KEY `" + name + "`");
                    statement.executeUpdate("ALTER TABLE `" + table + "` ADD CONSTRAINT `" + name + "` FOREIGN KEY (`"
                            + column + "`) REFERENCES `" + referenced + "` (`" + referencedColumn + "`) ON DELETE CASCADE");
                }
            }
        }
    }

    private java.sql.Connection jdbc() throws java.sql.SQLException {
        return java.sql.DriverManager.getConnection(
                mysql.getJdbcUrl() + "?characterEncoding=UTF-8", mysql.getUsername(), mysql.getPassword());
    }

    private void seedCatalogs() {
        Path dir = Path.of(requiredProperty("blackbox.migrations"));
        try (var connection = jdbc(); var statement = connection.createStatement()) {
            for (String file : CATALOG_MIGRATIONS) {
                for (String sql : dataStatements(Files.readString(dir.resolve(file), StandardCharsets.UTF_8))) {
                    statement.executeUpdate(sql);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("카탈로그 시드 실패", e);
        }
    }

    /** 주석 줄을 걷어내고 INSERT, UPDATE, DELETE 만 남긴다. 주석에도 세미콜론이 있을 수 있다. */
    private static List<String> dataStatements(String script) {
        String withoutComments = script.lines()
                .filter(line -> !line.trim().startsWith("--"))
                .collect(java.util.stream.Collectors.joining("\n"));
        return java.util.Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(sql -> {
                    String upper = sql.toUpperCase(java.util.Locale.ROOT);
                    return upper.startsWith("INSERT") || upper.startsWith("UPDATE") || upper.startsWith("DELETE");
                })
                .toList();
    }

    /**
     * 앱 컨테이너 안에서 host:port 로 TCP 연결이 되는지 본다. 안전장치(외부로 못 나감)를 확인하는 데만 쓴다.
     * bash 의 /dev/tcp 를 쓴다. 운영 Dockerfile 의 베이스 이미지(eclipse-temurin, Ubuntu)에 bash 와 timeout 이 있다.
     */
    public boolean appCanConnect(String host, int port) {
        try {
            var result = app.execInContainer("bash", "-c",
                    "timeout 5 bash -c '</dev/tcp/" + host + "/" + port + "' 2>/dev/null");
            return result.getExitCode() == 0;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("앱 컨테이너 연결 확인 실패", e);
        }
    }

    private void createBucket() {
        int status = s3Request("PUT", "/" + BUCKET);
        if (status != 200) {
            throw new IllegalStateException("S3 버킷 준비 실패: " + status);
        }
    }

    private int s3Request(String method, String path) {
        try {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(s3BaseUrl() + path))
                    .method(method, java.net.http.HttpRequest.BodyPublishers.noBody())
                    .build();
            return java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.discarding())
                    .statusCode();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void connect(GenericContainer<?> container, String alias) {
        DockerClient client = DockerClientFactory.instance().client();
        client.connectToNetworkCmd()
                .withContainerId(container.getContainerId())
                .withNetworkId(internal.getId())
                .withContainerNetwork(new ContainerNetwork().withAliases(alias))
                .exec();
    }

    private static ImageFromDockerfile appImage() {
        Path jar = Path.of(requiredProperty("blackbox.jar"));
        Path dockerfile = Path.of(requiredProperty("blackbox.dockerfile"));
        // 운영 Dockerfile 을 그대로 쓴다. JAR_FILE 기본값(build/libs/*.jar)에 맞춰 jar 를 둔다.
        return new ImageFromDockerfile("ono-blackbox-app", true)
                .withFileFromPath("Dockerfile", dockerfile)
                .withFileFromPath("build/libs/app.jar", jar)
                .withFileFromString("FirebaseAdminKey.json", DUMMY_FIREBASE_KEY);
    }

    private static String requiredProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 가 없습니다. ./gradlew blackboxTest 로 실행해 주세요");
        }
        return value;
    }

    private static void resetLog() {
        try {
            Files.createDirectories(LOG_DIR);
            Files.writeString(LOG_DIR.resolve("app.log"), "", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void appendAppLog(OutputFrame frame) {
        try {
            Files.writeString(LOG_DIR.resolve("app.log"), frame.getUtf8String(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // 로그 저장 실패로 테스트를 깨지 않는다.
        }
    }
}

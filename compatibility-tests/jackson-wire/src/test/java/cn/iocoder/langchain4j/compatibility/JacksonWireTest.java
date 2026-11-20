package cn.iocoder.langchain4j.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.output.ServiceOutputParser;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Tests the installed LangChain4j codec and real consumer entry points on both Jackson lines.
 *
 * @author 芋道源码
 */
class JacksonWireTest {

    private static final String ID = "lookup-甲";
    private static final long LARGE_ID = 9007199254740993L;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);
    private static final LocalTime TIME = LocalTime.of(8, 15, 30);
    private static final String INLINE_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC0lEQVR42mP8/x8AAwMCAO+aR9sAAAAASUVORK5CYII=";
    private static final BigDecimal AMOUNT = new BigDecimal("1234567890.000000000123");
    private static ConfigurableApplicationContext boot;
    private static Path fixtures;
    private static boolean write;

    @BeforeAll
    static void startBoot() throws Exception {
        assertEquals(required("wire.boot"), SpringBootVersion.getVersion());
        assertEquals(required("wire.jdk"), System.getProperty("java.specification.version"));
        boot = new SpringApplicationBuilder(Fixture.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "logging.level.root=warn")
                .run();
        String prefix = required("wire.codec");
        assertCodec(Json.class, prefix + "JsonCodec");
        assertCodec(ChatMessageSerializer.class, prefix + "ChatMessageJsonCodec");
        fixtures = Paths.get(required("wire.fixtures"));
        switch (required("wire.action")) {
            case "write":
                write = true;
                Files.createDirectories(fixtures);
                break;
            case "read":
                write = false;
                break;
            default:
                throw new IllegalArgumentException("Unknown wire.action");
        }
    }

    @AfterAll
    static void closeBoot() {
        if (boot != null) {
            boot.close();
        }
    }

    @Test
    void bindsToolArgumentsThroughDefaultToolExecutor() throws Exception {
        String json = wire("tool-arguments.json", JacksonWireTest::arguments);
        LookupTool tool = new LookupTool();
        ToolExecutionRequest request = lookup(json);
        String answer = new DefaultToolExecutor(tool, request).execute(request, "wire-fixture");
        assertEquals("查到 9007199254740993", answer);
        assertLookup(tool.calledWith);
    }

    @Test
    void readsConversationWithMultimodalContentAndToolMessages() throws IOException {
        String json = wire("conversation.json", () -> ChatMessageSerializer.messagesToJson(conversation()));
        List<ChatMessage> decoded = ChatMessageDeserializer.messagesFromJson(json);
        List<ChatMessage> expected = conversation();
        assertEquals(expected.size(), decoded.size());
        assertEquals(expected.get(0), decoded.get(0));
        assertEquals(expected.get(1), decoded.get(1));

        AiMessage result = (AiMessage) decoded.get(2);
        AiMessage expectedAi = (AiMessage) expected.get(2);
        assertEquals(expectedAi.text(), result.text());
        assertEquals(expectedAi.thinking(), result.thinking());
        assertEquals(expectedAi.attributes(), result.attributes());
        assertEquals(1, result.toolExecutionRequests().size());
        ToolExecutionRequest tool = result.toolExecutionRequests().get(0);
        assertEquals(ID, tool.id());
        assertEquals("lookup", tool.name());
        LookupTool lookupTool = new LookupTool();
        new DefaultToolExecutor(lookupTool, tool).execute(tool, "message-fixture");
        assertLookup(lookupTool.calledWith);
        assertEquals(expected.get(3), decoded.get(3));
    }

    @Test
    void parsesTypedStructuredOutputWithExactNumbers() throws IOException {
        String json = wire("structured-output.json", () -> Json.toJson(summary()));
        Output decoded = (Output) new ServiceOutputParser().parse(
                ChatResponse.builder().aiMessage(AiMessage.from(json)).build(), Output.class);
        assertEquals(DAY, decoded.day);
        assertEquals(Status.ACCEPTED, decoded.status);
        assertNull(decoded.note);
        assertEquals(2, decoded.rows.size());
        assertEquals("订单甲", decoded.rows.get(0).title);
        assertEquals(AMOUNT, decoded.rows.get(0).amount);
        assertEquals(LARGE_ID, decoded.rows.get(0).id);
        assertEquals("订单乙", decoded.rows.get(1).title);
        assertEquals(new BigDecimal("0.000001"), decoded.rows.get(1).amount);
        assertEquals(0L, decoded.rows.get(1).id);
    }

    private static void assertLookup(Query query) {
        assertNotNull(query);
        assertEquals(LARGE_ID, query.id);
        assertEquals(DAY, query.day);
        assertEquals(TIME, query.time);
        assertEquals(Status.ACCEPTED, query.status);
        assertEquals(Arrays.asList("芋道", "换行\n\"引号\""), query.tags);
        assertFalse(query.expand);
        assertNull(query.note);
    }

    private String wire(String name, Supplier<String> producer) throws IOException {
        Path file = fixtures.resolve(name);
        if (write) {
            Files.write(file, producer.get().getBytes(StandardCharsets.UTF_8));
        }
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static void assertCodec(Class<?> holder, String expected) throws Exception {
        Field codec = holder.getDeclaredField("CODEC");
        codec.setAccessible(true);
        assertEquals(expected, codec.get(null).getClass().getSimpleName());
    }

    private static ToolExecutionRequest lookup(String input) {
        return ToolExecutionRequest.builder().id(ID).name("lookup").arguments(input).build();
    }

    private static String arguments() {
        Query query = new Query();
        query.id = LARGE_ID;
        query.day = DAY;
        query.time = TIME;
        query.status = Status.ACCEPTED;
        query.tags = Arrays.asList("芋道", "换行\n\"引号\"");
        return Json.toJson(Collections.singletonMap("request", query));
    }

    private static List<ChatMessage> conversation() {
        return Arrays.asList(
                SystemMessage.from("按订单查询。"),
                UserMessage.from("芋道", TextContent.from("查一下\n订单"),
                        ImageContent.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aR9sAAAAASUVORK5CYII=", "image/png")),
                AiMessage.builder()
                        .text("正在查询")
                        .thinking("查订单")
                        .attributes(Collections.singletonMap("source", "共享样例"))
                        .toolExecutionRequests(Collections.singletonList(lookup(arguments())))
                        .build(),
                ToolExecutionResultMessage.from(ID, "lookup", "已查询\n完成"));
    }

    private static Output summary() {
        Output output = new Output();
        output.day = DAY;
        output.status = Status.ACCEPTED;
        output.rows = Arrays.asList(row(LARGE_ID, "订单甲", AMOUNT), row(0, "订单乙", new BigDecimal("0.000001")));
        return output;
    }

    private static Row row(long id, String title, BigDecimal amount) {
        Row row = new Row();
        row.id = id;
        row.title = title;
        row.amount = amount;
        return row;
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing " + key);
        }
        return value;
    }

    /** @author 芋道源码 */
    enum Status {
        ACCEPTED
    }

    /** @author 芋道源码 */
    public static class Query {
        public long id;
        public boolean expand;
        public LocalDate day;
        public LocalTime time;
        public List<String> tags;
        public Status status;
        public String note;
    }

    /** @author 芋道源码 */
    public static class LookupTool {
        Query calledWith;

        @Tool("查询订单")
        public String lookup(@P(value = "查询条件", name = "request") Query query) {
            calledWith = query;
            return "查到 " + query.id;
        }
    }

    /** @author 芋道源码 */
    public static class Output {
        public LocalDate day;
        public Status status;
        public List<Row> rows;
        public String note;
    }

    /** @author 芋道源码 */
    public static class Row {
        public long id;
        public String title;
        public BigDecimal amount;
    }

    /** @author 芋道源码 */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Fixture {}
}

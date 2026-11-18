package dev.langchain4j.rag.content.retriever.hibernate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.persistence.Entity;
import javax.persistence.Id;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author 芋道源码
 */
class Hibernate5ContentRetrieverTest {

    private SessionFactory factory;

    @BeforeEach
    void createSampleDatabase() {
        factory = new Configuration()
                .addAnnotatedClass(SampleProduct.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:" + UUID.randomUUID())
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .buildSessionFactory();

        try (Session session = factory.openSession()) {
            Transaction transaction = session.beginTransaction();
            session.persist(new SampleProduct(1L, "芋道样品", 28));
            transaction.commit();
        }
    }

    @AfterEach
    void close() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void describesMappedAttributesAndSerializesTupleAndEntityResults() {
        AtomicReference<String> prompt = new AtomicReference<>();
        List<Content> projection = retriever("select p.name, p.price from SampleProduct p", prompt)
                .retrieve(Query.from("样品价格是多少？"));
        assertThat(prompt.get()).contains("\"name\":\"SampleProduct\"", "\"identifierAttribute\":\"id\"");
        assertThat(projection).singleElement().satisfies(result ->
                assertThat(result.textSegment().text()).contains("[[\"芋道样品\",28]]"));

        List<Content> entities = retriever("from SampleProduct", prompt).retrieve(Query.from("列出样品"));
        assertThat(entities).singleElement().satisfies(result ->
                assertThat(result.textSegment().text()).contains("\"name\":\"芋道样品\"", "\"id\":1"));
    }

    @Test
    void rejectsMutationQueriesAndRetainsStoredRows() {
        AtomicReference<String> prompt = new AtomicReference<>();
        assertThat(retriever("delete from SampleProduct", prompt).retrieve(Query.from("测试查询"))).isEmpty();

        try (Session session = factory.openSession()) {
            assertThat(session.get(SampleProduct.class, 1L).name).isEqualTo("芋道样品");
        }
    }

    private HibernateContentRetriever retriever(String hql, AtomicReference<String> prompt) {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                prompt.set(((SystemMessage) request.messages().get(0)).text());
                return ChatResponse.builder().aiMessage(AiMessage.from(hql)).build();
            }
        };
        return HibernateContentRetriever.builder().entityManagerFactory(factory).chatModel(model).build();
    }

    /**
     * @author 芋道源码
     */
    @Entity(name = "SampleProduct")
    public static class SampleProduct {

        @Id
        private Long id;

        private String name;

        private Integer price;

        public SampleProduct() {}

        SampleProduct(Long id, String name, Integer price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }
    }
}

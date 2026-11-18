package dev.langchain4j.store.memory.chat.coherence;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;
import com.tangosol.net.Session;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ChatMemoryStore} backed by an Oracle Coherence named map.
 * <p>
 * The {@link CoherenceChatMemoryStore} supports memory identifiers of any type
 * that is a valid Coherence {@link NamedCache} key. The key type must properly
 * implement {@code equals()} and {@code hashCode()} and be serializable by
 * the configured Coherence serializer.
 */
public class CoherenceChatMemoryStore implements ChatMemoryStore {

    /**
     * The default {@link NamedCache} name.
     */
    public static final String DEFAULT_MAP_NAME = "chatMemory";

    /**
     * The {@link NamedCache} used to store the chat messages.
     */
    protected final NamedCache<Object, String> chatMemory;

    /**
     * Create a {@link CoherenceChatMemoryStore}.
     * <p>
     * This method is protected, instances of {@link CoherenceChatMemoryStore}
     * are created using the builder.
     *
     * @param chatMemory  the {@link NamedCache} to store the chat history.
     */
    protected CoherenceChatMemoryStore(NamedCache<Object, String> chatMemory) {
        this.chatMemory = chatMemory;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        validateId(memoryId);
        String json = chatMemory.get(memoryId);
        return json == null ? new ArrayList<>() : ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        validateId(memoryId);
        String json = ChatMessageSerializer.messagesToJson(ensureNotEmpty(messages, "messages"));
        chatMemory.put(memoryId, json);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        validateId(memoryId);
        chatMemory.remove(memoryId);
    }

    private void validateId(Object memoryId) {
        ensureNotNull(memoryId, "memoryId");
    }

    /**
     * Create a default {@link CoherenceChatMemoryStore}.
     *
     * @return a default {@link CoherenceChatMemoryStore}
     */
    public static CoherenceChatMemoryStore create() {
        return builder().build();
    }

    /**
     * Create a {@link CoherenceChatMemoryStore} that uses the
     * specified Coherence {@link NamedCache} name.
     *
     * @param name  the name of the Coherence {@link NamedCache} used to store chat messages
     *
     * @return a {@link CoherenceChatMemoryStore}
     */
    public static CoherenceChatMemoryStore create(String name) {
        return builder().name(name).build();
    }

    /**
     * Create a {@link CoherenceChatMemoryStore} that uses the
     * specified Coherence {@link NamedCache} name.
     *
     * @param map  the {@link NamedCache} used to store chat messages
     *
     * @return a {@link CoherenceChatMemoryStore}
     */
    public static CoherenceChatMemoryStore create(NamedCache<Object, String> map) {
        return new CoherenceChatMemoryStore(map);
    }

    /**
     * Return a {@link Builder} to use to build a {@link CoherenceChatMemoryStore}.
     *
     * @return  a {@link Builder} to use to build a {@link CoherenceChatMemoryStore}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder to create {@link CoherenceChatMemoryStore} instances.
     */
    public static class Builder {
        /**
         * The name of the {@link NamedCache} to contain the serialized {@link ChatMessage chat messages}.
         */
        private String name = DEFAULT_MAP_NAME;

        /**
         * The name of the {@link Session} to use to obtain the {@link NamedCache}.
         */
        private String sessionName;

        /**
         * The {@link Session} to use to obtain the {@link NamedCache}.
         */
        private Session session;

        /**
         * Create a {@link Builder}.
         */
        protected Builder() {}

        /**
         * Set the name of the {@link NamedCache} that will hold the
         * serialized {@link ChatMessage chat messages}.
         *
         * @param name the name of the {@link NamedCache} to store serialized {@link ChatMessage chat messages}
         * @return this builder for fluent method calls
         */
        public Builder name(String name) {
            this.name = isNullOrBlank(name) ? DEFAULT_MAP_NAME : name;
            return this;
        }

        /**
         * Select the default by passing {@code null}. For an application-specific
         * Java 8 session, use {@link #session(Session)}.
         *
         * @param sessionName  {@code null} for the default cache factory
         *
         * @return this builder for fluent method calls
         */
        public Builder session(String sessionName) {
            this.sessionName = sessionName;
            this.session = null;
            return this;
        }

        /**
         * Set the {@link Session} to use to obtain the
         * document chunk {@link NamedCache}.
         *
         * @param session  the {@link Session} to use
         *
         * @return this builder for fluent method calls
         */
        public Builder session(Session session) {
            this.session = session;
            this.sessionName = null;
            return this;
        }

        /**
         * Creates a new instance of {@link CoherenceChatMemoryStore} using the current
         * configuration of this {@code Builder}.
         * <p>
         * Uses the supplied {@link Session} or the default Coherence cache factory.
         * <p>
         * Once a {@link Session} is resolved, the configured {@link NamedCache} (or the default
         * {@link #DEFAULT_MAP_NAME} if none was set) is retrieved and used to create a new
         * {@link CoherenceChatMemoryStore} instance.
         *
         * @return a new instance of {@link CoherenceChatMemoryStore}
         */
        public CoherenceChatMemoryStore build() {
            if (session == null && sessionName != null) {
                throw new UnsupportedOperationException("Named Coherence sessions require the modern route. Pass a Session on Java 8.");
            }
            NamedCache<Object, String> map = session == null ? CacheFactory.getCache(name) : session.getCache(name);
            return new CoherenceChatMemoryStore(map);
        }
    }
}

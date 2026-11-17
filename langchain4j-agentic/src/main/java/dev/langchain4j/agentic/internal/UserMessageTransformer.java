package dev.langchain4j.agentic.internal;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@FunctionalInterface
public interface UserMessageTransformer extends BiFunction<ChatRequest, Object, ChatRequest> {

    @Override
    default ChatRequest apply(ChatRequest chatRequest, Object memoryId) {
        List<ChatMessage> messages = chatRequest.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                UserMessage transformedMessage = UserMessageTransformations.transformText(this, userMessage, memoryId);
                List<ChatMessage> modifiedMessages = chatRequest.messages().stream()
                        .map(message -> message == userMessage ? transformedMessage : message)
                        .toList();
                return ChatRequest.builder()
                        .messages(modifiedMessages)
                        .parameters(chatRequest.parameters())
                        .build();
            }
        }
        return chatRequest;
    }

    /**
     * The text contents of the given user message joined by new lines, or an empty string if it has none: the
     * message can also have other contents, like the image sent to a multimodal agent.
     */
    static String text(UserMessage userMessage) {
        return userMessage.contents().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .collect(Collectors.joining("\n"));
    }

    String transformUserMessage(String userMessage, Object memoryId);
}

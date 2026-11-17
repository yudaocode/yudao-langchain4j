package dev.langchain4j.agentic.internal;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the upstream text transformation private to the package on Java 8.
 *
 * @author 芋道源码
 */
final class UserMessageTransformations {

    private UserMessageTransformations() {}

    /**
     * Transforms the text of the given user message, as returned by {@link UserMessageTransformer#text(UserMessage)}, keeping its other
     * contents, like images, as well as its name and attributes. The transformed text replaces all the text contents
     * with a single one, in the position of the first of them. A message without any text, like a message made only
     * of an image, gets the transformation of an empty text as its first content, unless it is empty.
     */
    static UserMessage transformText(UserMessageTransformer transformer, UserMessage userMessage, Object memoryId) {
        String transformedText = transformer.transformUserMessage(UserMessageTransformer.text(userMessage), memoryId);

        List<Content> contents = new ArrayList<>();
        boolean textAdded = false;
        for (Content content : userMessage.contents()) {
            if (!(content instanceof TextContent)) {
                contents.add(content);
            } else if (!textAdded) {
                contents.add(TextContent.from(transformedText));
                textAdded = true;
            }
        }

        if (!textAdded) {
            if (isNullOrEmpty(transformedText)) {
                return userMessage;
            }
            contents.add(0, TextContent.from(transformedText));
        }
        return userMessage.toBuilder().contents(contents).build();
    }
}

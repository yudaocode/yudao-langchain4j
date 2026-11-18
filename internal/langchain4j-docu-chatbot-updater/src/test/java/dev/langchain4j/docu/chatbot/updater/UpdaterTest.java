package dev.langchain4j.docu.chatbot.updater;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Document;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * @author 芋道源码
 */
class UpdaterTest {

    @TempDir
    Path docs;

    @Test
    void loadsOnlyMarkdownAndMdxAcrossDirectories() throws Exception {
        Path nested = Files.createDirectory(docs.resolve("指南"));
        Files.write(docs.resolve("readme.md"), "首页".getBytes(StandardCharsets.UTF_8));
        Files.write(nested.resolve("intro.mdx"), "使用指南".getBytes(StandardCharsets.UTF_8));
        Files.write(nested.resolve("ignore.txt"), "忽略".getBytes(StandardCharsets.UTF_8));

        assertThat(Updater.loadDocuments(docs))
                .extracting(Document::text)
                .containsExactlyInAnyOrder("首页", "使用指南");
    }
}

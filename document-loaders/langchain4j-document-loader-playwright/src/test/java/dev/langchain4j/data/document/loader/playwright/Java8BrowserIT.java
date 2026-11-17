package dev.langchain4j.data.document.loader.playwright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.document.Document;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Uses a fresh headless test browser and a loopback page on the actual target VM.
 *
 * @author 芋道源码
 */
@Timeout(60)
@EnabledIfSystemProperty(named = "compat.browser.tests", matches = "true")
class Java8BrowserIT {
    @Test
    void browserLoadsUnicodeHtmlAndKeepsSourceMetadataOnNative8() throws Exception {
        assertEquals("1.8", System.getProperty("java.specification.version"));
        String chrome = System.getProperty("compat.chrome.binary");
        assertTrue(
                chrome != null && Files.isExecutable(Paths.get(chrome)),
                "Set compat.chrome.binary to an isolated test browser");
        byte[] html =
                "<!doctype html><html><head><meta charset=utf-8><title>Native8</title></head><body><div id=doc>预算 café 你好</div></body></html>"
                        .getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, html.length);
                exchange.getResponseBody().write(html);
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/doc";
        try (Playwright sdk = Playwright.create();
                Browser browser = sdk.chromium()
                        .launch(new BrowserType.LaunchOptions()
                                .setHeadless(true)
                                .setExecutablePath(Paths.get(chrome)))) {
            Document document =
                    PlaywrightDocumentLoader.builder().browser(browser).build().load(url);
            assertTrue(document.text().contains("预算 café 你好"), document.text());
            assertEquals(url, document.metadata().getString(Document.URL));
        } finally {
            server.stop(0);
        }
    }
}

package dev.langchain4j.data.document.loader.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.document.Document;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

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
        String driverPath = System.getProperty("compat.chrome.driver");
        assertTrue(
                driverPath != null && Files.isExecutable(Paths.get(driverPath)),
                "Set compat.chrome.driver to the matching test driver");
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
        ChromeOptions options = new ChromeOptions();
        options.setBinary(chrome);
        options.addArguments("--headless=new", "--no-first-run", "--disable-background-networking");
        ChromeDriverService service = new ChromeDriverService.Builder()
                .usingDriverExecutable(new File(driverPath))
                .usingAnyFreePort()
                .build();
        ChromeDriver driver = null;
        try {
            driver = new ChromeDriver(service, options);
            Document document =
                    SeleniumDocumentLoader.builder().webDriver(driver).build().load(url);
            assertTrue(document.text().contains("预算 café 你好"), document.text());
            assertEquals(url, document.metadata().getString(Document.URL));
        } finally {
            try {
                if (driver != null) driver.quit();
            } finally {
                try {
                    service.stop();
                } finally {
                    server.stop(0);
                }
            }
        }
    }
}

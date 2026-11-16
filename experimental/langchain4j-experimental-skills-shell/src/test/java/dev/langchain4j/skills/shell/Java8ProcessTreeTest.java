package dev.langchain4j.skills.shell;

import static org.assertj.core.api.Assertions.assertThat;
import dev.langchain4j.internal.compat.Java8Platform;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** @author 芋道源码 */
@DisabledOnOs(OS.WINDOWS)
class Java8ProcessTreeTest {
    @Test
    @Timeout(15)
    void killsOnlyTheOwnedShellDescendants() throws Exception {
        Process shell = new ProcessBuilder("sh", "-c", "sleep 30 & echo $!; wait").start();
        long child = -1;
        try (BufferedReader output = new BufferedReader(new InputStreamReader(shell.getInputStream()))) {
            child = Long.parseLong(output.readLine());
            assertThat(Java8Platform.isProcessAlive(child)).isTrue();
            Java8ProcessTree.destroyDescendants(shell);
            assertThat(shell.waitFor(5, TimeUnit.SECONDS)).isTrue();
            assertThat(Java8Platform.isProcessAlive(child)).isFalse();
        } finally {
            if (child > 0 && Java8Platform.isProcessAlive(child)) {
                new ProcessBuilder("kill", "-KILL", Long.toString(child)).start().waitFor();
            }
            shell.destroyForcibly();
            shell.getErrorStream().close();
            shell.getOutputStream().close();
        }
    }
}

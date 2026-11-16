package dev.langchain4j.skills.shell;

import dev.langchain4j.internal.compat.Java8Platform;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Best-effort descendant cleanup for a process owned by this shell runner.
 * Unix uses a PID/parent snapshot; Java 8 Windows cannot expose every vendor's native process PID.
 * @author 芋道源码
 */
final class Java8ProcessTree {
    private Java8ProcessTree() {}

    static void destroyDescendants(Process root) throws Exception {
        long rootPid = Java8Platform.pidForLogging(root);
        if (rootPid <= 0 || !root.isAlive()) return;
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
            run("taskkill", "/PID", Long.toString(rootPid), "/T", "/F");
            return;
        }
        Map<Long, List<Long>> children = new HashMap<>();
        Process snapshot = new ProcessBuilder("ps", "-A", "-o", "pid=,ppid=").start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                snapshot.getInputStream(), StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] ids = line.trim().split("\\s+");
                if (ids.length != 2) continue;
                long pid = Long.parseLong(ids[0]);
                long parent = Long.parseLong(ids[1]);
                if (pid > 0) children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(pid);
            }
        } finally {
            snapshot.getErrorStream().close();
            snapshot.getOutputStream().close();
            if (!snapshot.waitFor(5, TimeUnit.SECONDS)) snapshot.destroyForcibly();
        }
        List<Long> descendants = new ArrayList<>();
        descendants.add(rootPid);
        for (int index = 0; index < descendants.size(); index++) {
            List<Long> found = children.get(descendants.get(index));
            if (found != null) for (Long child : found) {
                if (!descendants.contains(child)) descendants.add(child);
            }
        }
        for (int index = descendants.size() - 1; index > 0; index--) {
            run("kill", "-KILL", Long.toString(descendants.get(index)));
        }
    }

    private static void run(String... command) throws Exception {
        Process commandProcess = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            if (!commandProcess.waitFor(5, TimeUnit.SECONDS)) commandProcess.destroyForcibly();
        } finally {
            commandProcess.getInputStream().close();
            commandProcess.getErrorStream().close();
            commandProcess.getOutputStream().close();
        }
    }
}

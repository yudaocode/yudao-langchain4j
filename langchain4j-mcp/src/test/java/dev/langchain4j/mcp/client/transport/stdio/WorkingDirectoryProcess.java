package dev.langchain4j.mcp.client.transport.stdio;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Standalone fixture: the Java 8 launcher resolves enclosing-class dependencies for nested mains.
 * @author 芋道源码
 */
public final class WorkingDirectoryProcess {
    private WorkingDirectoryProcess() {}
    public static void main(String[] args) throws IOException {
        System.out.println(Paths.get("").toRealPath().toUri());
        System.in.read();
    }
}

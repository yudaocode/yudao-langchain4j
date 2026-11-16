import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;

/**
 * Audits static JDK member references against an actual Java 8 runtime.
 *
 * @author 芋道源码
 */
public final class Java8RuntimeApiAudit {
    private static final Map<String, String> results = new HashMap<>();
    private static final Map<String, Set<String>> findings = new TreeMap<>();
    private static long classes;
    private static final Set<String> bootClasses = new HashSet<>();
    private static boolean platform(String owner) {
        return owner.startsWith("java/") || bootClasses.contains(owner) || owner.startsWith("org/slf4j/");
    }
    private static void reference(String source, String owner, String name, String descriptor, boolean field) {
        if (!platform(owner) || owner.startsWith("[")) return;
        String key = owner + "." + name + descriptor;
        String problem = results.get(key);
        if (problem == null) {
            try {
                Class<?> type = Class.forName(owner.replace('/', '.'), false, ClassLoader.getSystemClassLoader());
                boolean found = field ? hasField(type, name, descriptor, new HashSet<Class<?>>())
                        : hasMethod(type, name, descriptor, new HashSet<Class<?>>());
                problem = found ? "OK" : "MISSING_MEMBER";
            } catch (ClassNotFoundException failure) { problem = "MISSING_CLASS";
            } catch (LinkageError failure) { problem = "UNRESOLVED:" + failure.getClass().getSimpleName(); }
            results.put(key, problem);
        }
        if (!"OK".equals(problem)) findings.computeIfAbsent(problem + " " + key, ignored -> new TreeSet<>()).add(source);
    }
    private static boolean hasMethod(Class<?> type, String name, String descriptor, Set<Class<?>> seen) {
        if (type == null || !seen.add(type)) return false;
        if (type == java.lang.invoke.MethodHandle.class && (name.equals("invoke") || name.equals("invokeExact"))) return true;
        if (name.equals("<init>")) {
            for (Constructor<?> constructor : type.getDeclaredConstructors())
                if (Type.getConstructorDescriptor(constructor).equals(descriptor)) return true;
            return false;
        }
        for (Method method : type.getDeclaredMethods())
            if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor)) return true;
        if (type.isInterface() && hasMethod(Object.class, name, descriptor, seen)) return true;
        if (hasMethod(type.getSuperclass(), name, descriptor, seen)) return true;
        for (Class<?> itf : type.getInterfaces()) if (hasMethod(itf, name, descriptor, seen)) return true;
        return false;
    }
    private static boolean hasField(Class<?> type, String name, String descriptor, Set<Class<?>> seen) {
        if (type == null || !seen.add(type)) return false;
        for (Field field : type.getDeclaredFields())
            if (field.getName().equals(name) && Type.getDescriptor(field.getType()).equals(descriptor)) return true;
        if (hasField(type.getSuperclass(), name, descriptor, seen)) return true;
        for (Class<?> itf : type.getInterfaces()) if (hasField(itf, name, descriptor, seen)) return true;
        return false;
    }
    private static void handle(String source, Handle handle) {
        int tag = handle.getTag();
        reference(source, handle.getOwner(), handle.getName(), handle.getDesc(), tag <= Opcodes.H_PUTSTATIC);
    }
    public static void main(String[] args) throws Exception {
        if (!System.getProperty("java.specification.version").equals("1.8")) throw new IllegalStateException("Run with Java 8");
        for (String entry : System.getProperty("sun.boot.class.path").split(File.pathSeparator)) {
            if (!entry.endsWith(".jar") || !Files.isRegularFile(Paths.get(entry))) continue;
            try (JarFile boot = new JarFile(entry)) {
                Enumeration<JarEntry> entries = boot.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.endsWith(".class")) bootClasses.add(name.substring(0, name.length() - 6));
                }
            }
        }
        List<String> jars = Files.readAllLines(Paths.get(args[0]));
        for (String jar : jars) try (JarFile archive = new JarFile(jar)) {
            Enumeration<JarEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || name.endsWith("module-info.class") || name.startsWith("META-INF/versions/")) continue;
                if (bootClasses.contains(name.substring(0, name.length() - 6))) continue; // bootstrap wins over duplicate vendor definitions
                classes++;
                final String source = jar + "!" + name;
                try (InputStream input = archive.getInputStream(entry)) {
                    new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) { reference(source, owner, name, descriptor, false); }
                                @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) { reference(source, owner, name, descriptor, true); }
                                @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
                                    handle(source, bootstrap);
                                    for (Object arg : arguments) if (arg instanceof Handle) handle(source, (Handle) arg);
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
        System.out.println("VM=" + System.getProperty("java.version") + " jars=" + jars.size() + " classes=" + classes + " unique_JDK_member_refs=" + results.size() + " findings=" + findings.size());
        for (Map.Entry<String, Set<String>> finding : findings.entrySet()) {
            System.out.println(finding.getKey() + " sources=" + finding.getValue().size());
            for (String source : finding.getValue()) System.out.println("  " + source);
        }
    }
}

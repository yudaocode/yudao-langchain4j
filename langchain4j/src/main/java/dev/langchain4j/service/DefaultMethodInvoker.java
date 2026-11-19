package dev.langchain4j.service;

import dev.langchain4j.Internal;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Invokes default methods on public and package-private service interfaces.
 *
 * @author 芋道源码
 */
@Internal
public final class DefaultMethodInvoker {

    private DefaultMethodInvoker() {}

    public static Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        if (method.canAccess(proxy)) {
            return InvocationHandler.invokeDefault(proxy, method, arguments);
        }

        Class<?> declaringInterface = method.getDeclaringClass();
        return MethodHandles.privateLookupIn(declaringInterface, MethodHandles.lookup())
                .unreflectSpecial(method, declaringInterface)
                .bindTo(proxy)
                .asFixedArity()
                .invokeWithArguments(arguments == null ? new Object[0] : arguments);
    }
}

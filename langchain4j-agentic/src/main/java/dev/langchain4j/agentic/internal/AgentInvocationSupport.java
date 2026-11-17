package dev.langchain4j.agentic.internal;

import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.agentError;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import java.util.Map;

/**
 * Preserves invocation context restoration without private Java 9 interface methods.
 *
 * @author 芋道源码
 */
final class AgentInvocationSupport {

    private AgentInvocationSupport() {}

    static Object invoke(
            AgentInvoker invoker,
            DefaultAgenticScope agenticScope,
            AgentListener listener,
            Object agent,
            AgentInvocationArguments args,
            AgentModelResponse modelResponse) {
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> callerParameters = LangChain4jManaged.current();
        LangChain4jManaged.setCurrent(
                Map.of(AgenticScope.class, agenticScope, AgentModelResponse.class, modelResponse));
        try {
            return invoker.method().invoke(agent, args.positionalArgs());
        } catch (Exception e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
            if (cause instanceof AgenticSystemSuspendedException suspended) {
                throw suspended;
            }
            AgentInvocationException invocationException =
                    new AgentInvocationException("Failed to invoke agent method: " + invoker.method(), e);
            agentError(listener, agenticScope, invoker, args.namedArgs(), invocationException);
            throw invocationException;
        } finally {
            LangChain4jManaged.restore(callerParameters);
        }
    }
}

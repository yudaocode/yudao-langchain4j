package com.ibm.watsonx.ai.core;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.event.Level;

/**
 * Preserves SDK log-level checks and lazy INFO messages on SLF4J 1.7.
 *
 * @author 芋道源码
 */
public final class WatsonxLogSupport {

    private WatsonxLogSupport() {}

    public static boolean isEnabledForLevel(Logger logger, Level level) {
        return switch (level) {
            case TRACE -> logger.isTraceEnabled();
            case DEBUG -> logger.isDebugEnabled();
            case INFO -> logger.isInfoEnabled();
            case WARN -> logger.isWarnEnabled();
            case ERROR -> logger.isErrorEnabled();
        };
    }

    public static void info(Logger logger, Supplier<String> message) {
        if (logger.isInfoEnabled()) {
            logger.info(message.get());
        }
    }
}

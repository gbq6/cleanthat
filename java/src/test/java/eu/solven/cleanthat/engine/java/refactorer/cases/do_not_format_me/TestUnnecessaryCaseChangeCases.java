package eu.solven.cleanthat.engine.java.refactorer.cases.do_not_format_me;

import eu.solven.cleanthat.engine.java.refactorer.annotations.CompareMethods;
import eu.solven.cleanthat.engine.java.refactorer.annotations.UnmodifiedCompilationUnitAsResource;
import eu.solven.cleanthat.engine.java.refactorer.annotations.UnmodifiedCompilationUnitAsString;
import eu.solven.cleanthat.engine.java.refactorer.annotations.UnmodifiedMethod;
import eu.solven.cleanthat.engine.java.refactorer.meta.IJavaparserAstMutator;
import eu.solven.cleanthat.engine.java.refactorer.mutators.UnnecessaryCaseChange;
import eu.solven.cleanthat.engine.java.refactorer.test.AJavaparserRefactorerCases;

import java.util.Locale;

public class TestUnnecessaryCaseChangeCases extends AJavaparserRefactorerCases {

    @Override
    public IJavaparserAstMutator getTransformer() {
        return new UnnecessaryCaseChange();
    }

    private static final class CustomClass {
        private CustomClass equalsIgnoreCase(String string) {
            return this;
        }

        private CustomClass toLowerCase() {
            return this;
        }
    }

    @UnmodifiedMethod
    public static class CaseCustomClassWithEqualsIgnoreCase {
        public Object pre() {
            return new CustomClass().toLowerCase().equalsIgnoreCase("lowercase");
        }
    }

    // Cases that should be replaced with equalsIgnoreCase

    @CompareMethods
    public static class CaseToLowerCaseWithLiteralEmpty {
        public Object pre(String string) {
            return string.toLowerCase().equals("");
        }

        public Object post(String string) {
            return string.equalsIgnoreCase("");
        }
    }

    @CompareMethods
    public static class CaseToUpperCaseWithLiteralEmpty {
        public Object pre(String string) {
            return string.toUpperCase().equals("");
        }

        public Object post(String string) {
            return string.equalsIgnoreCase("");
        }
    }

    @CompareMethods
    public static class CaseToLowerCaseWithLiteralLowercase {
        public Object pre(String string) {
            return string.toLowerCase().equals("lowercase");
        }

        public Object post(String string) {
            return string.equalsIgnoreCase("lowercase");
        }
    }

    @CompareMethods
    public static class CaseToUpperCaseWithLiteralUppercase {
        public Object pre(String string) {
            return string.toUpperCase().equals("UPPERCASE");
        }

        public Object post(String string) {
            return string.equalsIgnoreCase("UPPERCASE");
        }
    }

    @CompareMethods
    public static class CaseLiteralEmptyWithToLowerCaseWith {
        public Object pre(String string) {
            return "".equals(string.toLowerCase());
        }

        public Object post(String string) {
            return "".equalsIgnoreCase(string);
        }
    }

    @CompareMethods
    public static class CaseLiteralEmptyWithToUpperCase {
        public Object pre(String string) {
            return "".equals(string.toUpperCase());
        }

        public Object post(String string) {
            return "".equalsIgnoreCase(string);
        }
    }

    @CompareMethods
    public static class CaseLiteralLowercaseWithToLowerCaseWith {
        public Object pre(String string) {
            return "lowercase".equals(string.toLowerCase());
        }

        public Object post(String string) {
            return "lowercase".equalsIgnoreCase(string);
        }
    }

    @CompareMethods
    public static class CaseLiteralUppercaseWithToUpperCase {
        public Object pre(String string) {
            return "UPPERCASE".equals(string.toUpperCase());
        }

        public Object post(String string) {
            return "UPPERCASE".equalsIgnoreCase(string);
        }
    }

    @CompareMethods
    public static class CaseToLowerCaseWithToLowerCase {
        public Object pre(String left, String right) {
            return left.toLowerCase().equals(right.toLowerCase());
        }

        public Object post(String left, String right) {
            return left.equalsIgnoreCase(right);
        }
    }

    @CompareMethods
    public static class CaseToUpperCaseWithToUpperCase {
        public Object pre(String left, String right) {
            return left.toUpperCase().equals(right.toUpperCase());
        }

        public Object post(String left, String right) {
            return left.equalsIgnoreCase(right);
        }
    }

    // Cases where case change should be omitted

    @CompareMethods
    public static class CaseToLowerCaseWithEqualsIgnoreCaseToLowerCase {
        public Object pre(String left, String right) {
            return left.toLowerCase().equalsIgnoreCase(right.toLowerCase());
        }

        public Object post(String left, String right) {
            return left.equalsIgnoreCase(right);
        }
    }

    @CompareMethods
    public static class CaseToUpperCaseWithEqualsIgnoreCaseToUpperCase {
        public Object pre(String left, String right) {
            return left.toUpperCase().equalsIgnoreCase(right.toUpperCase());
        }

        public Object post(String left, String right) {
            return left.equalsIgnoreCase(right);
        }
    }

    @CompareMethods
    public static class CaseToLowerCaseWithEqualsIgnoreCaseToUpperCase {
        public Object pre(String left, String right) {
            return left.toLowerCase().equalsIgnoreCase(right.toUpperCase());
        }

        public Object post(String left, String right) {
            return left.equalsIgnoreCase(right);
        }
    }

    @CompareMethods
    public static class CaseToUpperCaseWithEqualsIgnoreCaseToLowerCase {
        public Object pre(String left, String right) {
            return left.toUpperCase().equalsIgnoreCase(right.toLowerCase());
        }

        public Object post(String left, String right) {
            return left.equalsIgnoreCase(right);
        }
    }

    @CompareMethods
    public static class CaseToLowerCaseWithEqualsIgnoreCase {
        public Object pre(String left, String right) {
            return left.toLowerCase().equalsIgnoreCase(right);
        }

        public Object post(String left, String right) {
            return left.equalsIgnoreCase(right);
        }
    }

    @CompareMethods
    public static class CaseToUpperCaseWithEqualsIgnoreCase {
        public Object pre(String left, String right) {
            return left.toUpperCase().equalsIgnoreCase(right);
        }

        public Object post(String left, String right) {
            return left.equalsIgnoreCase(right);
        }
    }

    @CompareMethods
    public static class CaseVariableWithEqualsIgnoreCaseToLowerCase {
        public Object pre(String left, String right) {
            return left.equalsIgnoreCase(right.toLowerCase());
        }

        public Object post(String left, String right) {
            return left.equalsIgnoreCase(right);
        }
    }

    @CompareMethods
    public static class CaseVariableWithEqualsIgnoreCaseToUpperCase {
        public Object pre(String left, String right) {
            return left.equalsIgnoreCase(right.toUpperCase());
        }

        public Object post(String left, String right) {
            return left.equalsIgnoreCase(right);
        }
    }

    // Cases that could be replaced, but are ignored for now

    @UnmodifiedMethod
    public static class CaseToLowerCaseWithNull {
        public Object pre(String string) {
            return string.toLowerCase().equals(null);
        }
    }

    @UnmodifiedMethod
    public static class CaseToUpperCaseWithNull {
        public Object pre(String string) {
            return string.toUpperCase().equals(null);
        }
    }

    // Cases that should be ignored as the replacement WOULD change execution behavior

    @UnmodifiedMethod
    public static class CaseToLowerCaseWithLiteralUppercase {
        public Object pre(String string) {
            return string.toLowerCase().equals("UPPERCASE");
        }
    }

    @UnmodifiedMethod
    public static class CaseToUpperCaseWithLiteralLowercase {
        public Object pre(String string) {
            return string.toUpperCase().equals("lowercase");
        }
    }

    @UnmodifiedMethod
    public static class CaseToLowerCaseWithLiteralMixedCase {
        public Object pre(String string) {
            return string.toLowerCase().equals("MixedCase");
        }
    }

    @UnmodifiedMethod
    public static class CaseToUpperCaseWithLiteralMixedCase {
        public Object pre(String string) {
            return string.toUpperCase().equals("MixedCase");
        }
    }

    @UnmodifiedMethod
    public static class CaseLiteralUppercaseWithToLowerCase {
        public Object pre(String string) {
            return "UPPERCASE".equals(string.toLowerCase());
        }
    }

    @UnmodifiedMethod
    public static class CaseLiteralLowercaseWithToUpperCase {
        public Object pre(String string) {
            return "lowercase".equals(string.toUpperCase());
        }
    }

    @UnmodifiedMethod
    public static class CaseLiteralMixedCaseWithToLowerCase {
        public Object pre(String string) {
            return "MixedCase".equals(string.toLowerCase());
        }
    }

    @UnmodifiedMethod
    public static class CaseLiteralMixedCaseWithToUpperCase {
        public Object pre(String string) {
            return "MixedCase".equals(string.toUpperCase());
        }
    }

    @UnmodifiedMethod
    public static class CaseToLowerCaseWithToUpperCase {
        public Object pre(String left, String right) {
            return left.toLowerCase().equals(right.toUpperCase());
        }
    }

    @UnmodifiedMethod
    public static class CaseToUpperCaseWithToLowerCase {
        public Object pre(String left, String right) {
            return left.toUpperCase().equals(right.toLowerCase());
        }
    }

    // Cases that should be ignored as the replacement COULD change execution behavior

    @UnmodifiedMethod
    public static class CaseToLowerCaseWithLocaleAndEqualsIgnoreCase {
        public Object pre(String string) {
            return string.toLowerCase(Locale.ENGLISH).equalsIgnoreCase("lowercase");
        }
    }

    @UnmodifiedMethod
    public static class CaseToUpperCaseWithLocaleAndEqualsIgnoreCase {
        public Object pre(String string) {
            return string.toUpperCase(Locale.ENGLISH).equalsIgnoreCase("UPPERCASE");
        }
    }

    @UnmodifiedMethod
    public static class CaseAndEqualsIgnoreCaseWithToLowerCaseAndLocale {
        public Object pre(String string) {
            return "lowercase".equalsIgnoreCase(string.toLowerCase(Locale.ENGLISH));
        }
    }

    @UnmodifiedMethod
    public static class CaseAndEqualsIgnoreCaseWithToUpperCaseAndLocale {
        public Object pre(String string) {
            return "UPPERCASE".equalsIgnoreCase(string.toUpperCase(Locale.ENGLISH));
        }
    }

    @UnmodifiedMethod
    public static class CaseToLowerCaseWithLocale {
        public Object pre(String string) {
            return string.toLowerCase(Locale.ENGLISH).equals("lowercase");
        }
    }

    @UnmodifiedMethod
    public static class CaseToUpperCaseWithLocale {
        public Object pre(String string) {
            return string.toUpperCase(Locale.ENGLISH).equals("UPPERCASE");
        }
    }

    @UnmodifiedMethod
    public static class CaseToLowerCaseWithLocaleFlipped {
        public Object pre(String string) {
            return "lowercase".equals(string.toLowerCase(Locale.ENGLISH));
        }
    }

    @UnmodifiedMethod
    public static class CaseToUpperCaseWithLocaleFlipped {
        public Object pre(String string) {
            return "UPPERCASE".equals(string.toUpperCase(Locale.ENGLISH));
        }
    }

    @UnmodifiedMethod
    public static class CaseVariableWithToLowerCase {
        public Object pre(String left, String right) {
            return left.equals(right.toLowerCase());
        }
    }

    @UnmodifiedMethod
    public static class CaseVariableWithToUpperCase {
        public Object pre(String left, String right) {
            return left.equals(right.toUpperCase());
        }
    }

    @UnmodifiedMethod
    public static class CaseToLowerCaseWithVariable {
        public Object pre(String left, String right) {
            return left.toLowerCase().equals(right);
        }
    }

    @UnmodifiedMethod
    public static class CaseToUpperCaseWithVariable {
        public Object pre(String left, String right) {
            return left.toUpperCase().equals(right);
        }
    }

    // Cases that should be ignored as there is no case change involved

    @UnmodifiedMethod
    public static class CaseLiteralEqualsLiteral {
        public Object pre() {
            return "lowercase".equals("lowercase");
        }
    }

    @UnmodifiedMethod
    public static class CaseLiteralEqualsIgnoreCaseLiteral {
        public Object pre() {
            return "lowercase".equalsIgnoreCase("lowercase");
        }
    }

    // The one that does not work
    @UnmodifiedCompilationUnitAsResource(pre = "source/do_not_format_me/UnnecessaryCaseChange/AsyncLoggerConfig.java")
    public static class CaseUnknownError { }

    // The one that works, but I'd prefer not to have
    @UnmodifiedCompilationUnitAsString(pre = "/*\n" +
            " * Licensed to the Apache Software Foundation (ASF) under one or more\n" +
            " * contributor license agreements. See the NOTICE file distributed with\n" +
            " * this work for additional information regarding copyright ownership.\n" +
            " * The ASF licenses this file to You under the Apache license, Version 2.0\n" +
            " * (the \"License\"); you may not use this file except in compliance with\n" +
            " * the License. You may obtain a copy of the License at\n" +
            " *\n" +
            " *      http://www.apache.org/licenses/LICENSE-2.0\n" +
            " *\n" +
            " * Unless required by applicable law or agreed to in writing, software\n" +
            " * distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
            " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
            " * See the license for the specific language governing permissions and\n" +
            " * limitations under the license.\n" +
            " */\n" +
            "package org.apache.logging.log4j.core.async;\n" +
            "\n" +
            "import java.util.Arrays;\n" +
            "import java.util.List;\n" +
            "import java.util.concurrent.TimeUnit;\n" +
            "\n" +
            "import org.apache.logging.log4j.Level;\n" +
            "import org.apache.logging.log4j.LogManager;\n" +
            "import org.apache.logging.log4j.core.Core;\n" +
            "import org.apache.logging.log4j.core.Filter;\n" +
            "import org.apache.logging.log4j.core.LogEvent;\n" +
            "import org.apache.logging.log4j.core.config.AppenderRef;\n" +
            "import org.apache.logging.log4j.core.config.Configuration;\n" +
            "import org.apache.logging.log4j.core.config.LoggerConfig;\n" +
            "import org.apache.logging.log4j.core.config.Node;\n" +
            "import org.apache.logging.log4j.core.config.Property;\n" +
            "import org.apache.logging.log4j.core.config.plugins.Plugin;\n" +
            "import org.apache.logging.log4j.core.config.plugins.PluginAttribute;\n" +
            "import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;\n" +
            "import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;\n" +
            "import org.apache.logging.log4j.core.config.plugins.PluginElement;\n" +
            "import org.apache.logging.log4j.core.config.plugins.PluginFactory;\n" +
            "import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;\n" +
            "import org.apache.logging.log4j.core.jmx.RingBufferAdmin;\n" +
            "import org.apache.logging.log4j.core.util.Booleans;\n" +
            "import org.apache.logging.log4j.spi.AbstractLogger;\n" +
            "import org.apache.logging.log4j.util.Strings;\n" +
            "\n" +
            "/**\n" +
            " * Asynchronous Logger object that is created via configuration and can be\n" +
            " * combined with synchronous loggers.\n" +
            " * <p>\n" +
            " * AsyncLoggerConfig is a logger designed for high throughput and low latency\n" +
            " * logging. It does not perform any I/O in the calling (application) thread, but\n" +
            " * instead hands off the work to another thread as soon as possible. The actual\n" +
            " * logging is performed in the background thread. It uses\n" +
            " * <a href=\"https://lmax-exchange.github.io/disruptor/\">LMAX Disruptor</a> for\n" +
            " * inter-thread communication.\n" +
            " * <p>\n" +
            " * To use AsyncLoggerConfig, specify {@code <asyncLogger>} or\n" +
            " * {@code <asyncRoot>} in configuration.\n" +
            " * <p>\n" +
            " * Note that for performance reasons, this logger does not include source\n" +
            " * location by default. You need to specify {@code includeLocation=\"true\"} in\n" +
            " * the configuration or any %class, %location or %line conversion patterns in\n" +
            " * your log4j.xml configuration will produce either a \"?\" character or no output\n" +
            " * at all.\n" +
            " * <p>\n" +
            " * For best performance, use AsyncLoggerConfig with the RandomAccessFileAppender or\n" +
            " * RollingRandomAccessFileAppender, with immediateFlush=false. These appenders have\n" +
            " * built-in support for the batching mechanism used by the Disruptor library,\n" +
            " * and they will flush to disk at the end of each batch. This means that even\n" +
            " * with immediateFlush=false, there will never be any items left in the buffer;\n" +
            " * all log events will all be written to disk in a very efficient manner.\n" +
            " */\n" +
            "@Plugin(name = \"asyncLogger\", category = Node.CATEGORY, printObject = true)\n" +
            "public class AsyncLoggerConfig extends LoggerConfig {\n" +
            "\n" +
            "    @PluginBuilderFactory\n" +
            "    public static <B extends Builder<B>> B newAsyncBuilder() {\n" +
            "        return new Builder<B>().asBuilder();\n" +
            "    }\n" +
            "\n" +
            "    public static class Builder<B extends Builder<B>> extends LoggerConfig.Builder<B> {\n" +
            "\n" +
            "        @Override\n" +
            "        public LoggerConfig build() {\n" +
            "            final String name = getLoggerName().equals(ROOT) ? Strings.EMPTY : getLoggerName();\n" +
            "            LevelAndRefs container = LoggerConfig.getLevelAndRefs(getLevel(), getRefs(), getLevelAndRefs(),\n" +
            "                    getConfig());\n" +
            "            return new AsyncLoggerConfig(name, container.refs,getFilter(), container.level, isAdditivity(),\n" +
            "                    getProperties(), getConfig(), includeLocation(getIncludeLocation()));\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    private static final ThreadLocal<Boolean> ASYNC_LOGGER_ENTERED = new ThreadLocal<Boolean>() {\n" +
            "        @Override\n" +
            "        protected Boolean initialValue() {\n" +
            "            return Boolean.FALSE;\n" +
            "        }\n" +
            "    };\n" +
            "\n" +
            "    private final AsyncLoggerConfigDelegate delegate;\n" +
            "\n" +
            "    protected AsyncLoggerConfig(final String name,\n" +
            "                                final List<AppenderRef> appenders, final Filter filter,\n" +
            "                                final Level level, final boolean additive,\n" +
            "                                final Property[] properties, final Configuration config,\n" +
            "                                final boolean includeLocation) {\n" +
            "        super(name, appenders, filter, level, additive, properties, config,\n" +
            "                includeLocation);\n" +
            "        delegate = config.getAsyncLoggerConfigDelegate();\n" +
            "        delegate.setLogEventFactory(getLogEventFactory());\n" +
            "    }\n" +
            "\n" +
            "    // package-protected for testing\n" +
            "    AsyncLoggerConfigDelegate getAsyncLoggerConfigDelegate() {\n" +
            "        return delegate;\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    protected void log(final LogEvent event, final LoggerConfigPredicate predicate) {\n" +
            "        // See LOG4J2-2301\n" +
            "        if (predicate == LoggerConfigPredicate.ALL &&\n" +
            "                ASYNC_LOGGER_ENTERED.get() == Boolean.FALSE &&\n" +
            "                // Optimization: AsyncLoggerConfig is identical to LoggerConfig\n" +
            "                // when no appenders are present. Avoid splitting for synchronous\n" +
            "                // and asynchronous execution paths until encountering an\n" +
            "                // AsyncLoggerConfig with appenders.\n" +
            "                hasAppenders()) {\n" +
            "            // This is the first AsnycLoggerConfig encountered by this LogEvent\n" +
            "            ASYNC_LOGGER_ENTERED.set(Boolean.TRUE);\n" +
            "            try {\n" +
            "                // Detect the first time we encounter an AsyncLoggerConfig. We must log\n" +
            "                // to all non-async loggers first.\n" +
            "                super.log(event, LoggerConfigPredicate.SYNCHRONOUS_ONLY);\n" +
            "                // Then pass the event to the background thread where\n" +
            "                // all async logging is executed. It is important this\n" +
            "                // happens at most once and after all synchronous loggers\n" +
            "                // have been invoked, because we lose parameter references\n" +
            "                // from reusable messages.\n" +
            "                logToAsyncDelegate(event);\n" +
            "            } finally {\n" +
            "                ASYNC_LOGGER_ENTERED.set(Boolean.FALSE);\n" +
            "            }\n" +
            "        } else {\n" +
            "            super.log(event, predicate);\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    protected void callAppenders(final LogEvent event) {\n" +
            "        super.callAppenders(event);\n" +
            "    }\n" +
            "\n" +
            "    private void logToAsyncDelegate(final LogEvent event) {\n" +
            "        if (!isFiltered(event)) {\n" +
            "            // Passes on the event to a separate thread that will call\n" +
            "            // asyncCallAppenders(LogEvent).\n" +
            "            populateLazilyInitializedFields(event);\n" +
            "            if (!delegate.tryEnqueue(event, this)) {\n" +
            "                handleQueueFull(event);\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    private void handleQueueFull(final LogEvent event) {\n" +
            "        if (AbstractLogger.getRecursionDepth() > 1) { // LOG4J2-1518, LOG4J2-2031\n" +
            "            // If queue is full AND we are in a recursive call, call appender directly to prevent deadlock\n" +
            "            AsyncQueueFullMessageUtil.logWarningToStatusLogger();\n" +
            "            logToAsyncLoggerConfigsOnCurrentThread(event);\n" +
            "        } else {\n" +
            "            // otherwise, we leave it to the user preference\n" +
            "            final EventRoute eventRoute = delegate.getEventRoute(event.getLevel());\n" +
            "            eventRoute.logMessage(this, event);\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    private void populateLazilyInitializedFields(final LogEvent event) {\n" +
            "        event.getSource();\n" +
            "        event.getThreadName();\n" +
            "    }\n" +
            "\n" +
            "    void logInBackgroundThread(final LogEvent event) {\n" +
            "        delegate.enqueueEvent(event, this);\n" +
            "    }\n" +
            "\n" +
            "    /**\n" +
            "     * Called by AsyncLoggerConfigHelper.RingBufferLog4jEventHandler.\n" +
            "     *\n" +
            "     * This method will log the provided event to only configs of type {@link AsyncLoggerConfig} (not\n" +
            "     * default {@link LoggerConfig} definitions), which will be invoked on the <b>calling thread</b>.\n" +
            "     */\n" +
            "    void logToAsyncLoggerConfigsOnCurrentThread(final LogEvent event) {\n" +
            "        log(event, LoggerConfigPredicate.ASYNCHRONOUS_ONLY);\n" +
            "    }\n" +
            "\n" +
            "    private String displayName() {\n" +
            "        return LogManager.ROOT_LOGGER_NAME.equals(getName()) ? LoggerConfig.ROOT : getName();\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public void start() {\n" +
            "        LOGGER.trace(\"AsyncLoggerConfig[{}] starting...\", displayName());\n" +
            "        super.start();\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public boolean stop(final long timeout, final TimeUnit timeUnit) {\n" +
            "        setStopping();\n" +
            "        super.stop(timeout, timeUnit, false);\n" +
            "        LOGGER.trace(\"AsyncLoggerConfig[{}] stopping...\", displayName());\n" +
            "        setStopped();\n" +
            "        return true;\n" +
            "    }\n" +
            "\n" +
            "    /**\n" +
            "     * Creates and returns a new {@code RingBufferAdmin} that instruments the\n" +
            "     * ringbuffer of this {@code AsyncLoggerConfig}.\n" +
            "     *\n" +
            "     * @param contextName name of the {@code LoggerContext}\n" +
            "     * @return a new {@code RingBufferAdmin} that instruments the ringbuffer\n" +
            "     */\n" +
            "    public RingBufferAdmin createRingBufferAdmin(final String contextName) {\n" +
            "        return delegate.createRingBufferAdmin(contextName, getName());\n" +
            "    }\n" +
            "\n" +
            "    /**\n" +
            "     * Factory method to create a LoggerConfig.\n" +
            "     *\n" +
            "     * @param additivity True if additive, false otherwise.\n" +
            "     * @param levelName The Level to be associated with the Logger.\n" +
            "     * @param loggerName The name of the Logger.\n" +
            "     * @param includeLocation \"true\" if location should be passed downstream\n" +
            "     * @param refs An array of Appender names.\n" +
            "     * @param properties Properties to pass to the Logger.\n" +
            "     * @param config The Configuration.\n" +
            "     * @param filter A Filter.\n" +
            "     * @return A new LoggerConfig.\n" +
            "     * @deprecated use {@link #createLogger(boolean, Level, String, String, AppenderRef[], Property[], Configuration, Filter)}\n" +
            "     */\n" +
            "    @Deprecated\n" +
            "    public static LoggerConfig createLogger(\n" +
            "            final String additivity,\n" +
            "            final String levelName,\n" +
            "            final String loggerName,\n" +
            "            final String includeLocation,\n" +
            "            final AppenderRef[] refs,\n" +
            "            final Property[] properties,\n" +
            "            final Configuration config,\n" +
            "            final Filter filter) {\n" +
            "        if (loggerName == null) {\n" +
            "            LOGGER.error(\"Loggers cannot be configured without a name\");\n" +
            "            return null;\n" +
            "        }\n" +
            "\n" +
            "        final List<AppenderRef> appenderRefs = Arrays.asList(refs);\n" +
            "        Level level;\n" +
            "        try {\n" +
            "            level = Level.toLevel(levelName, Level.ERROR);\n" +
            "        } catch (final Exception ex) {\n" +
            "            LOGGER.error(\n" +
            "                    \"Invalid Log level specified: {}. Defaulting to Error\",\n" +
            "                    levelName);\n" +
            "            level = Level.ERROR;\n" +
            "        }\n" +
            "        final String name = loggerName.equals(LoggerConfig.ROOT) ? Strings.EMPTY : loggerName;\n" +
            "        final boolean additive = Booleans.parseBoolean(additivity, true);\n" +
            "\n" +
            "        return new AsyncLoggerConfig(name, appenderRefs, filter, level,\n" +
            "                additive, properties, config, includeLocation(includeLocation));\n" +
            "    }\n" +
            "\n" +
            "    /**\n" +
            "     * Factory method to create a LoggerConfig.\n" +
            "     *\n" +
            "     * @param additivity True if additive, false otherwise.\n" +
            "     * @param level The Level to be associated with the Logger.\n" +
            "     * @param loggerName The name of the Logger.\n" +
            "     * @param includeLocation \"true\" if location should be passed downstream\n" +
            "     * @param refs An array of Appender names.\n" +
            "     * @param properties Properties to pass to the Logger.\n" +
            "     * @param config The Configuration.\n" +
            "     * @param filter A Filter.\n" +
            "     * @return A new LoggerConfig.\n" +
            "     * @since 3.0\n" +
            "     */\n" +
            "    @Deprecated\n" +
            "    public static LoggerConfig createLogger(\n" +
            "            @PluginAttribute(value = \"additivity\", defaultBoolean = true) final boolean additivity,\n" +
            "            @PluginAttribute(\"level\") final Level level,\n" +
            "            @Required(message = \"Loggers cannot be configured without a name\") @PluginAttribute(\"name\") final String loggerName,\n" +
            "            @PluginAttribute(\"includeLocation\") final String includeLocation,\n" +
            "            @PluginElement(\"AppenderRef\") final AppenderRef[] refs,\n" +
            "            @PluginElement(\"Properties\") final Property[] properties,\n" +
            "            @PluginConfiguration final Configuration config,\n" +
            "            @PluginElement(\"Filter\") final Filter filter) {\n" +
            "        final String name = loggerName.equals(ROOT) ? Strings.EMPTY : loggerName;\n" +
            "        return new AsyncLoggerConfig(name, Arrays.asList(refs), filter, level, additivity, properties, config,\n" +
            "                includeLocation(includeLocation));\n" +
            "    }\n" +
            "\n" +
            "    // Note: for asynchronous loggers, includeLocation default is FALSE\n" +
            "    protected static boolean includeLocation(final String includeLocationConfigValue) {\n" +
            "        return Boolean.parseBoolean(includeLocationConfigValue);\n" +
            "    }\n" +
            "\n" +
            "    /**\n" +
            "     * An asynchronous root Logger.\n" +
            "     */\n" +
            "    @Plugin(name = \"asyncRoot\", category = Core.CATEGORY_NAME, printObject = true)\n" +
            "    public static class RootLogger extends LoggerConfig {\n" +
            "\n" +
            "        @PluginBuilderFactory\n" +
            "        public static <B extends Builder<B>> B newAsyncRootBuilder() {\n" +
            "            return new Builder<B>().asBuilder();\n" +
            "        }\n" +
            "\n" +
            "        public static class Builder<B extends Builder<B>> extends RootLogger.Builder<B> {\n" +
            "\n" +
            "            @Override\n" +
            "            public LoggerConfig build() {\n" +
            "                LevelAndRefs container = LoggerConfig.getLevelAndRefs(getLevel(), getRefs(), getLevelAndRefs(),\n" +
            "                        getConfig());\n" +
            "                return new AsyncLoggerConfig(LogManager.ROOT_LOGGER_NAME, container.refs, getFilter(), container.level,\n" +
            "                        isAdditivity(), getProperties(), getConfig(),\n" +
            "                        AsyncLoggerConfig.includeLocation(getIncludeLocation()));\n" +
            "            }\n" +
            "        }\n" +
            "\n" +
            "        /**\n" +
            "         * @deprecated use {@link #createLogger(String, Level, String, AppenderRef[], Property[], Configuration, Filter)}\n" +
            "         */\n" +
            "        @Deprecated\n" +
            "        public static LoggerConfig createLogger(\n" +
            "                final String additivity,\n" +
            "                final String levelName,\n" +
            "                final String includeLocation,\n" +
            "                final AppenderRef[] refs,\n" +
            "                final Property[] properties,\n" +
            "                final Configuration config,\n" +
            "                final Filter filter) {\n" +
            "            final List<AppenderRef> appenderRefs = Arrays.asList(refs);\n" +
            "            Level level = null;\n" +
            "            try {\n" +
            "                level = Level.toLevel(levelName, Level.ERROR);\n" +
            "            } catch (final Exception ex) {\n" +
            "                LOGGER.error(\"Invalid Log level specified: {}. Defaulting to Error\", levelName);\n" +
            "                level = Level.ERROR;\n" +
            "            }\n" +
            "            final boolean additive = Booleans.parseBoolean(additivity, true);\n" +
            "            return new AsyncLoggerConfig(LogManager.ROOT_LOGGER_NAME,\n" +
            "                    appenderRefs, filter, level, additive, properties, config,\n" +
            "                    AsyncLoggerConfig.includeLocation(includeLocation));\n" +
            "        }\n" +
            "\n" +
            "        /**\n" +
            "         *\n" +
            "         */\n" +
            "        @Deprecated\n" +
            "        public static LoggerConfig createLogger(\n" +
            "                @PluginAttribute(\"additivity\") final String additivity,\n" +
            "                @PluginAttribute(\"level\") final Level level,\n" +
            "                @PluginAttribute(\"includeLocation\") final String includeLocation,\n" +
            "                @PluginElement(\"AppenderRef\") final AppenderRef[] refs,\n" +
            "                @PluginElement(\"Properties\") final Property[] properties,\n" +
            "                @PluginConfiguration final Configuration config,\n" +
            "                @PluginElement(\"Filter\") final Filter filter) {\n" +
            "            final List<AppenderRef> appenderRefs = Arrays.asList(refs);\n" +
            "            final Level actualLevel = level == null ? Level.ERROR : level;\n" +
            "            final boolean additive = Booleans.parseBoolean(additivity, true);\n" +
            "            return new AsyncLoggerConfig(LogManager.ROOT_LOGGER_NAME, appenderRefs, filter, actualLevel, additive,\n" +
            "                    properties, config, AsyncLoggerConfig.includeLocation(includeLocation));\n" +
            "        }\n" +
            "    }\n" +
            "}\n")
    public static class CaseUnknownErrorOriginal { }

}

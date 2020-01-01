package org.mockserver.persistence;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.ui.MockServerMatcherNotifier;
import org.slf4j.event.Level;

import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.slf4j.event.Level.*;

public class ExpectationFileWatcher {

    private final ExpectationSerializer expectationSerializer;
    private final MockServerLogger mockServerLogger;
    private final MockServerMatcher mockServerMatcher;
    private FileWatcher fileWatcher;

    public ExpectationFileWatcher(MockServerLogger mockServerLogger, MockServerMatcher mockServerMatcher) {
        if (ConfigurationProperties.watchInitializationJson()) {
            this.expectationSerializer = new ExpectationSerializer(mockServerLogger);
            this.mockServerLogger = mockServerLogger;
            this.mockServerMatcher = mockServerMatcher;
            try {
                fileWatcher = new FileWatcher(ConfigurationProperties.initializationJsonPath(), () -> {
                    if (MockServerLogger.isEnabled(DEBUG)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(LogEntry.LogMessageType.DEBUG)
                                .setLogLevel(DEBUG)
                                .setMessageFormat("Expectation file watcher detected modification on file " + ConfigurationProperties.initializationJsonPath() + " updating expectations")
                        );
                    }
                    addExpectationsFromInitializer();
                }, throwable -> {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(LogEntry.LogMessageType.WARN)
                            .setLogLevel(WARN)
                            .setMessageFormat("Exception while processing expectation file update " + throwable.getMessage())
                            .setThrowable(throwable)
                    );
                });
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.EXCEPTION)
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("Exception creating file watcher for " + ConfigurationProperties.initializationJsonPath())
                        .setThrowable(throwable)
                );
            }
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.INFO)
                    .setLogLevel(INFO)
                    .setMessageFormat("Created expectation file watcher for " + ConfigurationProperties.initializationJsonPath())
            );
        } else {
            this.expectationSerializer = null;
            this.mockServerLogger = null;
            this.mockServerMatcher = null;
        }
    }

    private void addExpectationsFromInitializer() {
        Expectation[] expectations = retrieveExpectationsFromJson();
        if (MockServerLogger.isEnabled(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.TRACE)
                    .setLogLevel(TRACE)
                    .setMessageFormat("Updating expectations from " + ConfigurationProperties.initializationJsonPath() + "{}")
                    .setArguments(Arrays.asList(expectations))
            );
        }
        mockServerMatcher.update(expectations, MockServerMatcherNotifier.Cause.FILE_WATCHER);
    }

    private Expectation[] retrieveExpectationsFromJson() {
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        if (isNotBlank(initializationJsonPath)) {
            try {
                String jsonExpectations = FileReader.readFileFromClassPathOrPath(initializationJsonPath);
                if (isNotBlank(jsonExpectations)) {
                    return expectationSerializer.deserializeArray(jsonExpectations, true);
                } else {
                    return new Expectation[0];
                }
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(SERVER_CONFIGURATION)
                        .setLogLevel(WARN)
                        .setMessageFormat("Exception while loading JSON initialization file with file watcher, ignoring file")
                        .setThrowable(throwable)
                );
            }
        }
        return new Expectation[0];
    }

    public void stop() {
        if (fileWatcher != null) {
            fileWatcher.setRunning(false);
        }
    }
}

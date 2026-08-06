package org.jetbrains.sbt.process.mock;

import java.nio.file.Path;

// Keep these command strings in sync with community/sbt/sbt-mock-process/src/MockSbtProcess.java and the mock-process runtime tests.
public final class MockSbtProcessCommands {
    public static final String Exit = "exit";
    public static final String WaitForFilePrefix = "mockWaitForFile ";
    public static final String SlowShutdownReleaseFileProperty = "org.jetbrains.sbt.mock.process.slowShutdownReleaseFile";
    public static final String SlowShutdownStartedFileProperty = "org.jetbrains.sbt.mock.process.slowShutdownStartedFile";
    public static final String WaitForFileMarkerPrefix = "[mock-sbt-marker]";
    public static final String JdwpListeningBeforePrompt = "mockJdwpListeningBeforePrompt";
    public static final String JdwpListeningAfterPrompt = "mockJdwpListeningAfterPrompt";
    public static final String JdwpListeningGluedToPrompt = "mockJdwpListeningGluedToPrompt";
    public static final String JdwpListeningMessage = "Listening for transport dt_socket at address: 12345";
    public static final String WroteStructureOutputPrefix = "wrote structure to: ";
    private static final String JdwpListeningCommandOutputFormat = "mock jdwp listening command output: %s";
    // Synthetic marker printed by MockSbtProcess.waitForFile; tests assert this exact fragment instead of ordinary sbt output.
    private static final String WaitForFileOutputFormat = WaitForFileMarkerPrefix + " waiting for file: %s";
    // Synthetic marker printed by MockSbtProcess.waitForFile after the release file appears.
    private static final String ResumedAfterWaitingForFileOutputFormat = WaitForFileMarkerPrefix + " resumed after waiting for file: %s";

    private MockSbtProcessCommands() {
    }

    public static String jdwpListeningCommandOutput(String command) {
        return String.format(JdwpListeningCommandOutputFormat, command);
    }

    public static String waitForFileCommand(Path file) {
        return WaitForFilePrefix + "\"" + file + "\"";
    }

    public static String waitingForFileOutput(Path file) {
        return String.format(WaitForFileOutputFormat, file);
    }

    public static String resumedAfterWaitingForFileOutput(Path file) {
        return String.format(ResumedAfterWaitingForFileOutputFormat, file);
    }
}

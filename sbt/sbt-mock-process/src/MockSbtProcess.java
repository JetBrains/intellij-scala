import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.sbt.process.mock.MockSbtProcessCommands;

public final class MockSbtProcess {
    private static final String StructureOutputFileProperty = "sbt.structure.outputFile";

    private static final class Commands {
        private static final String Exit = MockSbtProcessCommands.Exit;
        private static final String WaitForFilePrefix = MockSbtProcessCommands.WaitForFilePrefix;
        private static final String MockJdwpListeningBeforePrompt = MockSbtProcessCommands.JdwpListeningBeforePrompt;
        private static final String MockJdwpListeningAfterPrompt = MockSbtProcessCommands.JdwpListeningAfterPrompt;
        private static final String MockJdwpListeningGluedToPrompt = MockSbtProcessCommands.JdwpListeningGluedToPrompt;
        private static final String DumpStructureTo = "dumpStructureTo";
        private static final String DumpStructure = "dumpStructure";
    }

    // Keep in sync with VmOptions in org.jetbrains.sbt.process.mock.MockSbtProcessForTests.
    private static final class VmOptions {
        private static final String ModeProperty = "org.jetbrains.sbt.mock.process.mode";

        private static final String NoShellMode = "no-shell";
        private static final String NoShellStdinMode = "no-shell-stdin";
        private static final String OldShellMode = "old-shell";
        private static final String NewShellMode = "new-shell";

        private static boolean isShellMode(String mode) {
            return OldShellMode.equals(mode) || NewShellMode.equals(mode);
        }
    }

    private static final AtomicBoolean FinalDebugPrinted = new AtomicBoolean(false);

    public static void main(String[] args) throws IOException, InterruptedException {
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> {
                    try {
                        waitForSlowShutdownReleaseFileIfConfigured();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (IOException e) {
                        Log.error("exception while waiting for slow shutdown release file: " + e);
                    }
                    Log.debugFinal("shutdown hook");
                },
                "MockSbtProcess-shutdown"
        ));

        try {
            String mode = System.getProperty(VmOptions.ModeProperty, VmOptions.NoShellMode);
            logStartupDebug("started (" + mode + ")", VmOptions.isShellMode(mode));
            if (VmOptions.isShellMode(mode)) {
                printIgnoredArgs(args);
                runStdinCommandLoop(mode);
            } else if (VmOptions.NoShellStdinMode.equals(mode)) {
                printIgnoredArgs(args);
                runStdinCommandLoop(null);
            } else {
                String command = toCommandText(args);
                processCommand(command, false);
            }
        } catch (Exception e) {
            Log.error("exception: " + e);
            throw e;
        } finally {
            Log.debugFinal("finally");
        }
    }

    private static void printIgnoredArgs(String[] args) {
        String argsText = toCommandText(args);
        if (!argsText.isEmpty()) {
            Log.warn("ignored args=" + argsText);
        }
    }

    private static String toCommandText(String[] args) {
        return String.join(" ", args).trim();
    }

    private static void runStdinCommandLoop(String promptMode) throws IOException, InterruptedException {
        logStartupDebug("starting command loop" + (promptMode == null ? "" : " (" + promptMode + ")"), promptMode != null);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            processCommandLoop(reader, promptMode);
        }
    }

    private static void processCommandLoop(BufferedReader reader, String promptMode) throws IOException, InterruptedException {
        boolean shellMode = promptMode != null;
        logStartupDebug("listening for input line...", promptMode != null);
        printPromptIfNeeded(promptMode);

        String line;
        while ((line = reader.readLine()) != null) {
            echoInputLineIfNeeded(line, promptMode);
            Log.debug("received line=" + line, shellMode);
            String command = line.trim();
            if (command.equals(Commands.Exit)) {
                Log.debug("exit command received", shellMode);
                return;
            }

            processCommand(command, shellMode);
            if (command.equals(Commands.MockJdwpListeningBeforePrompt)) {
                printMockJdwpListeningMessage();
            }
            delayMockJdwpPromptIfNeeded(command);
            if (command.equals(Commands.MockJdwpListeningGluedToPrompt) && promptMode != null) {
                printPromptGluedToMockJdwpListeningMessage(promptMode);
            } else {
                printPromptIfNeeded(promptMode);
            }
            if (command.equals(Commands.MockJdwpListeningAfterPrompt)) {
                printMockJdwpListeningMessage();
            }
        }

        Log.debug("input stream closed", shellMode);
    }

    private static void echoInputLineIfNeeded(String line, String promptMode) {
        if (promptMode != null) {
            // Real sbt/JLine renders the submitted command line as terminal input. The mock does not emulate terminal
            // mode, so shell modes explicitly print the raw line to keep prompt-to-command-to-prompt ordering realistic.
            System.out.println(line);
            System.out.flush();
        }
    }

    private static void logStartupDebug(String message, boolean shellMode) {
        // In shell mode these startup markers must stay ordered with the prompt on stdout; stderr can be observed later.
        if (shellMode) {
            Log.debugStdout(message);
        } else {
            Log.debug(message);
        }
    }

    private static void printPromptIfNeeded(String mode) {
        if (mode != null) {
            printPrompt(mode);
        }
    }

    private static void processCommand(String command, boolean shellMode) throws IOException, InterruptedException {
        Log.debug("[processCommand] command=" + command, shellMode);

        if (command.isEmpty()) {
            Log.debug("ignoring blank command", shellMode);
            return;
        }

        if (command.startsWith(Commands.WaitForFilePrefix)) {
            String filePath = command.substring(Commands.WaitForFilePrefix.length()).trim();
            waitForFile(filePath);
        }

        boolean printJdwpListeningOutput = command.equals(Commands.MockJdwpListeningBeforePrompt) ||
                command.equals(Commands.MockJdwpListeningAfterPrompt) ||
                command.equals(Commands.MockJdwpListeningGluedToPrompt);
        if (printJdwpListeningOutput) {
            Log.info(MockSbtProcessCommands.jdwpListeningCommandOutput(command));
        }

        Path structureFile = extractStructureFile(command);
        if (structureFile != null) {
            DummyStructure.writeDummyProjectStructure(structureFile);
            Log.info(MockSbtProcessCommands.WroteStructureOutputPrefix + structureFile);
        }
    }

    private static void waitForFile(String filePath) throws InterruptedException {
        Path file = Paths.get(unquote(filePath));
        Log.info(MockSbtProcessCommands.waitingForFileOutput(file));

        while (!Files.exists(file)) {
            Thread.sleep(50);
        }

        Log.info(MockSbtProcessCommands.resumedAfterWaitingForFileOutput(file));
    }

    private static void waitForSlowShutdownReleaseFileIfConfigured() throws IOException, InterruptedException {
        String releaseFileText = System.getProperty(MockSbtProcessCommands.SlowShutdownReleaseFileProperty);
        if (releaseFileText == null || releaseFileText.trim().isEmpty()) {
            return;
        }

        createMarkerFileIfConfigured(MockSbtProcessCommands.SlowShutdownStartedFileProperty);

        Path releaseFile = Paths.get(releaseFileText);
        while (!Files.exists(releaseFile)) {
            Thread.sleep(50);
        }
    }

    private static void createMarkerFileIfConfigured(String propertyName) throws IOException {
        String fileText = System.getProperty(propertyName);
        if (fileText == null || fileText.trim().isEmpty()) {
            return;
        }

        Path file = Paths.get(fileText);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, "started".getBytes(StandardCharsets.UTF_8));
    }

    private static Path extractStructureFile(String command) {
        String[] commands = command.split(";");
        for (String part : commands) {
            int dumpStructureToIndex = part.indexOf(Commands.DumpStructureTo);
            if (dumpStructureToIndex < 0) {
                continue;
            }

            String path = part.substring(dumpStructureToIndex + Commands.DumpStructureTo.length()).trim();
            if (path.isEmpty()) {
                continue;
            }

            return Paths.get(unquote(path));
        }

        // NOTE: this might be not needed any more since "989fe7a7" commit (but needs to be checked)
        if (command.contains(Commands.DumpStructure)) {
            String structureOutputFile = System.getProperty(StructureOutputFileProperty);
            if (structureOutputFile != null) {
                String path = structureOutputFile.trim();
                if (!path.isEmpty()) {
                    return Paths.get(path);
                }
            }
        }

        return null;
    }

    private static String unquote(String text) {
        if (text.length() >= 2) {
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return text.substring(1, text.length() - 1);
            }
        }
        return text;
    }

    private static class DummyStructure {

        public static void writeDummyProjectStructure(Path structureFile) throws IOException {
            Path parent = structureFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(structureFile, dummyProjectStructureXml().getBytes(StandardCharsets.UTF_8));
        }

        private static String dummyProjectStructureXml() {
            Path projectRoot = Paths.get("").toAbsolutePath().normalize();
            Path sourceRoot = projectRoot.resolve("src");
            Path targetRoot = projectRoot.resolve("target");
            Path classesRoot = targetRoot.resolve("classes");
            Path testClassesRoot = targetRoot.resolve("test-classes");
            Path projectTargetRoot = projectRoot.resolve("project").resolve("target");
            String buildUri = projectRoot.toUri().toString();
            String projectRootPath = projectRoot.toString();

            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<structure sbt=\"1.12.1\">\n" +
                    "  <build>\n" +
                    "    <uri>" + xml(buildUri) + "</uri>\n" +
                    "  </build>\n" +
                    "  <project>\n" +
                    "    <id>mock-sbt-test</id>\n" +
                    "    <buildURI>" + xml(buildUri) + "</buildURI>\n" +
                    "    <name>mock-sbt-test</name>\n" +
                    "    <organization></organization>\n" +
                    "    <version>0.1.0-SNAPSHOT</version>\n" +
                    "    <base>" + xml(projectRootPath) + "</base>\n" +
                    "    <target>" + xml(targetRoot.toString()) + "</target>\n" +
                    "    <compileOrder>Mixed</compileOrder>\n" +
                    "    <mainSourceDir>" + xml(sourceRoot.toString()) + "</mainSourceDir>\n" +
                    "    <configuration id=\"compile\">\n" +
                    "      <sources managed=\"false\">" + xml(sourceRoot.toString()) + "</sources>\n" +
                    "      <classes>" + xml(classesRoot.toString()) + "</classes>\n" +
                    "    </configuration>\n" +
                    "    <configuration id=\"test\">\n" +
                    "      <classes>" + xml(testClassesRoot.toString()) + "</classes>\n" +
                    "    </configuration>\n" +
                    "    <dependencies>\n" +
                    "      <projects>\n" +
                    "        <forProduction></forProduction>\n" +
                    "        <forTest></forTest>\n" +
                    "      </projects>\n" +
                    "      <modules>\n" +
                    "        <forProduction></forProduction>\n" +
                    "        <forTest></forTest>\n" +
                    "      </modules>\n" +
                    "      <jars>\n" +
                    "        <forProduction></forProduction>\n" +
                    "        <forTest></forTest>\n" +
                    "      </jars>\n" +
                    "    </dependencies>\n" +
                    "  </project>\n" +
                    "  <localCachePath>" + xml(projectTargetRoot.toString()) + "</localCachePath>\n" +
                    "</structure>\n";
        }

        private static String xml(String text) {
            return text
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
        }
    }

    private static void printPrompt(String mode) {
        System.out.print(promptText(mode));
        System.out.flush();
    }

    private static String promptText(String mode) {
        return VmOptions.NewShellMode.equals(mode)
                ? "sbt:mock>"
                : "[IJ]>";
    }

    private static void printMockJdwpListeningMessage() {
        System.err.println(MockSbtProcessCommands.JdwpListeningMessage);
        System.err.flush();
    }

    private static void printPromptGluedToMockJdwpListeningMessage(String mode) {
        System.out.print(promptText(mode) + MockSbtProcessCommands.JdwpListeningMessage);
        System.out.flush();
    }

    private static void delayMockJdwpPromptIfNeeded(String command) throws InterruptedException {
        if (command.equals(Commands.MockJdwpListeningBeforePrompt) || command.equals(Commands.MockJdwpListeningAfterPrompt)) {
            Thread.sleep(500);
        }
    }

    private static class Log {
        private static void debug(String message) {
            System.err.println("[debug] " + message);
            System.err.flush();
        }

        private static void debug(String message, boolean stdout) {
            if (stdout) {
                debugStdout(message);
            } else {
                debug(message);
            }
        }

        private static void debugStdout(String message) {
            System.out.println("[debug] " + message);
            System.out.flush();
        }

        private static void info(String message) {
            System.out.println("[info] " + message);
            System.out.flush();
        }

        private static void error(String message) {
            System.err.println("[error] " + message);
            System.err.flush();
        }

        private static void warn(String message) {
            System.err.println("[warn] " + message);
            System.err.flush();
        }

        private static void debugFinal(String source) {
            if (VmOptions.isShellMode(System.getProperty(VmOptions.ModeProperty, VmOptions.NoShellMode))) {
                return;
            }
            if (FinalDebugPrinted.compareAndSet(false, true)) {
                debug("finished (" + source + ")");
            }
        }
    }
}

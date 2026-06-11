import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MockSbtProcess {
    private static final String WaitForFileCommandPrefix = "mockWaitForFile ";

    // Keep in sync with VmOptions in org.jetbrains.sbt.process.mock.MockSbtProcessForTests.
    private static final class VmOptions {
        private static final String ModeProperty = "org.jetbrains.sbt.mock.process.mode";

        private static final String NoShellMode = "no-shell";
        private static final String NoShellStdinMode = "no-shell-stdin";
        private static final String OldShellMode = "old-shell";
        private static final String NewShellMode = "new-shell";
    }

    private static final AtomicBoolean FinalDebugPrinted = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> debugFinal("shutdown hook"),
                "MockSbtProcess-shutdown"
        ));

        try {
            String mode = System.getProperty(VmOptions.ModeProperty, VmOptions.NoShellMode);
            debug("started");
            debug("mode=" + mode);
            if (VmOptions.OldShellMode.equals(mode) || VmOptions.NewShellMode.equals(mode)) {
                runShell(mode);
            } else if (VmOptions.NoShellStdinMode.equals(mode)) {
                runNonShellFromStdin(args);
            } else {
                runNonShell(args);
            }
        } catch (Exception e) {
            error("exception: " + e);
        } finally {
            debugFinal("finally");
        }
    }

    private static void runShell(String mode) throws IOException, InterruptedException {
        debug("shell mode: starting command loop");
        printPrompt(mode);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            debug("shell mode: listening for input line...");
            while ((line = reader.readLine()) != null) {
                debug("shell mode: received line=" + line);
                String command = line.trim();
                if (command.equals("exit")) {
                    debug("shell mode: exit command received");
                    return;
                }

                if (command.isEmpty()) {
                    debug("shell mode: ignoring blank command");
                } else {
                    processCommand(command);
                }
                printPrompt(mode);
            }
        }

        debug("shell mode: input stream closed");
    }

    private static void runNonShell(String[] args) throws IOException, InterruptedException {
        String command = String.join(" ", args).trim();
        debug("non-shell mode: command=" + command);
        if (!command.isEmpty()) {
            processCommand(command);
        }
    }

    private static void runNonShellFromStdin(String[] args) throws IOException, InterruptedException {
        String command = String.join(" ", args).trim();
        debug("non-shell stdin mode: command=" + command);
        if (!command.isEmpty()) {
            info("mock sbt launcher args: " + command);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String inputCommand = line.trim();
                debug("non-shell stdin mode: received line=" + inputCommand);
                if (inputCommand.equals("exit")) {
                    return;
                }
                if (!inputCommand.isEmpty()) {
                    processCommand(inputCommand);
                }
            }
        }
    }

    private static void processCommand(String command) throws IOException, InterruptedException {
        debug("shell mode: accepting command=" + command);
        if (command.startsWith(WaitForFileCommandPrefix)) {
            String filePath = command.substring(WaitForFileCommandPrefix.length()).trim();
            waitForFile(filePath);
        }

        info("mock sbt accepted: " + command);

        Path structureFile = extractStructureFile(command);
        if (structureFile != null) {
            writeDummyProjectStructure(structureFile);
            info("mock sbt wrote structure to: " + structureFile);
        }
    }

    private static void waitForFile(String filePath) throws InterruptedException {
        Path file = Paths.get(unquote(filePath));
        info("mock sbt waiting for file: " + file);

        while (!Files.exists(file)) {
            Thread.sleep(50);
        }

        info("mock sbt resumed after file: " + file);
    }

    private static Path extractStructureFile(String command) {
        String[] commands = command.split(";");
        for (String part : commands) {
            int dumpStructureToIndex = part.indexOf("dumpStructureTo");
            if (dumpStructureToIndex < 0) {
                continue;
            }

            String path = part.substring(dumpStructureToIndex + "dumpStructureTo".length()).trim();
            if (path.isEmpty()) {
                continue;
            }

            return Paths.get(unquote(path));
        }

        if (command.contains("dumpStructure")) {
            String structureOutputFile = System.getProperty("sbt.structure.outputFile");
            if (structureOutputFile != null && !structureOutputFile.trim().isEmpty()) {
                return Paths.get(structureOutputFile);
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

    private static void writeDummyProjectStructure(Path structureFile) throws IOException {
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

    private static void printPrompt(String mode) {
        if (VmOptions.NewShellMode.equals(mode)) {
            debug("printing new-shell prompt");
            System.out.print("sbt:mock>");
        } else {
            debug("printing legacy-shell prompt");
            System.out.print("[IJ]>");
        }
        System.out.flush();
    }

    private static void debug(String message) {
        System.err.println("[debug] MockSbtProcess: " + message);
        System.err.flush();
    }

    private static void info(String message) {
        System.out.println("[info] " + message);
        System.out.flush();
    }

    private static void error(String message) {
        System.err.println("[error] MockSbtProcess: " + message);
        System.err.flush();
    }

    private static void debugFinal(String source) {
        if (FinalDebugPrinted.compareAndSet(false, true)) {
            debug("finished (" + source + ")");
        }
    }
}

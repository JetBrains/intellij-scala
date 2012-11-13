package org.jetbrains.jps.incremental.scala;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.ChunkBuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;

import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.*;
import static org.jetbrains.jps.incremental.scala.Utilities.*;
import static org.jetbrains.jps.incremental.scala.Utilities.toCanonicalPath;

/**
 * @author Pavel Fatin
 */
public class ScalaBuilder extends ModuleLevelBuilder {
  public static final String BUILDER_NAME = "scala";
  public static final String BUILDER_DESCRIPTION = "Scala builder";
  private static Class RUNNER_CLASS = ClassRunner.class;

  public static final String SCALA_EXTENSION = "scala";

  protected ScalaBuilder() {
    super(BuilderCategory.TRANSLATOR);
  }

  private static boolean isScalaFile(String path) {
    return path.endsWith(SCALA_EXTENSION);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return BUILDER_DESCRIPTION;
  }

  @Override
  public ExitCode build(CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        ChunkBuildOutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    ExitCode exitCode;
    try {
      exitCode = doBuild(context, chunk, dirtyFilesHolder);
    } catch (ConfigurationException e) {
      CompilerMessage message = new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, e.getMessage());
      context.processMessage(message);
      return ExitCode.ABORT;
    }
    return exitCode;
  }

  private ExitCode doBuild(final CompileContext context, ModuleChunk chunk,
                           DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) throws IOException {
    List<File> filesToCompile = collectFilesToCompiler(dirtyFilesHolder);

    if (filesToCompile.isEmpty()) {
      return ExitCode.NOTHING_DONE;
    }

    // Find a path to this jar
    File thisJar = new File(PathUtil.getJarPathForClass(RUNNER_CLASS));

    // Find a path to bundled Zinc jars (<plugin dir>/../zinc)
    File zincHome = new File(thisJar.getParentFile().getParentFile(), "zinc");

    ZincSettings zincSettings = ZincSettings.create(zincHome);
    JavaSettings javaSettings = JavaSettings.create(chunk);
    CompilationSettings compilationSettings = CompilationSettings.create(context, chunk);

    List<String> compilerArguments = new ArrayList<String>();

    // Add all compiler arguments
    compilerArguments.addAll(createCompilerArguments(javaSettings, zincSettings, compilationSettings));

    // Add all sources
    compilerArguments.addAll(toCanonicalPaths(filesToCompile));

    // Create a temp file for the compiler arguments
    File tempFile = FileUtil.createTempFile("ideaScalaToCompile", ".txt", true);

    // Save the compiler arguments to the temp file
    writeStringTo(tempFile, join(compilerArguments, "\n"));

    // Create a command line
    List<String> commandLine = createCommandLine(javaSettings, zincSettings, thisJar, tempFile);

    // Run the command
    exec(ArrayUtil.toStringArray(commandLine), context);

    // Delete the temp file
    asyncDelete(tempFile);

    return ExitCode.OK;
  }

  private static List<String> createCommandLine(JavaSettings javaSettings, ZincSettings zincSettings, File thisJar, File argumentsFile) {
    List<File> javaClasspath = new ArrayList<File>();

    // Add this jar (which contatins a runner class) to the classpath
    javaClasspath.add(thisJar);

    // Add Zinc jars to the classpath
    javaClasspath.addAll(zincSettings.getClasspath());

    List<String> commands = new ArrayList<String>();

    commands.add(toCanonicalPath(javaSettings.getExecutable()));

    commands.add("-Xmx384m");

    commands.add("-cp");
    commands.add(join(javaClasspath, File.pathSeparator));

    commands.add("-Dfile.encoding=" + System.getProperty("file.encoding"));

    commands.add(RUNNER_CLASS.getName());

    commands.add("com.typesafe.zinc.Main");

    commands.add(toCanonicalPath(argumentsFile));

    return commands;
  }

  public static List<String> createCompilerArguments(JavaSettings javaSettings,
                                                     ZincSettings zincSettings,
                                                     CompilationSettings compilationSettings) {
    List<String> args = new ArrayList<String>();

    args.add("-debug");

    args.add("-scala-path");
    args.add(join(toCanonicalPaths(compilationSettings.getScalaCompilerClasspath()), File.pathSeparator));

    args.add("-sbt-interface");
    args.add(toCanonicalPath(zincSettings.getSbtInterface()));

    args.add("-compiler-interface");
    args.add(toCanonicalPath(zincSettings.getCompilerSources()));

    args.add("-java-home");
    args.add(toCanonicalPath(javaSettings.getHome()));

    args.add("-d");
    args.add(toCanonicalPath(compilationSettings.getOutputDirectory()));

    args.add("-cp");
    args.add(join(toCanonicalPaths(compilationSettings.getCompilationClasspath()), File.pathSeparator));

    return args;
  }

  private void exec(String[] commands, final MessageHandler messageHandler) throws IOException {
    Process process = Runtime.getRuntime().exec(commands);

    BaseOSProcessHandler handler = new BaseOSProcessHandler(process, null, null) {
      @Override
      protected Future<?> executeOnPooledThread(Runnable task) {
        return SharedThreadPool.getInstance().executeOnPooledThread(task);
      }
    };

    final OutputParser parser = new OutputParser(messageHandler, BUILDER_NAME);

    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        messageHandler.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING, event.getText()));
//        parser.processMessageLine(event.getText());
      }

      @Override
      public void processTerminated(ProcessEvent event) {
//        parser.finishProcessing();
      }
    });

    handler.startNotify();
    handler.waitFor();
  }

  private List<File> collectFilesToCompiler(DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) throws IOException {
    final List<File> filesToCompile = new ArrayList<File>();

    dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
      public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor sourceRoot) throws IOException {
        if (isScalaFile(file.getPath())) {
          filesToCompile.add(file);
        }
        return true;
      }
    });

    return filesToCompile;
  }
}

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
import org.jetbrains.jps.incremental.scala.data.CompilationData;
import org.jetbrains.jps.incremental.scala.data.JavaData;
import org.jetbrains.jps.incremental.scala.data.ZincData;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;

import static com.intellij.openapi.util.io.FileUtil.asyncDelete;
import static com.intellij.openapi.util.text.StringUtil.join;
import static org.jetbrains.jps.incremental.scala.Utilities.*;

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

    // Compute all configuration data
    ZincData zincData = ZincData.create(zincHome);
    JavaData javaData = JavaData.create(chunk);
    CompilationData compilationData = CompilationData.create(context, chunk);

    List<String> zincArguments = new ArrayList<String>();

    // Add Zinc arguments
    zincArguments.addAll(createZincArguments(zincData, javaData, compilationData));

    // Add sources
    zincArguments.addAll(toCanonicalPaths(filesToCompile));

    // Create a temp file for the Zinc arguments
    File tempFile = FileUtil.createTempFile("ideaZincArguments", ".txt", true);

    // Save the compiler arguments to the temp file
    writeStringTo(tempFile, join(zincArguments, "\n"));

    List<File> vmClasspath = new ArrayList<File>();

    // Add this jar (which contatins a runner class) to the classpath
    vmClasspath.add(thisJar);

    // Add Zinc jars to the classpath
    vmClasspath.addAll(zincData.getClasspath());

    // Create a command line
    List<String> commandLine = createCommandLine(javaData, vmClasspath, "com.typesafe.zinc.Main", tempFile);

    // Run the command
    exec(commandLine, context);

    // Delete the temp file
    asyncDelete(tempFile);

    return ExitCode.OK;
  }

  private static List<String> createCommandLine(JavaData javaData, Collection<File> classpath, String mainClass, File inputFile) {
    List<String> commands = new ArrayList<String>();

    commands.add(toCanonicalPath(javaData.getExecutable()));

    commands.add("-Xmx384m");

    commands.add("-cp");
    commands.add(join(classpath, File.pathSeparator));

    commands.add("-Dfile.encoding=" + System.getProperty("file.encoding"));

    commands.add(RUNNER_CLASS.getName());

    commands.add(mainClass);

    commands.add(toCanonicalPath(inputFile));

    return commands;
  }

  private static List<String> createZincArguments(ZincData zincData, JavaData javaData, CompilationData compilationData) {
    List<String> args = new ArrayList<String>();

    args.add("-debug");

    args.add("-sbt-interface");
    args.add(toCanonicalPath(zincData.getSbtInterface()));

    args.add("-compiler-interface");
    args.add(toCanonicalPath(zincData.getCompilerSources()));

    args.add("-java-home");
    args.add(toCanonicalPath(javaData.getHome()));

    args.add("-scala-path");
    args.add(join(toCanonicalPaths(compilationData.getScalaCompilerClasspath()), File.pathSeparator));

    args.add("-d");
    args.add(toCanonicalPath(compilationData.getOutputDirectory()));

    args.add("-cp");
    args.add(join(toCanonicalPaths(compilationData.getCompilationClasspath()), File.pathSeparator));

    return args;
  }

  private void exec(List<String> commands, final MessageHandler messageHandler) throws IOException {
    Process process = Runtime.getRuntime().exec(ArrayUtil.toStringArray(commands));

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

package org.jetbrains.jps.incremental.scala;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.scala.model.FacetSettings;
import org.jetbrains.jps.incremental.scala.model.LibraryLevel;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;

import static org.jetbrains.jps.incremental.scala.Utilities.toCanonicalPaths;
import static org.jetbrains.jps.incremental.scala.Utilities.writeStringTo;

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

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  @Override
  public String getDescription() {
    return BUILDER_DESCRIPTION;
  }

  private static boolean isScalaFile(String path) {
    return path.endsWith(SCALA_EXTENSION);
  }

  @Override
  public ExitCode build(CompileContext context, ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) throws ProjectBuildException, IOException {
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
    List<String> sources = toCanonicalPaths(collectFilesToCompiler(dirtyFilesHolder));

    if (sources.isEmpty()) {
      return ExitCode.NOTHING_DONE;
    }

    File tempFile = createFileWithArguments(context, chunk, sources);

    List<String> vmClasspath = getVMClasspathIn(context, chunk);

    List<String> commands = ExternalProcessUtil.buildJavaCommandLine(
        getJavaExecutableIn(chunk),
        RUNNER_CLASS.getName(),
        Collections.<String>emptyList(), vmClasspath,
        Arrays.asList("-Xmx384m", "-Dfile.encoding=" + System.getProperty("file.encoding")),
        Arrays.<String>asList("scala.tools.nsc.Main", tempFile.getPath())
    );

    exec(ArrayUtil.toStringArray(commands), context);

    FileUtil.asyncDelete(tempFile);

    return ExitCode.OK;
  }

  private void exec(String[] commands, MessageHandler messageHandler) throws IOException {
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
        parser.processMessageLine(event.getText());
      }

      @Override
      public void processTerminated(ProcessEvent event) {
        parser.finishProcessing();
      }
    });

    handler.startNotify();
    handler.waitFor();
  }

  private static File createFileWithArguments(CompileContext context, ModuleChunk chunk, Collection<String> sources) throws IOException {
    ModuleBuildTarget target = chunk.representativeTarget();

    File outputDir = target.getOutputDir();

    if (outputDir == null)
      throw new ConfigurationException("Output directory not specified for module " + target.getModuleName());

    File tempFile = FileUtil.createTempFile("ideaScalaToCompile", ".txt", true);

    Collection<File> chunkClasspath = context.getProjectPaths()
        .getCompilationClasspathFiles(chunk, chunk.containsTests(), false, false);

    String outputPath = FileUtil.toCanonicalPath(outputDir.getPath());
    String classpath = StringUtil.join(toCanonicalPaths(chunkClasspath), File.pathSeparator);
    List<String> arguments = createCompilerArguments(outputPath, classpath, sources);

    writeStringTo(tempFile, StringUtil.join(arguments, "\n"));

    return tempFile;
  }

  private static List<String> createCompilerArguments(String outputPath,
                                                      String classpath,
                                                      Collection<String> sources) {
    List<String> args = new ArrayList<String>();

    args.add("-verbose");
    args.add("-d");
    args.add(outputPath);
    args.add("-cp");
    args.add(classpath);
    args.addAll(sources);

    return args;
  }

  private static String getJavaExecutableIn(ModuleChunk chunk) {
    JpsSdk<?> sdk = chunk.getModules().iterator().next().getSdk(JpsJavaSdkType.INSTANCE);
    return sdk == null ? SystemProperties.getJavaHome() + "/bin/java" : JpsJavaSdkType.getJavaExecutable(sdk);
  }

  private static List<String> getVMClasspathIn(CompileContext context, ModuleChunk chunk) {
    List<String> result = new ArrayList<String>();

//    Collection<File> platformCompilationClasspath = context.getProjectPaths().getPlatformCompilationClasspath(chunk, true);
//    result.addAll(toCanonicalPaths(platformCompilationClasspath));

    JpsModule module = chunk.representativeTarget().getModule();
    JpsModel model = context.getProjectDescriptor().getModel();
    JpsLibrary compilerLibrary = getCompilerLibraryIn(module, model);

    List<File> compilerLibraryFiles = compilerLibrary.getFiles(JpsOrderRootType.COMPILED);

    if (compilerLibraryFiles.isEmpty())
      throw new ConfigurationException("Compiler library is empty: " + compilerLibrary.getName());

    result.addAll(toCanonicalPaths(compilerLibraryFiles));

    result.add(FileUtil.toCanonicalPath(PathUtil.getJarPathForClass(RUNNER_CLASS)));

    return result;
  }

  private static JpsLibrary getCompilerLibraryIn(JpsModule module, JpsModel model) {
    FacetSettings settings = SettingsManager.getFacetSettings(module);

    if (settings == null) throw new ConfigurationException(
        "No Scala facet in module: " + module.getName());

    LibraryLevel compilerLibraryLevel = settings.getCompilerLibraryLevel();

    if (compilerLibraryLevel == null) throw new ConfigurationException(
        "No compiler library level set in module: " + module.getName());

    JpsLibraryCollection libraryCollection = getLibraryCollection(compilerLibraryLevel, model, module);

    String compilerLibraryName = settings.getCompilerLibraryName();

    if (compilerLibraryName == null) throw new ConfigurationException(
        "No compiler library name set in module: " + module.getName());

    JpsLibrary library = libraryCollection.findLibrary(compilerLibraryName);

    if (library == null) throw new ConfigurationException(
        String.format("Сompiler library for module %s not found: %s / %s ",
            module.getName(), compilerLibraryLevel, compilerLibraryName));

    return library;
  }

  private static JpsLibraryCollection getLibraryCollection(LibraryLevel level, JpsModel model, JpsModule module) {
    switch (level) {
      case Global:
        return model.getGlobal().getLibraryCollection();
      case Project:
        return model.getProject().getLibraryCollection();
      case Module:
        return module.getLibraryCollection();
      default:
        throw new ConfigurationException("Unknown library level: " + level);
    }
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

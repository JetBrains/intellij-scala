package org.jetbrains.jps.incremental.scala;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.ChunkBuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.scala.data.CompilationData;
import org.jetbrains.jps.incremental.scala.data.JavaData;
import org.jetbrains.jps.incremental.scala.data.SbtData;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Pavel Fatin
 */
public class ScalaBuilder extends ModuleLevelBuilder {
  private static final String BUILDER_NAME = "scala";
  private static final String BUILDER_DESCRIPTION = "Scala builder";
  private static Class RUNNER_CLASS = ClassRunner.class;

  private static final String SCALA_EXTENSION = "scala";
  private static final String JAVA_EXTENSION = "java";

  protected ScalaBuilder() {
    super(BuilderCategory.TRANSLATOR);
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
      exitCode = doBuild(context, chunk, dirtyFilesHolder, outputConsumer);
    } catch (ConfigurationException e) {
      CompilerMessage message = new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, e.getMessage());
      context.processMessage(message);
      return ExitCode.ABORT;
    }
    return exitCode;
  }

  private ExitCode doBuild(final CompileContext context, ModuleChunk chunk,
                           DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                           ChunkBuildOutputConsumer outputConsumer) throws IOException {
    // Collect all Scala and Java files in chunk modules
    context.processMessage(new ProgressMessage("Searching for compilable files"));
    Map<File, BuildTarget> filesToCompile = collectCompilableFiles(chunk);

    if (filesToCompile.isEmpty()) {
      return ExitCode.NOTHING_DONE;
    }

    context.processMessage(new ProgressMessage("Reading compilation settings"));

    // Find a path to this jar
    File thisJar = new File(PathUtil.getJarPathForClass(RUNNER_CLASS));

    // Find a path to bundled SBT jars (<plugin dir>/../sbt)
    File sbtHome = new File(thisJar.getParentFile().getParentFile(), "sbt");

    // Compute all configuration data
    SbtData sbtData = SbtData.create(sbtHome);
    JavaData javaData = JavaData.create(chunk);
    CompilationData compilationData = CompilationData.create(context, chunk);

    FileHandler fileHandler = new ConsumerFileHander(outputConsumer, filesToCompile);

    Compiler compiler = new Compiler(BUILDER_NAME, context, fileHandler);

    Set<File> sources = filesToCompile.keySet();

    compiler.compile(sources.toArray(new File[sources.size()]), sbtData, javaData, compilationData);

    context.processMessage(new ProgressMessage("Compilation completed", 1.0F));

    return ExitCode.OK;
  }

  private static Map<File, BuildTarget> collectCompilableFiles(ModuleChunk chunk) {
    final Map<File, BuildTarget> result = new HashMap<File, BuildTarget>();

    for (final ModuleBuildTarget target : chunk.getTargets()) {
      JpsModule module = target.getModule();

      for (JpsModuleSourceRoot root : module.getSourceRoots()) {
        FileUtil.processFilesRecursively(root.getFile(), new Processor<File>() {
          public boolean process(File file) {
            String path = file.getPath();
            if (isScalaFile(path) || isJavaFile(path)) {
              result.put(file, target);
            }
            return true;
          }
        });
      }
    }

    return result;
  }

  private static boolean isScalaFile(String path) {
    return path.endsWith(SCALA_EXTENSION);
  }

  private static boolean isJavaFile(String path) {
    return path.endsWith(JAVA_EXTENSION);
  }
}

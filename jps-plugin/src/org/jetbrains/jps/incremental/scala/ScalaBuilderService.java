package org.jetbrains.jps.incremental.scala;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author Pavel Fatin
 */
public class ScalaBuilderService extends BuilderService {
  @NotNull
  @Override
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Collections.singletonList(new ScalaBuilderDecorator());
  }

  @NotNull
  @Override
  public List<? extends TargetBuilder<?, ?>> createBuilders() {
    return Collections.singletonList(new StubTargetBuilder());
  }

  private static class ScalaBuilderDecorator extends ScalaBuilder {
    @Override
    public ExitCode build(CompileContext context,
                          ModuleChunk chunk,
                          DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                          OutputConsumer outputConsumer) {
      JpsProject project = context.getProjectDescriptor().getProject();

      return isScalaProject(project)
          ? super.build(context, chunk, dirtyFilesHolder, outputConsumer)
          : ExitCode.NOTHING_DONE;
    }
  }

  // TODO expect future JPS API to provide a more elegant way to substitute default Java compiler
  private static class StubTargetBuilder extends TargetBuilder<JavaSourceRootDescriptor, ModuleBuildTarget> {
    public StubTargetBuilder() {
      super(Collections.<BuildTargetType<ModuleBuildTarget>>emptyList());
    }

    @Override
    public void buildStarted(CompileContext context) {
      JpsProject project = context.getProjectDescriptor().getProject();

      // Disable default Java compiler for a project with Scala facets
      if (isScalaProject(project)) {
        JpsJavaExtensionService.getInstance()
            .getOrCreateCompilerConfiguration(project)
            .setJavaCompilerId("scala");
      }
    }

    @Override
    public void build(@NotNull ModuleBuildTarget target,
                      @NotNull DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> holder,
                      @NotNull BuildOutputConsumer outputConsumer,
                      @NotNull CompileContext context) throws ProjectBuildException, IOException {
      // do nothing
    }

    @NotNull
    @Override
    public String getPresentableName() {
      return "Scala Stub Builder";
    }
  }

  private static boolean isScalaProject(JpsProject project) {
    for (JpsModule module : project.getModules()) {
      if (SettingsManager.hasScalaSdk(module)) {
        return true;
      }
    }
    return false;
  }
}

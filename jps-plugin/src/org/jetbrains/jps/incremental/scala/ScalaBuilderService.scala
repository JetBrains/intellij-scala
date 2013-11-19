package org.jetbrains.jps.incremental.scala

import _root_.java.util.Collections
import org.jetbrains.jps.incremental._
import _root_.scala.collection.JavaConverters._
import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.builders.{BuildOutputConsumer, DirtyFilesHolder}
import _root_.java.util

/**
 * Nikolay.Tropin
 * 11/19/13
 */
class ScalaBuilderService extends BuilderService {
  @NotNull
  override def createModuleLevelBuilders: util.List[_ <: ModuleLevelBuilder] = {
    List[ModuleLevelBuilder](
      new ScalaBuilder(BuilderCategory.SOURCE_PROCESSOR, IdeaIncrementalBuilder),
      new ScalaBuilder(BuilderCategory.OVERWRITING_TRANSLATOR, IdeaIncrementalBuilder),
      new ScalaBuilder(BuilderCategory.TRANSLATOR, SbtBuilder)
    ).asJava
  }

  override def createBuilders(): util.List[_ <: TargetBuilder[_, _]] = Collections.singletonList(StubTargetBuilder)
}

private object StubTargetBuilder
        extends TargetBuilder[JavaSourceRootDescriptor, ModuleBuildTarget](util.Collections.emptyList()) {

  override def buildStarted(context: CompileContext) {
    val project: JpsProject = context.getProjectDescriptor.getProject
    if (ScalaBuilder.isScalaProject(project)) {
      JpsJavaExtensionService.getInstance.getOrCreateCompilerConfiguration(project).setJavaCompilerId("scala")
    }
  }

  def build(@NotNull target: ModuleBuildTarget,
            @NotNull holder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
            @NotNull outputConsumer: BuildOutputConsumer,
            @NotNull context: CompileContext) {
    //do nothing
  }

  @NotNull def getPresentableName: String = "Scala Stub Builder"
}
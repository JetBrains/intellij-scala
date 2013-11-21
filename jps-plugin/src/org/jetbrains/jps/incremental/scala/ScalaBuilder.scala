package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.incremental.{ModuleBuildTarget, CompileContext, BuilderCategory, ModuleLevelBuilder}
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.scala.model.{Order, IncrementalType}
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.annotations.NotNull
import scala.collection.JavaConverters._
import java.util
import ScalaBuilder._

/**
 * Nikolay.Tropin
 * 11/19/13
 */
class ScalaBuilder(category: BuilderCategory, @NotNull delegate: ScalaBuilderDelegate) extends ModuleLevelBuilder(category) {
  def getPresentableName: String = "Scala builder"

  def build(context: CompileContext,
            chunk: ModuleChunk,
            dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
            outputConsumer: ModuleLevelBuilder.OutputConsumer): ModuleLevelBuilder.ExitCode = {

    if (isDisabled(context)) ExitCode.NOTHING_DONE
    else delegate.build(context, chunk, dirtyFilesHolder, outputConsumer)
  }

  override def buildStarted(context: CompileContext) {
    if (isDisabled(context)) {}
    else delegate.buildStarted(context)
  }

  override def getCompilableFileExtensions: util.List[String] = List("scala").asJava

  private def isDisabled(context: CompileContext): Boolean = {
    val project: JpsProject = context.getProjectDescriptor.getProject
    if (!isScalaProject(project)) return true

    SettingsManager.getProjectSettings.incrementalType match {
      case IncrementalType.SBT if delegate != SbtBuilder => true
      case IncrementalType.IDEA =>
        if (delegate != IdeaIncrementalBuilder) return true

        SettingsManager.getProjectSettings.compileOrder match {
          case Order.JavaThenScala if getCategory == BuilderCategory.SOURCE_PROCESSOR => true
          case (Order.ScalaThenJava | Order.Mixed) if getCategory == BuilderCategory.OVERWRITING_TRANSLATOR => true
          case _ => false
        }
      case _ => false
    }
  }
}

object ScalaBuilder {
  def isScalaProject(project: JpsProject): Boolean = {
    import scala.collection.JavaConversions._
    for (module <- project.getModules) {
      if (SettingsManager.getFacetSettings(module) != null) {
        return true
      }
    }
    false
  }

}
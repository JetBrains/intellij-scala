package org.jetbrains.jps.incremental.scala

import java.util

import com.intellij.internal.statistic.UsageTrigger
import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.scala.ScalaBuilder._
import org.jetbrains.jps.incremental.{BuilderCategory, CompileContext, ModuleBuildTarget, ModuleLevelBuilder}
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.plugin.scala.compiler.{CompileOrder, IncrementalType}

import scala.collection.JavaConverters._

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

    if (isDisabled(context, Some(chunk))) ExitCode.NOTHING_DONE
    else delegate.build(context, chunk, dirtyFilesHolder, outputConsumer)
  }

  override def buildStarted(context: CompileContext) {
    if (isScalaProject(context.getProjectDescriptor.getProject)) {
      new IncrementalTypeChecker(context).checkAndUpdate()
      collectStats(context)
    }

    if (isDisabled(context)) {}
    else delegate.buildStarted(context)
  }

  override def getCompilableFileExtensions: util.List[String] = List("scala").asJava

  private def isDisabled(context: CompileContext, chunk: Option[ModuleChunk] = None): Boolean = {
    val project: JpsProject = context.getProjectDescriptor.getProject
    if (!isScalaProject(project)) return true
    if (chunk.isDefined && delegate == IdeaIncrementalBuilder && !hasScalaModules(chunk.get)) return true

    val projectSettings = SettingsManager.getProjectSettings(context.getProjectDescriptor)

    projectSettings.incrementalType match {
      case IncrementalType.SBT if delegate != SbtBuilder => true
      case IncrementalType.IDEA =>
        if (delegate != IdeaIncrementalBuilder) return true

        projectSettings.compileOrder match {
          case CompileOrder.JavaThenScala if getCategory == BuilderCategory.SOURCE_PROCESSOR => true
          case (CompileOrder.ScalaThenJava | CompileOrder.Mixed) if getCategory == BuilderCategory.OVERWRITING_TRANSLATOR => true
          case _ => false
        }
      case _ => false
    }
  }
}

object ScalaBuilder {
  def isScalaProject(project: JpsProject): Boolean = hasScalaFacets(project.getModules)

  def hasScalaModules(chunk: ModuleChunk): Boolean = hasScalaFacets(chunk.getModules)

  private def hasScalaFacets(modules: util.Collection[JpsModule]): Boolean = {
    import scala.collection.JavaConversions._
    modules.exists(SettingsManager.getFacetSettings(_) != null)
  }

  private def collectStats(context: CompileContext) {
    val withCompileServerId = "scala.compilation.with.server"
    val withoutCompileServerId = "scala.compilation.without.server"
    val incrementalByIdeaId = "scala.compilation.incremental.by.idea"
    val incrementalBySbtId = "scala.compilation.incremental.by.sbt"

    val serverEnabled = SettingsManager.getGlobalSettings(context.getProjectDescriptor.getModel.getGlobal)
            .isCompileServerEnabled
    val incrementalType = SettingsManager.getProjectSettings(context.getProjectDescriptor).incrementalType

    if (serverEnabled) UsageTrigger.trigger(withCompileServerId)
    else UsageTrigger.trigger(withoutCompileServerId)

    incrementalType match {
      case IncrementalType.SBT => UsageTrigger.trigger(incrementalBySbtId)
      case IncrementalType.IDEA => UsageTrigger.trigger(incrementalByIdeaId)
    }
  }
}
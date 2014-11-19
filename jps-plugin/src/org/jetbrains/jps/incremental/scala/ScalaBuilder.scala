package org.jetbrains.jps.incremental.scala

import java.util

import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.messages.{CompilerMessage, BuildMessage}
import org.jetbrains.jps.incremental.scala.ScalaBuilder._
import org.jetbrains.jps.incremental.{BuilderCategory, CompileContext, ModuleBuildTarget, ModuleLevelBuilder}
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.incremental.scala.model.CompileOrder
import org.jetbrains.jps.incremental.scala.model.IncrementalityType

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

    if (isDisabled(context, Some(chunk))) {
      return ExitCode.NOTHING_DONE
    }

    if (delegate == IdeaIncrementalBuilder) {
      if (IdeaIncrementalBuilder.collectSources(context, chunk, dirtyFilesHolder).isEmpty) {
        return ExitCode.NOTHING_DONE
      } else if (!hasScalaModules(chunk)) {
        if (!hasBuildModules(chunk)) {
          val message = "skipping Scala files without a Scala SDK in module(s) " + chunk.getPresentableShortName
          context.processMessage(new CompilerMessage("scala", BuildMessage.Kind.WARNING, message))
        }
        return ExitCode.NOTHING_DONE
      }
    } else {
      if (!isScalaProject(context.getProjectDescriptor.getProject)) {
        return ExitCode.NOTHING_DONE
      }
    }

    delegate.build(context, chunk, dirtyFilesHolder, outputConsumer)
  }

  override def buildStarted(context: CompileContext) {
    new IncrementalTypeChecker(context).checkAndUpdate()

    if (isDisabled(context)) {}
    else delegate.buildStarted(context)
  }

  override def getCompilableFileExtensions: util.List[String] = List("scala").asJava

  private def isDisabled(context: CompileContext, chunk: Option[ModuleChunk] = None): Boolean = {
    val projectSettings = SettingsManager.getProjectSettings(context.getProjectDescriptor.getProject)

    projectSettings.getIncrementalityType match {
      case IncrementalityType.SBT if delegate != SbtBuilder => true
      case IncrementalityType.IDEA =>
        if (delegate != IdeaIncrementalBuilder) return true

        projectSettings.getCompileOrder match {
          case CompileOrder.JavaThenScala if getCategory == BuilderCategory.SOURCE_PROCESSOR => true
          case (CompileOrder.ScalaThenJava | CompileOrder.Mixed) if getCategory == BuilderCategory.OVERWRITING_TRANSLATOR => true
          case _ => false
        }
      case _ => false
    }
  }
}

// Invokation of these methods can take a long time on large projects (like IDEA's one)
object ScalaBuilder {
  def isScalaProject(project: JpsProject): Boolean = hasScalaSdks(project.getModules)

  def hasScalaModules(chunk: ModuleChunk): Boolean = hasScalaSdks(chunk.getModules)

  private def hasBuildModules(chunk: ModuleChunk): Boolean = {
    import scala.collection.JavaConversions._
    chunk.getModules.exists(_.getName.endsWith("-build")) // gen-idea doesn't use the SBT module type
  }

  private def hasScalaSdks(modules: util.Collection[JpsModule]): Boolean = {
    import scala.collection.JavaConversions._
    modules.exists(SettingsManager.hasScalaSdk)
  }
}
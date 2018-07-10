package org.jetbrains.plugin.scala.compilerReferences

import java.util

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.{JavaModuleBuildTargetType, JavaSourceRootDescriptor}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import org.jetbrains.jps.incremental.{BuilderCategory, CompileContext, ModuleBuildTarget, ModuleLevelBuilder}

import scala.collection.JavaConverters._

class ScalaCompilerReferenceIndexBuilder extends ModuleLevelBuilder(BuilderCategory.CLASS_POST_PROCESSOR) {
  import ScalaCompilerReferenceIndexBuilder._

  override def getPresentableName: String                     = "scala compiler-reference indexer"
  override def getCompilableFileExtensions: util.List[String] = List("scala", "java").asJava

  override def buildStarted(context: CompileContext): Unit =
    context.processMessage(CompilationStarted(isRebuildInAllModules(context)))

  override def buildFinished(context: CompileContext): Unit = {
    val pd = context.getProjectDescriptor

    val timestamp = allJavaTargetTypes
      .flatMap(pd.getBuildTargetIndex.getAllTargets(_).asScala)
      .map { target =>
        val stamp = context.getCompilationStartStamp(target)
        if (stamp == 0) Long.MaxValue
        else stamp
      }
      .min

    context.processMessage(CompilationFinished(timestamp))
  }

  override def build(
    context:          CompileContext,
    chunk:            ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
    outputConsumer:   ModuleLevelBuilder.OutputConsumer
  ): ExitCode = {
    val affectedModules: Set[String] = chunk.getModules.asScala.map(_.getName)(collection.breakOut)

    val compiledClasses =
      outputConsumer.getCompiledClasses.values().iterator().asScala.toSet

    val removedSources = for {
      target      <- chunk.getTargets.asScala.toSet if target != null
      removedFile <- dirtyFilesHolder.getRemovedFiles(target).asScala
    } yield removedFile

    val data = ChunkBuildData(
      compiledClasses,
      removedSources,
      affectedModules
    )

    if (removedSources.nonEmpty || compiledClasses.nonEmpty) context.processMessage(ChunkBuildInfo(data))

    ExitCode.OK
  }

  private def isRebuildInAllModules(context: CompileContext): Boolean =
    allJavaTargetTypes.forall { ttype =>
      val targets = context.getProjectDescriptor.getBuildTargetIndex.getAllTargets(ttype).asScala
      targets.forall(context.getScope.isBuildForced)
    }
}

object ScalaCompilerReferenceIndexBuilder {
  val id = "sc.compiler.ref.index"
  val chunkBuildDataType = "chunk-build-data-info"
  val compilationFinishedType = "compilation-finished"
  val compilationStartedType = "compilation-started"

  private val allJavaTargetTypes = JavaModuleBuildTargetType.ALL_TYPES.asScala

  import org.jetbrains.plugin.scala.compilerReferences.Codec._

  final case class ChunkBuildInfo(data: ChunkBuildData)
      extends CustomBuilderMessage(id, chunkBuildDataType, data.encode)

  final case class CompilationFinished(timestamp: Long)
      extends CustomBuilderMessage(id, compilationFinishedType, timestamp.encode)

  final case class CompilationStarted(isCleanBuild: Boolean)
      extends CustomBuilderMessage(id, compilationStartedType, isCleanBuild.encode)
}

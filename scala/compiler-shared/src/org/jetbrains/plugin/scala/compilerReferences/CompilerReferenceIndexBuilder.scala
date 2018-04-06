package org.jetbrains.plugin.scala.compilerReferences

import java.time.Instant
import java.util

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.builders.{BuildTargetRegistry, DirtyFilesHolder}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import org.jetbrains.jps.incremental.{BuilderCategory, CompileContext, ModuleBuildTarget, ModuleLevelBuilder}

import scala.collection.JavaConverters._

class CompilerReferenceIndexBuilder extends ModuleLevelBuilder(BuilderCategory.CLASS_POST_PROCESSOR) {
  import CompilerReferenceIndexBuilder._

  override def getPresentableName: String                     = "scala compiler-reference indexer"
  override def getCompilableFileExtensions: util.List[String] = List("scala", "java").asJava

  override def build(
    context: CompileContext,
    chunk: ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
    outputConsumer: ModuleLevelBuilder.OutputConsumer
  ): ExitCode = {
    val timeStamp                    = Instant.now().getEpochSecond
    val affectedModules: Set[String] = chunk.getModules.asScala.map(_.getName)(collection.breakOut)

    val compiledClasses =
      outputConsumer.getCompiledClasses.values().iterator().asScala.toSet

    val removedSources = for {
      target      <- chunk.getTargets.asScala.toSet if target != null
      removedFile <- dirtyFilesHolder.getRemovedFiles(target).asScala
    } yield removedFile

    val isCleanBuild = isCleanBuildInAllAffectedModules(context, chunk)

    val data = BuildData(
      timeStamp,
      compiledClasses,
      removedSources,
      affectedModules,
      isCleanBuild
    )

    if (removedSources.nonEmpty || compiledClasses.nonEmpty) context.processMessage(BuildDataInfo(data))

    ExitCode.OK
  }

  private def isCleanBuildInAllAffectedModules(context: CompileContext, chunk: ModuleChunk): Boolean = {
    val descriptor  = context.getProjectDescriptor
    val scope       = context.getScope
    val targetIndex = descriptor.getBuildTargetIndex
    val modules     = chunk.getModules.asScala

    modules.forall(
      module =>
        targetIndex
          .getModuleBasedTargets(module, BuildTargetRegistry.ModuleTargetSelector.ALL)
          .asScala
          .collect { case mbt: ModuleBuildTarget => mbt }
          .forall(
            target => scope.isWholeTargetAffected(target) || targetIndex.isDummy(target)
        )
    )
  }
}

object CompilerReferenceIndexBuilder {
  val id            = "sc.compiler.ref.index"
  val buildDataType = "build-data-info"

  import org.jetbrains.plugin.scala.compilerReferences.Codec._

  final case class BuildDataInfo(data: BuildData) extends CustomBuilderMessage(id, buildDataType, data.encode)
}

package org.jetbrains.plugin.scala.compilerReferences

import java.time.Instant
import java.util

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.{JavaModuleBuildTargetType, JavaSourceRootDescriptor}
import org.jetbrains.jps.builders.{BuildTargetRegistry, DirtyFilesHolder}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import org.jetbrains.jps.incremental.{BuilderCategory, CompileContext, ModuleBuildTarget, ModuleLevelBuilder}

import scala.collection.JavaConverters._

class CompilerReferenceIndexBuilder extends ModuleLevelBuilder(BuilderCategory.CLASS_POST_PROCESSOR) {
  import CompilerReferenceIndexBuilder._

  override def getPresentableName: String = "scalacompiler-reference indexer"

  override def getCompilableFileExtensions: util.List[String] = List("scala", "java").asJava

  override def build(
    context: CompileContext,
    chunk: ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
    outputConsumer: ModuleLevelBuilder.OutputConsumer
  ): ExitCode = {
    val timeStamp       = Instant.now().getEpochSecond
    val affectedModules = fullyAffectedModuleNames(context, chunk)

    val compiledClasses =
      outputConsumer.getCompiledClasses.values().iterator().asScala.toSet

    val removedSources = for {
      target      <- chunk.getTargets.asScala.toSet if target != null
      removedFile <- dirtyFilesHolder.getRemovedFiles(target).asScala
    } yield removedFile

    val isRebuild = isRebuildInAllModules(context)
    
    val data = BuildData(
      timeStamp,
      compiledClasses,
      removedSources,
      affectedModules,
      isRebuild
    )
    
    if (removedSources.nonEmpty || compiledClasses.nonEmpty) context.processMessage(BuildDataInfo(data))

    ExitCode.OK
  }

  private def isRebuildInAllModules(context: CompileContext): Boolean =
    JavaModuleBuildTargetType.ALL_TYPES.asScala.forall(context.getScope.isBuildForcedForAllTargets)

  private def fullyAffectedModuleNames(context: CompileContext, chunk: ModuleChunk): Set[String] = {
    val descriptor  = context.getProjectDescriptor
    val scope       = context.getScope
    val targetIndex = descriptor.getBuildTargetIndex

    chunk.getModules
      .iterator()
      .asScala
      .filterNot(
        module =>
          targetIndex
            .getModuleBasedTargets(module, BuildTargetRegistry.ModuleTargetSelector.ALL)
            .asScala
            .exists(
              target =>
                target.isInstanceOf[ModuleBuildTarget] &&
                  !scope.isWholeTargetAffected(target) &&
                  !targetIndex.isDummy(target)
          )
      )
      .map(_.getName)
      .toSet
  }
}

object CompilerReferenceIndexBuilder {
  val id            = "sc.compiler.ref.index"
  val buildDataType = "build-data-info"

  import org.jetbrains.plugin.scala.compilerReferences.Codec._

  final case class BuildDataInfo(data: BuildData) extends CustomBuilderMessage(id, buildDataType, data.encode)
}

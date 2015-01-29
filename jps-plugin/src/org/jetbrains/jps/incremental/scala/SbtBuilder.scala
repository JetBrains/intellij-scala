package org.jetbrains.jps.incremental.scala

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Processor
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.impl.TargetOutputIndexImpl
import org.jetbrains.jps.builders.java.{ResourceRootDescriptor, ResourcesTargetType, JavaModuleBuildTargetType, JavaSourceRootDescriptor}
import org.jetbrains.jps.builders.{BuildRootDescriptor, BuildTarget, DirtyFilesHolder}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental._
import org.jetbrains.jps.incremental.java.JavaBuilder
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.jps.incremental.scala.ScalaBuilder._
import org.jetbrains.jps.incremental.scala.local.IdeClientSbt
import org.jetbrains.jps.incremental.scala.model.IncrementalityType
import org.jetbrains.jps.model.JpsProject

import _root_.scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class SbtBuilder extends ModuleLevelBuilder(BuilderCategory.TRANSLATOR) {
  override def getPresentableName = "Scala SBT builder"

  override def buildStarted(context: CompileContext) = {
    val project: JpsProject = context.getProjectDescriptor.getProject
    if (isScalaProject(project) && !isDisabled(context))
      JavaBuilder.IS_ENABLED.set(context, false)
  }

  override def build(context: CompileContext,
            chunk: ModuleChunk,
            dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
            outputConsumer: ModuleLevelBuilder.OutputConsumer): ModuleLevelBuilder.ExitCode = {

    if (isDisabled(context) || ChunkExclusionService.isExcluded(chunk))
      return ExitCode.NOTHING_DONE

    checkIncrementalTypeChange(context)

    if (!hasDirtyFilesOrDependencies(context, chunk, dirtyFilesHolder))
      return ExitCode.NOTHING_DONE

    updateSharedResources(context, chunk)

    context.processMessage(new ProgressMessage("Searching for compilable files..."))

    val filesToCompile = collectCompilableFiles(context, chunk)
    if (filesToCompile.isEmpty)
      return ExitCode.NOTHING_DONE

    // Delete dirty class files (to handle force builds and form changes)
    BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, dirtyFilesHolder)

    val sources = filesToCompile.keySet.toSeq

    val modules = chunk.getModules.asScala.toSet

    val client = new IdeClientSbt("scala", context, modules.map(_.getName).toSeq, outputConsumer, filesToCompile.get)

    compile(context, chunk, sources, modules, client) match {
      case Left(error) =>
        client.error(error)
        ExitCode.ABORT
      case Right(code) =>
        if (client.hasReportedErrors || client.isCanceled) {
          ExitCode.ABORT
        } else {
          client.progress("Compilation completed", Some(1.0F))
          code
        }
    }
  }

  // TODO Mirror file deletion (either via the outputConsumer or a custom index)
  private def updateSharedResources(context: CompileContext, chunk: ModuleChunk) {
    val project = context.getProjectDescriptor

    val resourceTargets: Seq[ResourcesTarget] = {
      val sourceModules = SourceDependenciesProviderService.getSourceDependenciesFor(chunk)
      val targetType = chunk.representativeTarget.getTargetType match {
        case JavaModuleBuildTargetType.PRODUCTION => ResourcesTargetType.PRODUCTION
        case JavaModuleBuildTargetType.TEST => ResourcesTargetType.TEST
        case _ => ResourcesTargetType.PRODUCTION
      }
      sourceModules.map(new ResourcesTarget(_, targetType))
    }

    val resourceRoots: Seq[ResourceRootDescriptor] = {
      val rootIndex = project.getBuildRootIndex
      resourceTargets.flatMap(rootIndex.getTargetRoots(_, context).asScala)
    }

    val excludeIndex = project.getModuleExcludeIndex
    val outputRoot: File = chunk.representativeTarget().getOutputDir

    resourceRoots.foreach { root: ResourceRootDescriptor =>
      val filter = root.createFileFilter()

      FileUtil.processFilesRecursively(root.getRootFile, new Processor[File] {
        def process(file: File) = {
          if (file.isFile && filter.accept(file) && !excludeIndex.isExcluded(file)) {
            ResourceUpdater.updateResource(context, root, file, outputRoot)
          }
          true
        }
      })
    }
  }

  private def isDisabled(context: CompileContext): Boolean = {
    projectSettings(context).getIncrementalityType != IncrementalityType.SBT
  }

  private def hasDirtyFilesOrDependencies(context: CompileContext, chunk: ModuleChunk,
                                          dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget]): Boolean = {

    val representativeTarget = chunk.representativeTarget()

    val timestamps = new TargetTimestamps(context)

    val targetTimestamp = timestamps.get(representativeTarget)

    val hasDirtyDependencies = {
      val dependencies = moduleDependenciesIn(context, representativeTarget)

      targetTimestamp.map { thisTimestamp =>
        dependencies.exists { dependency =>
          val thatTimestamp = timestamps.get(dependency)
          thatTimestamp.map(_ > thisTimestamp).getOrElse(true)
        }
      } getOrElse {
        dependencies.nonEmpty
      }
    }

    if (!hasDirtyDependencies && !dirtyFilesHolder.hasDirtyFiles && !dirtyFilesHolder.hasRemovedFiles) {
      if (targetTimestamp.isEmpty)
        timestamps.set(representativeTarget, context.getCompilationStartStamp)
      return false
    }

    timestamps.set(representativeTarget, context.getCompilationStartStamp)
    true
  }

  private def collectCompilableFiles(context: CompileContext,chunk: ModuleChunk): Map[File, BuildTarget[_ <: BuildRootDescriptor]] = {
    var result = Map[File, BuildTarget[_ <: BuildRootDescriptor]]()

    val project = context.getProjectDescriptor

    val rootIndex = project.getBuildRootIndex
    val excludeIndex = project.getModuleExcludeIndex

    val sourceTargets = {
      val sourceModules = SourceDependenciesProviderService.getSourceDependenciesFor(chunk)
      val targetType = chunk.representativeTarget.getTargetType match {
        case javaBuildTarget: JavaModuleBuildTargetType => javaBuildTarget
        case _ => JavaModuleBuildTargetType.PRODUCTION
      }
      sourceModules.map(new ModuleBuildTarget(_, targetType))
    }

    for (target <- chunk.getTargets.asScala ++ sourceTargets;
         root <- rootIndex.getTargetRoots(target, context).asScala) {
      FileUtil.processFilesRecursively(root.getRootFile, new Processor[File] {
        def process(file: File) = {
          if (!excludeIndex.isExcluded(file)) {
            val path = file.getPath
            if (path.endsWith(".scala") || path.endsWith(".java")) {
              result += file -> target
            }
          }
          true
        }
      })
    }

    result
  }

  private def moduleDependenciesIn(context: CompileContext, target: ModuleBuildTarget): Seq[ModuleBuildTarget] = {
    val dependencies = {
      val targetOutputIndex = {
        val targets = context.getProjectDescriptor.getBuildTargetIndex.getAllTargets
        new TargetOutputIndexImpl(targets, context)
      }
      target.computeDependencies(context.getProjectDescriptor.getBuildTargetIndex, targetOutputIndex).asScala
    }

    dependencies.filter(_.isInstanceOf[ModuleBuildTarget]).map(_.asInstanceOf[ModuleBuildTarget]).toSeq
  }
}
package org.jetbrains.jps.incremental.scala

import java.io.File
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Processor
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.{FileProcessor, BuildRootDescriptor, BuildTarget, DirtyFilesHolder}
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.builders.impl.TargetOutputIndexImpl
import org.jetbrains.jps.incremental._
import messages.ProgressMessage
import collection.JavaConverters._
import org.jetbrains.jps.incremental.ModuleLevelBuilder.{OutputConsumer, ExitCode}
import org.jetbrains.jps.incremental.scala.local.IdeClientSbt
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.incremental.java.JavaBuilder

/**
 * @author Pavel Fatin
 */
object SbtBuilder extends ScalaBuilderDelegate {
  def getPresentableName = "Scala SBT builder"

  override def buildStarted(context: CompileContext) = {
    val project: JpsProject = context.getProjectDescriptor.getProject
    if (ScalaBuilder.isScalaProject(project))
      JavaBuilder.IS_ENABLED.set(context, false)
  }

  def build(context: CompileContext, chunk: ModuleChunk,
            dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
            outputConsumer: OutputConsumer): ModuleLevelBuilder.ExitCode = {

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

    if (!hasDirtyDependencies &&
            !hasDirtyFiles(dirtyFilesHolder) &&
            !dirtyFilesHolder.hasRemovedFiles) {

      if (targetTimestamp.isEmpty) {
        timestamps.set(representativeTarget, context.getCompilationStartStamp)
      }

      ExitCode.NOTHING_DONE
    } else {
      timestamps.set(representativeTarget, context.getCompilationStartStamp)

      doBuild(context, chunk, dirtyFilesHolder, outputConsumer)
    }
  }

  private def doBuild(context: CompileContext, chunk: ModuleChunk,
                      dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
                      outputConsumer: OutputConsumer): ModuleLevelBuilder.ExitCode = {

    if (ChunkExclusionService.isExcluded(chunk, context.getProjectDescriptor.getModel.getGlobal)) {
      return ExitCode.NOTHING_DONE
    }

    context.processMessage(new ProgressMessage("Searching for compilable files..."))
    val filesToCompile = collectCompilableFiles(context, chunk)

    if (filesToCompile.isEmpty) {
      return ExitCode.NOTHING_DONE
    }

    // Delete dirty class files (to handle force builds and form changes)
    BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, dirtyFilesHolder)

    val sources = filesToCompile.keySet.toSeq

    val modules = chunk.getModules.asScala.toSet

    val client = new IdeClientSbt("scala", context, modules.map(_.getName).toSeq, outputConsumer, filesToCompile.get)

    client.progress("Reading compilation settings...")

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

  private def hasDirtyFiles(dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget]): Boolean = {
    var result = false

    dirtyFilesHolder.processDirtyFiles(new FileProcessor[JavaSourceRootDescriptor, ModuleBuildTarget] {
      def apply(target: ModuleBuildTarget, file: File, root: JavaSourceRootDescriptor) = {
        result = true
        false
      }
    })

    result
  }

  private def collectCompilableFiles(context: CompileContext,chunk: ModuleChunk): Map[File, BuildTarget[_ <: BuildRootDescriptor]] = {
    var result = Map[File, BuildTarget[_ <: BuildRootDescriptor]]()

    val project = context.getProjectDescriptor

    val rootIndex = project.getBuildRootIndex
    val excludeIndex = project.getModuleExcludeIndex

    for (target <- chunk.getTargets.asScala;
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
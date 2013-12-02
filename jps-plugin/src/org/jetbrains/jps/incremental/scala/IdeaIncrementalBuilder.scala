package org.jetbrains.jps.incremental.scala

import java.io.File
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._
import org.jetbrains.jps.builders.{FileProcessor, DirtyFilesHolder}
import org.jetbrains.jps.builders.java.{JavaBuilderUtil, JavaSourceRootDescriptor}
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.jps.incremental.ModuleLevelBuilder.{ExitCode, OutputConsumer}
import org.jetbrains.jps.incremental.scala.local.IdeClientIdea
import org.jetbrains.jps.incremental.scala.model.Order
import org.jetbrains.jps.incremental._
import org.jetbrains.jps.ModuleChunk

/**
 * Nikolay.Tropin
 * 11/19/13
 */
object IdeaIncrementalBuilder extends ScalaBuilderDelegate {
  def getPresentableName: String = "Scala IDEA builder"

  def build(context: CompileContext,
            chunk: ModuleChunk,
            dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
            outputConsumer: OutputConsumer): ExitCode = {

    val successfullyCompiled = mutable.Set[File]()

    if (ChunkExclusionService.isExcluded(chunk)) return ExitCode.NOTHING_DONE

    context.processMessage(new ProgressMessage("Searching for compilable files..."))
    val sources = collectSources(context, chunk, dirtyFilesHolder)
    if (sources.isEmpty)
      return ExitCode.NOTHING_DONE

    context.processMessage(new ProgressMessage("Reading compilation settings..."))

    val delta = context.getProjectDescriptor.dataManager.getMappings.createDelta()
    val callback = delta.getCallback

    val modules = chunk.getModules.asScala.toSet
    val client = new IdeClientIdea("scalac", context, modules.map(_.getName).toSeq, outputConsumer, callback, successfullyCompiled)

    val compileResult = compile(context, chunk, sources, modules, client)

    compileResult match {
      case Left(error) =>
        client.error(error)
        ExitCode.ABORT
      case _ if client.hasReportedErrors || client.isCanceled => ExitCode.ABORT
      case Right(code) =>
        if (delta != null && JavaBuilderUtil.updateMappings(context, delta, dirtyFilesHolder, chunk, sources.asJava, successfullyCompiled.asJava))
          ExitCode.ADDITIONAL_PASS_REQUIRED
        else {
          client.progress("Compilation completed", Some(1.0F))
          code
        }
    }
  }

  private def collectSources(context: CompileContext,
                             chunk: ModuleChunk,
                             dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget]): Seq[File] = {

    val result = ListBuffer[File]()

    val project = context.getProjectDescriptor
    val excludeIndex = project.getModuleExcludeIndex

    val compileOrder = SettingsManager.getProjectSettings(project).compileOrder
    val extensionsToCollect = compileOrder match {
      case Order.Mixed => List(".scala", ".java")
      case _ => List(".scala")
    }

    def checkAndCollectFile(file: File, target: ModuleBuildTarget): Unit = {
      val fileName = file.getName
      if (extensionsToCollect.exists(fileName.endsWith) && !excludeIndex.isExcluded(file))
        result += file
    }

    dirtyFilesHolder.processDirtyFiles(new FileProcessor[JavaSourceRootDescriptor, ModuleBuildTarget] {
      def apply(target: ModuleBuildTarget, file: File, root: JavaSourceRootDescriptor) = {
        checkAndCollectFile(file, target)
        true
      }
    })

    //if no scala files to compile, return empty seq
    if (!result.exists(_.getName.endsWith(".scala"))) Seq.empty
    else result.toSeq
  }

}

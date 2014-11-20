package org.jetbrains.jps.incremental.scala

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Processor
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.{JavaBuilderUtil, JavaSourceRootDescriptor}
import org.jetbrains.jps.builders.{DirtyFilesHolder, FileProcessor}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.{ExitCode, OutputConsumer}
import org.jetbrains.jps.incremental.messages.{CompilerMessage, BuildMessage, ProgressMessage}
import org.jetbrains.jps.incremental.scala.ScalaBuilder._
import org.jetbrains.jps.incremental.scala.local.IdeClientIdea
import org.jetbrains.jps.incremental.scala.model.{IncrementalityType, CompileOrder}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import org.jetbrains.jps.incremental._

/**
 * Nikolay.Tropin
 * 11/19/13
 */
class IdeaIncrementalBuilder(category: BuilderCategory) extends ScalaBuilder(category) {

  override def getPresentableName: String = "Scala IDEA builder"

  override def doBuild(context: CompileContext,
            chunk: ModuleChunk,
            dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
            outputConsumer: OutputConsumer): ExitCode = {

    val successfullyCompiled = mutable.Set[File]()

    context.processMessage(new ProgressMessage("Searching for compilable files..."))
    val sources = collectSources(context, chunk, dirtyFilesHolder)
    if (sources.isEmpty)
      return ExitCode.NOTHING_DONE

    context.processMessage(new ProgressMessage("Reading compilation settings..."))

    val delta = context.getProjectDescriptor.dataManager.getMappings.createDelta()
    val callback = delta.getCallback

    val modules = chunk.getModules.asScala.toSet
    val client = new IdeClientIdea("scalac", context, modules.map(_.getName).toSeq, outputConsumer, callback, successfullyCompiled)

    val scalaSources = sources.filter(_.getName.endsWith(".scala")).asJava

    compile(context, chunk, sources, modules, client) match {
      case Left(error) =>
        client.error(error)
        ExitCode.ABORT
      case _ if client.hasReportedErrors || client.isCanceled => ExitCode.ABORT
      case Right(code) =>
        if (delta != null && JavaBuilderUtil.updateMappings(context, delta, dirtyFilesHolder, chunk, scalaSources, successfullyCompiled.asJava))
          ExitCode.ADDITIONAL_PASS_REQUIRED
        else {
          client.progress("Compilation completed", Some(1.0F))
          code
        }
    }
  }

  override protected def isDisabled(context: CompileContext): Boolean = {
    val settings = projectSettings(context)
    def wrongIncrType = settings.getIncrementalityType != IncrementalityType.IDEA
    def wrongCompileOrder = settings.getCompileOrder match {
      case CompileOrder.JavaThenScala => getCategory == BuilderCategory.SOURCE_PROCESSOR
      case (CompileOrder.ScalaThenJava | CompileOrder.Mixed) => getCategory == BuilderCategory.OVERWRITING_TRANSLATOR
      case _ => false
    }
    wrongIncrType || wrongCompileOrder
  }


  override protected def isNeeded(context: CompileContext, chunk: ModuleChunk,
                                  dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget]): Boolean = {

    if (ChunkExclusionService.isExcluded(chunk)) false
    else if (collectSources(context, chunk, dirtyFilesHolder).isEmpty) false
    else if (hasBuildModules(chunk)) false // *.scala files in SBT "build" modules are rightly excluded from compilation
    else if (!hasScalaModules(chunk)) {
      val message = "skipping Scala files without a Scala SDK in module(s) " + chunk.getPresentableShortName
      context.processMessage(new CompilerMessage("scala", BuildMessage.Kind.WARNING, message))
      false
    }
    else true
  }

  private def collectSources(context: CompileContext,
                     chunk: ModuleChunk,
                     dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget]): Seq[File] = {

    val result = ListBuffer[File]()

    val project = context.getProjectDescriptor

    val compileOrder = projectSettings(context).getCompileOrder
    val extensionsToCollect = compileOrder match {
      case CompileOrder.Mixed => List(".scala", ".java")
      case _ => List(".scala")
    }

    def checkAndCollectFile(file: File): Boolean = {
      val fileName = file.getName
      if (extensionsToCollect.exists(fileName.endsWith))
        result += file

      true
    }

    dirtyFilesHolder.processDirtyFiles(new FileProcessor[JavaSourceRootDescriptor, ModuleBuildTarget] {
      def apply(target: ModuleBuildTarget, file: File, root: JavaSourceRootDescriptor) = checkAndCollectFile(file)
    })

    for {
      target <- chunk.getTargets.asScala
      tempRoot <- project.getBuildRootIndex.getTempTargetRoots(target, context).asScala
    } {
      FileUtil.processFilesRecursively(tempRoot.getRootFile, new Processor[File] {
        def process(file: File) = checkAndCollectFile(file)
      })
    }


    //if no scala files to compile, return empty seq
    if (!result.exists(_.getName.endsWith(".scala"))) Seq.empty
    else result.toSeq
  }


}

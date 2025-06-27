package org.jetbrains.jps.incremental.scala

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.{JavaModuleBuildTargetType, JavaSourceRootDescriptor}
import org.jetbrains.jps.builders.{BuildTarget, DirtyFilesHolder}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.scala.model.JpsScalaProjectMetadataExtensionService.{moduleHasScala, projectHasScala}
import org.jetbrains.jps.incremental.{BuilderCategory, CompileContext, ModuleBuildTarget, ModuleLevelBuilder}
import org.jetbrains.plugins.scala.compiler.references.Builder.rebuildPropertyKey
import org.jetbrains.plugins.scala.compiler.references.Messages._
import org.jetbrains.plugins.scala.compiler.references.ModuleScope
import org.jetbrains.plugins.scala.indices.protocol.CompiledClass
import org.jetbrains.plugins.scala.indices.protocol.jps.JpsCompilationInfo

import java.nio.file.Paths
import java.{util => jutil}
import scala.jdk.CollectionConverters._

class ScalaCompilerReferenceIndexBuilder extends ModuleLevelBuilder(BuilderCategory.CLASS_POST_PROCESSOR) {

  override def getPresentableName: String =
    JpsBundle.message("scala.compiler.reference.indexer")

  override def getCompilableFileExtensions: jutil.List[String] =
    List("scala", "java").asJava

  override def buildStarted(context: CompileContext): Unit = {
    if (!projectHasScala(context))
      return

    context.processMessage(CompilationStarted(shouldBeNonIncremental))
  }

  override def buildFinished(context: CompileContext): Unit = {
    if (!projectHasScala(context))
      return

    if (shouldBeNonIncremental) {
      val pd                      = context.getProjectDescriptor
      val (allClasses, timestamp) = getAllClassesInfo(context) match {
        case Some(tuple) => tuple
        case None => return
      }
      val allModules = pd.getProject.getModules.asScala.filter(moduleHasScala(context))
        .flatMap { m =>
          val name = m.getName
          Seq(ModuleScope.Production, ModuleScope.Test).map(_.appendScopeSuffix(name))
        }
        .toSet

      val info = JpsCompilationInfo(
        allModules,
        Set.empty,
        allClasses,
        timestamp
      )
      context.processMessage(ChunkCompilationInfo(info))
    }
    context.processMessage(CompilationFinished)
  }

  private[this] def getTargetTimestamps(targets: Iterable[BuildTarget[_]], context: CompileContext): Option[Long] =
    targets.collect { case target: ModuleBuildTarget =>
      val stamp = context.getCompilationStartStamp(target)

      if (stamp == 0) Long.MaxValue
      else            stamp
    }.minOption

  private[this] val shouldBeNonIncremental: Boolean =
    sys.props.get(rebuildPropertyKey).exists(java.lang.Boolean.valueOf(_))

  override def build(
    context:          CompileContext,
    chunk:            ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
    outputConsumer:   ModuleLevelBuilder.OutputConsumer
  ): ExitCode = {
    if (!projectHasScala(context))
      return ExitCode.OK

    if (!shouldBeNonIncremental) {
      val targets = chunk.getTargets.asScala.toSeq

      val affectedModules = targets.map { target =>
        val scope = if (target.isTests) ModuleScope.Test else ModuleScope.Production
        val name = target.getModule.getName
        scope.appendScopeSuffix(name)
      }.toSet

      val compiledClasses =
        outputConsumer.getCompiledClasses
          .values()
          .iterator()
          .asScala
          .map { cc =>
            val sourcePath = Option(ContainerUtil.getFirstItem(cc.getSourceFiles)).map(_.toPath).orNull
            val outputPath = cc.getOutputFile.toPath
            CompiledClass(sourcePath, outputPath)
          }
          .toSet

      val optTimestamp = getTargetTimestamps(targets, context)
      val timestamp = optTimestamp match {
        case Some(t) => t
        case None => return ExitCode.OK
      }

      val removedSources = for {
        target      <- targets.toSet if target != null
        removedFile <- dirtyFilesHolder.getRemoved(target).asScala
      } yield removedFile

      val data = JpsCompilationInfo(
        affectedModules,
        removedSources,
        compiledClasses,
        timestamp
      )

      context.processMessage(ChunkCompilationInfo(data))
      ExitCode.OK
    } else ExitCode.OK
  }

  private[this] def getAllClassesInfo(context: CompileContext): Option[(Set[CompiledClass], Long)] = {
    val pd               = context.getProjectDescriptor
    val buildTargetIndex = pd.getBuildTargetIndex
    val dataManager      = pd.dataManager
    val targets          = allJavaTargetTypes.flatMap(buildTargetIndex.getAllTargets(_).asScala)
    val mappings         = targets.map(dataManager.getSourceToOutputMap).iterator

    val optTimestamp = getTargetTimestamps(targets, context)
    val timestamp = optTimestamp match {
      case Some(t) => t
      case None => return None
    }

    val classes = Set.newBuilder[CompiledClass]

    while (mappings.hasNext) {
      val mapping = mappings.next()
      val sources = mapping.getSourcesIterator.asScala

      sources.foreach { source =>
        val outputs    = Option(mapping.getOutputs(source)).fold(Iterable.empty[String])(_.asScala)
        val sourcePath = Paths.get(source)
        outputs.foreach(cls => classes += CompiledClass(sourcePath, Paths.get(cls)))
      }
    }

    Some((classes.result(), timestamp))
  }

  private val allJavaTargetTypes = JavaModuleBuildTargetType.ALL_TYPES.asScala
}

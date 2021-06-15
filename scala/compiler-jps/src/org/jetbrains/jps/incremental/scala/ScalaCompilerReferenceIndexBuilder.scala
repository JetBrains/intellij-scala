package org.jetbrains.jps.incremental.scala

import java.io.File
import java.{util => jutil}

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.{JavaModuleBuildTargetType, JavaSourceRootDescriptor}
import org.jetbrains.jps.builders.{BuildTarget, DirtyFilesHolder}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.scala.InitialScalaBuilder.hasScala
import org.jetbrains.jps.incremental.{BuilderCategory, CompileContext, ModuleBuildTarget, ModuleLevelBuilder}
import org.jetbrains.plugins.scala.compilerReferences.Builder.rebuildPropertyKey
import org.jetbrains.plugins.scala.compilerReferences.Messages._
import org.jetbrains.plugins.scala.indices.protocol.CompiledClass
import org.jetbrains.plugins.scala.indices.protocol.jps.JpsCompilationInfo

import scala.jdk.CollectionConverters._

class ScalaCompilerReferenceIndexBuilder extends ModuleLevelBuilder(BuilderCategory.CLASS_POST_PROCESSOR) {

  override def getPresentableName: String =
    JpsBundle.message("scala.compiler.reference.indexer")

  override def getCompilableFileExtensions: jutil.List[String] =
    List("scala", "java").asJava

  override def buildStarted(context: CompileContext): Unit =
    context.processMessage(CompilationStarted(shouldBeNonIncremental))

  override def buildFinished(context: CompileContext): Unit = {
    if (shouldBeNonIncremental) {
      val pd                      = context.getProjectDescriptor
      val (allClasses, timestamp) = getAllClassesInfo(context)
      val allModules = pd.getProject.getModules.asScala.filter(hasScala(context, _)).map(_.getName).toSet

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

  private[this] def getTargetTimestamps(targets: Iterable[BuildTarget[_]], context: CompileContext): Long =
    targets.collect { case target: ModuleBuildTarget =>
      val stamp = context.getCompilationStartStamp(target)

      if (stamp == 0) Long.MaxValue
      else            stamp
    }.min

  private[this] val shouldBeNonIncremental: Boolean =
    sys.props.get(rebuildPropertyKey).exists(java.lang.Boolean.valueOf(_))

  override def build(
    context:          CompileContext,
    chunk:            ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
    outputConsumer:   ModuleLevelBuilder.OutputConsumer
  ): ExitCode = if (!shouldBeNonIncremental) {
    val affectedModules = chunk.getModules.asScala.filter(hasScala(context, _)).map(_.getName).toSet

    val compiledClasses =
      outputConsumer.getCompiledClasses
        .values()
        .iterator()
        .asScala
        .map(cc => CompiledClass(ContainerUtil.getFirstItem(cc.getSourceFiles), cc.getOutputFile))
        .toSet

    val timestamp = getTargetTimestamps(chunk.getTargets.asScala, context)

    val removedSources = for {
      target      <- chunk.getTargets.asScala.toSet if target != null
      removedFile <- dirtyFilesHolder.getRemovedFiles(target).asScala
    } yield new File(removedFile)

    val data = JpsCompilationInfo(
      affectedModules,
      removedSources,
      compiledClasses,
      timestamp
    )

    context.processMessage(ChunkCompilationInfo(data))
    ExitCode.OK
  } else ExitCode.OK

  private[this] def getAllClassesInfo(context: CompileContext): (Set[CompiledClass], Long) = {
    val pd               = context.getProjectDescriptor
    val buildTargetIndex = pd.getBuildTargetIndex
    val dataManager      = pd.dataManager
    val targets          = allJavaTargetTypes.flatMap(buildTargetIndex.getAllTargets(_).asScala)
    val mappings         = targets.map(dataManager.getSourceToOutputMap).iterator

    val timestamp = getTargetTimestamps(targets, context)
    val classes = Set.newBuilder[CompiledClass]

    while (mappings.hasNext) {
      val mapping = mappings.next()
      val sources = mapping.getSourcesIterator.asScala

      sources.foreach { source =>
        val outputs    = Option(mapping.getOutputs(source)).fold(Iterable.empty[String])(_.asScala)
        val sourceFile = new File(source)
        outputs.foreach(cls => classes += CompiledClass(sourceFile, new File(cls)))
      }
    }

    (classes.result(), timestamp)
  }

  private val allJavaTargetTypes = JavaModuleBuildTargetType.ALL_TYPES.asScala
}

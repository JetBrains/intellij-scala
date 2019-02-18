package org.jetbrains.plugin.scala.compilerReferences

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}
import java.nio.charset.StandardCharsets
import java.util
import java.util.Base64
import java.util.zip.{DeflaterOutputStream, InflaterInputStream}

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.{BuildTarget, DirtyFilesHolder}
import org.jetbrains.jps.builders.java.{JavaModuleBuildTargetType, JavaSourceRootDescriptor}
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import org.jetbrains.jps.incremental.{BuilderCategory, CompileContext, ModuleBuildTarget, ModuleLevelBuilder}
import org.jetbrains.plugins.scala.indices.protocol.CompiledClass
import org.jetbrains.plugins.scala.indices.protocol.IdeaIndicesJsonProtocol._
import org.jetbrains.plugins.scala.indices.protocol.jps.JpsCompilationInfo
import spray.json._

import scala.collection.JavaConverters._
import scala.util.Try

class ScalaCompilerReferenceIndexBuilder extends ModuleLevelBuilder(BuilderCategory.CLASS_POST_PROCESSOR) {
  import ScalaCompilerReferenceIndexBuilder._

  override def getPresentableName: String                     = "scala compiler-reference indexer"
  override def getCompilableFileExtensions: util.List[String] = List("scala", "java").asJava

  override def buildStarted(context: CompileContext): Unit =
    context.processMessage(CompilationStarted(!shouldBeIncremental))

  override def buildFinished(context: CompileContext): Unit = {
    if (!shouldBeIncremental) {
      val pd                      = context.getProjectDescriptor
      val (allClasses, timestamp) = getAllClassesInfo(context)
      val allModules: Set[String] = pd.getProject.getModules.asScala.map(_.getName)(collection.breakOut)

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

  private[this] def getTargetTimestamps(targets: Traversable[BuildTarget[_]], context: CompileContext): Long =
    targets.collect { case target: ModuleBuildTarget =>
      val stamp = context.getCompilationStartStamp(target)

      if (stamp == 0) Long.MaxValue
      else            stamp
    }.min

  private[this] val shouldBeIncremental: Boolean =
    sys.props.get(propertyKey).exists(java.lang.Boolean.valueOf(_))

  override def build(
    context:          CompileContext,
    chunk:            ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
    outputConsumer:   ModuleLevelBuilder.OutputConsumer
  ): ExitCode = if (shouldBeIncremental) {
    val affectedModules: Set[String] = chunk.getModules.asScala.map(_.getName)(collection.breakOut)

    val compiledClasses =
      outputConsumer.getCompiledClasses
        .values()
        .iterator()
        .asScala
        .map(cc => CompiledClass(cc.getSourceFile, cc.getOutputFile))
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

    if (removedSources.nonEmpty || compiledClasses.nonEmpty) context.processMessage(ChunkCompilationInfo(data))

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
        val outputs    = mapping.getOutputs(source)
        val sourceFile = new File(source)
        outputs.forEach(cls => classes += CompiledClass(sourceFile, new File(cls)))
      }
    }

    (classes.result, timestamp)
  }
}

object ScalaCompilerReferenceIndexBuilder {
  val id                      = "sc.compiler.ref.index"
  val compilationDataType     = "compilation-data"
  val compilationFinishedType = "compilation-finished"
  val compilationStartedType  = "compilation-started"
  val propertyKey             = "scala.compiler.indices.rebuild"

  private[this] def tryWith[R <: AutoCloseable, T](resource: => R)(f: R => T): Try[T] =
    Try(resource).flatMap { resource =>
      Try(f(resource)).flatMap { result =>
        Try {
          if (resource != null) resource.close()
        }.map(_ => result)
      }
    }

  private val allJavaTargetTypes = JavaModuleBuildTargetType.ALL_TYPES.asScala

  def compressCompilationInfo(data: JpsCompilationInfo): String = {
    val baos     = new ByteArrayOutputStream(1024)
    val json     = data.toJson.compactPrint

    tryWith(new DeflaterOutputStream(baos))(deflater =>
      deflater.write(json.getBytes(StandardCharsets.UTF_8))
    )

    Base64.getEncoder.encodeToString(baos.toByteArray)
  }

  def decompressCompilationInfo(compressed: String): Try[JpsCompilationInfo] = {
    val decompressed = Base64.getDecoder.decode(compressed)
    val bais         = new ByteArrayInputStream(decompressed)

    val decode =
      tryWith(new InflaterInputStream(bais)) { inflater =>
        val out    = new ByteArrayOutputStream
        val buffer = new Array[Byte](8192)
        var read   = 0

        while ({ read = inflater.read(buffer); read > 0 }) {
          out.write(buffer, 0, read)
        }
        out.close()
        out.toByteArray
      }

    for {
      bytes   <- decode
      json    = new String(bytes, StandardCharsets.UTF_8)
      jpsInfo <- Try(json.parseJson.convertTo[JpsCompilationInfo])
    } yield jpsInfo
  }

  final case class ChunkCompilationInfo(data: JpsCompilationInfo)
      extends CustomBuilderMessage(id, compilationDataType, data.toJson.compactPrint)

  final case object CompilationFinished
      extends CustomBuilderMessage(id, compilationFinishedType, "")

  final case class CompilationStarted(isCleanBuild: Boolean)
      extends CustomBuilderMessage(id, compilationStartedType, isCleanBuild.toString)
}

package org.jetbrains.jps.incremental.scala.data

import org.jetbrains.jps.builders.java.{JavaBuilderUtil, JavaModuleBuildTargetType}
import org.jetbrains.jps.incremental.scala.{ChunkExclusionService, JpsBundle, SettingsManager}
import org.jetbrains.jps.incremental.{CompileContext, ModuleBuildTarget}
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.{ModuleChunk, ProjectPaths}
import org.jetbrains.plugins.scala.compiler.data.{CompilationData, ZincData}

import java.io.{File, IOException}
import java.util.Collections
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

trait CompilationDataFactory {

  def from(sources: Seq[File],
           allSources: Seq[File],
           context: CompileContext,
           chunk: ModuleChunk): Either[String, CompilationData]
}

object CompilationDataFactory
  extends CompilationDataFactory {

  private val compilationStamp = System.nanoTime()

  // This is an escape hatch from the current Either[String, CompilationData] API, for a recoverable error.
  // The factory needs to be refactored in the future.
  private[scala] final val NoCompilationData = "No compilation data. Skip target."

  override def from(sources: Seq[File],
                    allSources: Seq[File],
                    context: CompileContext,
                    chunk: ModuleChunk): Either[String, CompilationData] = {
    val target = chunk.representativeTarget
    val module = target.getModule

    outputsNotSpecified(chunk) match {
      case Some(message) => return Left(message)
      case None =>
    }
    val output = target.getOutputDir.getCanonicalFile
    checkOrCreate(output)

    val classpath = ProjectPaths.getCompilationClasspathFiles(chunk, chunk.containsTests, false, true).asScala
    val compilerSettings = SettingsManager.getProjectSettings(module.getProject).getCompilerSettings(chunk)
    val scalaOptions = CompilerDataFactory.scalaOptionsFor(compilerSettings, chunk)
    val order = compilerSettings.getCompileOrder

    createOutputToCacheMap(context).map { outputToCacheMap =>

      val cacheFile = outputToCacheMap.getOrElse(output, return Left(NoCompilationData))

      val classpathSet = classpath.toSet
      val relevantOutputToCacheMap = (outputToCacheMap - output).filter(p => classpathSet.contains(p._1))

      val preferredEncoding: Option[String] =
        Option(context.getProjectDescriptor.getEncodingConfiguration.getPreferredModuleChunkEncoding(chunk))

      def ensureEncodingIsExplicitlySet(compilerOptions: Seq[String]): Seq[String] = {
        val EncodingOptionKey = "-encoding"
        if (compilerOptions.contains(EncodingOptionKey))
          compilerOptions
        else {
          val encodingOption = preferredEncoding.toSeq.flatMap(Seq(EncodingOptionKey, _))
          encodingOption ++ compilerOptions
        }
      }

      val javaOptions = CompilerDataFactory.javaOptionsFor(context, chunk)

      val outputGroups = createOutputGroups(chunk)

      val canonicalSources = sources.map(_.getCanonicalFile)

      val isCompile =
        !JavaBuilderUtil.isCompileJavaIncrementally(context) &&
          !JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)

      CompilationData(
        sources = canonicalSources,
        classpath = classpath.toSeq,
        output = output,
        scalaOptions = ensureEncodingIsExplicitlySet(scalaOptions),
        javaOptions = ensureEncodingIsExplicitlySet(javaOptions),
        order = order,
        cacheFile = cacheFile,
        outputToCacheMap = relevantOutputToCacheMap,
        outputGroups = outputGroups,
        zincData = ZincData(allSources, compilationStamp, isCompile)
      )
    }
  }

  private def checkOrCreate(output: File): Unit = {
    if (!output.exists()) {
      try {
        if (!output.mkdirs()) throw new IOException("Cannot create output directory: " + output.toString)
      } catch {
        case t: Throwable => throw new IOException("Cannot create output directory: " + output.toString, t)
      }
    }
  }

  private def outputsNotSpecified(chunk: ModuleChunk): Option[String] = {
    val moduleNames = chunk.getTargets.asScala.filter(_.getOutputDir == null).map(_.getModule.getName)
    moduleNames.toSeq match {
      case Seq() => None
      case Seq(name) => Some(JpsBundle.message("output.directory.not.specified.for.module.name", name))
      case names => Some(names.mkString(JpsBundle.message("output.directory.not.specified.for.modules"), ", ", ""))
    }
  }

  private def createOutputToCacheMap(context: CompileContext): Either[String, Map[File, File]] = {
    val targetToOutput = targetsIn(context).collect {
      case target if target.getOutputDir != null => (target, target.getOutputDir)
    }

    outputClashesIn(targetToOutput).toLeft {
      val paths = context.getProjectDescriptor.dataManager.getDataPaths

      for ((target, output) <- targetToOutput.toMap)
        yield (
          output.getCanonicalFile,
          new File(
            paths.getTargetDataRoot(target).getCanonicalFile,
            s"cache-${target.getPresentableName}.zip")
        )
    }
  }

  private def createOutputGroups(chunk: ModuleChunk): Seq[(File, File)] =
    for {
      target <- chunk.getTargets.asScala.toSeq
      outputDir <- Option(target.getOutputDir).toSeq
      module = target.getModule
      output = outputDir.getCanonicalFile
      sourceRoot <- module.getSourceRoots.asScala
      if sourceRoot.getRootType.isForTests == target.isTests
      sourceRootFile = sourceRoot.getFile.getCanonicalFile
      if sourceRootFile.exists
    } yield (sourceRootFile, output)
  
  private def targetsIn(context: CompileContext): Seq[ModuleBuildTarget] = {
    def isExcluded(target: ModuleBuildTarget): Boolean =
      ChunkExclusionService.isExcluded(chunk(target))

    def isProductionTargetOfTestModule(target: ModuleBuildTarget): Boolean = {
      target.getTargetType == JavaModuleBuildTargetType.PRODUCTION &&
        JpsJavaExtensionService.getInstance.getTestModuleProperties(target.getModule) != null
    }

    val buildTargetIndex = context.getProjectDescriptor.getBuildTargetIndex
    val targets = JavaModuleBuildTargetType.ALL_TYPES.iterator.asScala.flatMap(buildTargetIndex.getAllTargets(_).asScala)

    targets.distinct
      .filterNot(target => buildTargetIndex.isDummy(target) || isExcluded(target) || isProductionTargetOfTestModule(target))
      .to(ArraySeq)
  }

  private def chunk(target: ModuleBuildTarget): ModuleChunk =
    new ModuleChunk(Collections.singleton(target))

  private def outputClashesIn(targetToOutput: Seq[(ModuleBuildTarget, File)]): Option[String] = {
    val outputToTargetsMap = targetToOutput.groupBy(_._2).view.mapValues(_.map(_._1))

    val errors = outputToTargetsMap.collect {
      case (output, targets) if output != null && targets.length > 1 =>
        val targetNames = targets.map(_.getPresentableName).mkString(", ")
        JpsBundle.message("output.path.shared.between", output, targetNames)
    }

    if (errors.isEmpty) None else Some(
      errors.mkString("\n") +
      JpsBundle.message("configure.separate.output.paths"))
  }
}

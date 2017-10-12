package org.jetbrains.jps.incremental.scala
package data

import java.io.{File, IOException}
import java.util
import java.util.Collections

import org.jetbrains.jps.builders.java.{JavaBuilderUtil, JavaModuleBuildTargetType}
import org.jetbrains.jps.incremental.java.JavaBuilder
import org.jetbrains.jps.incremental.scala.model.{CompileOrder, CompilerSettings}
import org.jetbrains.jps.incremental.{CompileContext, ModuleBuildTarget}
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions
import org.jetbrains.jps.{ModuleChunk, ProjectPaths}

import scala.collection.JavaConverters._

case class ZincData(allSources: Seq[File], compilationStartDate: Long, isCompile: Boolean)

/**
 * @author Pavel Fatin
 */
case class CompilationData(sources: Seq[File],
                           classpath: Seq[File],
                           output: File,
                           scalaOptions: Seq[String],
                           javaOptions: Seq[String],
                           order: CompileOrder,
                           cacheFile: File,
                           outputToCacheMap: Map[File, File],
                           outputGroups: Seq[(File, File)],
                           zincData: ZincData){
  def allSourceFilesCount: Option[Int] = Some(zincData.allSources.size)
}

object CompilationData {
  private val compilationStamp = System.nanoTime()

  def from(sources: Seq[File], allSources: Seq[File], context: CompileContext, chunk: ModuleChunk): Either[String, CompilationData] = {
    val target = chunk.representativeTarget
    val module = target.getModule

    outputsNotSpecified(chunk) match {
      case Some(message) => return Left(message)
      case None =>
    }
    val output = target.getOutputDir.getCanonicalFile
    checkOrCreate(output)

    val classpath = ProjectPaths.getCompilationClasspathFiles(chunk, chunk.containsTests, false, true).asScala.toSeq
    val compilerSettings = SettingsManager.getProjectSettings(module.getProject).getCompilerSettings(chunk)
    val scalaOptions = scalaOptionsFor(compilerSettings, chunk)
    val order = compilerSettings.getCompileOrder

    createOutputToCacheMap(context).map { outputToCacheMap =>

      val cacheFile = outputToCacheMap.getOrElse(output, {
        val message =
          s"""Unknown build target output directory: $output
             |Current outputs:
             |${outputToCacheMap.keys.mkString("\n")}
           """.stripMargin
        throw new RuntimeException(message)
      })

      val relevantOutputToCacheMap = (outputToCacheMap - output).filter(p => classpath.contains(p._1))

      val commonOptions = {
        val encoding = context.getProjectDescriptor.getEncodingConfiguration.getPreferredModuleChunkEncoding(chunk)
        Option(encoding).map(Seq("-encoding", _)).getOrElse(Seq.empty)
      }

      val javaOptions = javaOptionsFor(context, chunk)

      val outputGroups = createOutputGroups(chunk)

      val canonicalSources = sources.map(_.getCanonicalFile)

      val hydraSettings = SettingsManager.getHydraSettings(context.getProjectDescriptor.getProject)
      val scalaVersion = CompilerData.compilerVersion(module)
      val hydraOptions =
        if (hydraSettings.isHydraEnabled && scalaVersion.nonEmpty && hydraSettings.getArtifactPaths.containsKey(scalaVersion.get))
          Seq("-sourcepath", outputGroups.map(_._1).mkString(File.pathSeparator), "-cpus", "2")
        else
          Seq.empty

      val isCompile =
        !JavaBuilderUtil.isCompileJavaIncrementally(context) &&
          !JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)

      CompilationData(canonicalSources, classpath, output, commonOptions ++ scalaOptions ++ hydraOptions, commonOptions ++ javaOptions,
        order, cacheFile, relevantOutputToCacheMap, outputGroups,
        ZincData(allSources, compilationStamp, isCompile))
    }
  }

  def checkOrCreate(output: File) {
    if (!output.exists()) {
      try {
        if (!output.mkdirs()) throw new IOException("Cannot create output directory: " + output.toString)
      } catch {
        case t: Throwable => throw new IOException("Cannot create output directory: " + output.toString, t)
      }
    }
  }

  def outputsNotSpecified(chunk: ModuleChunk): Option[String] = {
    val moduleNames = chunk.getTargets.asScala.filter(_.getOutputDir == null).map(_.getModule.getName)
    moduleNames.toSeq match {
      case Seq() => None
      case Seq(name) => Some(s"Output directory not specified for module $name")
      case names => Some(names.mkString(s"Output directory not specified for modules ", ", ", ""))
    }
  }

  def scalaOptionsFor(compilerSettings: CompilerSettings, chunk: ModuleChunk): Array[String] = {
    val noBootCp = if (CompilerData.needNoBootCp(chunk)) Nil else Seq("-nobootcp", "-javabootclasspath", File.pathSeparator)
    val scalaOptions = noBootCp ++: compilerSettings.getCompilerOptions
    scalaOptions
  }

  def javaOptionsFor(context: CompileContext, chunk: ModuleChunk): Seq[String] = {
    val compilerConfig = {
      val project = context.getProjectDescriptor.getProject
      JpsJavaExtensionService.getInstance.getOrCreateCompilerConfiguration(project)
    }

    val options = new util.ArrayList[String]()

    addCommonJavacOptions(options, compilerConfig.getCurrentCompilerOptions)

    val annotationProcessingProfile = {
      val module = chunk.representativeTarget.getModule
      compilerConfig.getAnnotationProcessingProfile(module)
    }

    JavaBuilder.addCompilationOptions(options, context, chunk, annotationProcessingProfile)

    options.asScala
  }

  // TODO JavaBuilder.loadCommonJavacOptions should be public
  def addCommonJavacOptions(options: util.ArrayList[String], compilerOptions: JpsJavaCompilerOptions) {
    if (compilerOptions.DEBUGGING_INFO) {
      options.add("-g")
    }

    if (compilerOptions.DEPRECATION) {
      options.add("-deprecation")
    }

    if (compilerOptions.GENERATE_NO_WARNINGS) {
      options.add("-nowarn")
    }

    if (!compilerOptions.ADDITIONAL_OPTIONS_STRING.isEmpty) {
      // TODO extract VM options
      options.addAll(compilerOptions.ADDITIONAL_OPTIONS_STRING.split("\\s+").toSeq.asJava)
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

  private def createOutputGroups(chunk: ModuleChunk): Seq[(File, File)] = {
    for {
      target <- chunk.getTargets.asScala.toSeq
      if target.getOutputDir != null
      module = target.getModule
      output = target.getOutputDir.getCanonicalFile
      sourceRoot <- module.getSourceRoots.asScala.map(_.getFile.getCanonicalFile)
      if sourceRoot.exists
    } yield (sourceRoot, output)
  }

  private def targetsIn(context: CompileContext): Seq[ModuleBuildTarget] = {
    def isExcluded(target: ModuleBuildTarget): Boolean = {
      val chunk = new ModuleChunk(Collections.singleton(target))
      ChunkExclusionService.isExcluded(chunk)
    }

    def isProductionTargetOfTestModule(target: ModuleBuildTarget): Boolean = {
      target.getTargetType == JavaModuleBuildTargetType.PRODUCTION &&
        JpsJavaExtensionService.getInstance.getTestModuleProperties(target.getModule) != null
    }

    val buildTargetIndex = context.getProjectDescriptor.getBuildTargetIndex
    val targets = JavaModuleBuildTargetType.ALL_TYPES.asScala.flatMap(buildTargetIndex.getAllTargets(_).asScala)

    targets.distinct.filterNot { target =>
      buildTargetIndex.isDummy(target) || isExcluded(target) || isProductionTargetOfTestModule(target)
    }
  }

  private def outputClashesIn(targetToOutput: Seq[(ModuleBuildTarget, File)]): Option[String] = {
    val outputToTargetsMap = targetToOutput.groupBy(_._2).mapValues(_.map(_._1))

    val errors = outputToTargetsMap.collect {
      case (output, targets) if output != null && targets.length > 1 =>
        val targetNames = targets.map(_.getPresentableName).mkString(", ")
        "Output path %s is shared between: %s".format(output, targetNames)
    }

    if (errors.isEmpty) None else Some(errors.mkString("\n") +
            "\nPlease configure separate output paths to proceed with the compilation." +
            "\nTIP: you can use Project Artifacts to combine compiled classes if needed.")
  }
}
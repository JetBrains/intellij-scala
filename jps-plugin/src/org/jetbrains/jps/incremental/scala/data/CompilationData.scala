package org.jetbrains.jps.incremental.scala
package data

import java.io.File
import org.jetbrains.jps.incremental.{ModuleBuildTarget, CompileContext}
import org.jetbrains.jps.{ProjectPaths, ModuleChunk}
import org.jetbrains.jps.incremental.scala.SettingsManager
import collection.JavaConverters._
import org.jetbrains.jps.incremental.scala.model.Order
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.LanguageLevel._
import org.jetbrains.jps.model.module.JpsModule
import java.util.Collections

/**
 * @author Pavel Fatin
 */
case class CompilationData(sources: Seq[File],
                           classpath: Seq[File],
                           output: File,
                           scalaOptions: Seq[String],
                           javaOptions: Seq[String],
                           order: Order,
                           cacheFile: File,
                           outputToCacheMap: Map[File, File])

object CompilationData {
  def from(sources: Seq[File], context: CompileContext, chunk: ModuleChunk): Either[String, CompilationData] = {
    val target = chunk.representativeTarget
    val module = target.getModule

    Option(target.getOutputDir)
            .toRight("Output directory not specified for module " + module.getName)
            .flatMap { output =>

      val classpath = ProjectPaths.getCompilationClasspathFiles(chunk, chunk.containsTests, false, false).asScala.toSeq

      val facetSettings = Option(SettingsManager.getFacetSettings(module))

      val scalaOptions = facetSettings.map(_.getCompilerOptions.toSeq).getOrElse(Seq.empty)

      val order = facetSettings.map(_.getCompileOrder).getOrElse(Order.Mixed)

      createOutputToCacheMap(context).map { outputToCacheMap =>

        val cacheFile = outputToCacheMap.get(output).getOrElse {
          throw new RuntimeException("Unknown build target output directory: " + output)
        }

        val relevantOutputToCacheMap = (outputToCacheMap - output).filter(p => classpath.contains(p._1))

        val commonOptions = {
          val encoding = context.getProjectDescriptor.getEncodingConfiguration.getPreferredModuleChunkEncoding(chunk)
          Option(encoding).map(Seq("-encoding", _)).getOrElse(Seq.empty)
        }

        val javaOptions = javaOptionsFor(module)

        CompilationData(sources, classpath, output, commonOptions ++ scalaOptions, commonOptions ++ javaOptions, order, cacheFile, relevantOutputToCacheMap)
      }
    }
  }

  // TODO expect future JPS API to provide a public API for Java options retrieval
  private def javaOptionsFor(module: JpsModule): Seq[String] = {
    val config = {
      val project = module.getProject
      val compilerConfig = JpsJavaExtensionService.getInstance.getOrCreateCompilerConfiguration(project)
      compilerConfig.getCurrentCompilerOptions
    }

    var options: Vector[String] = Vector.empty

    if (config.DEBUGGING_INFO) {
      options :+= "-g"
    }

    if (config.DEPRECATION) {
      options :+= "-deprecation"
    }

    if (config.GENERATE_NO_WARNINGS) {
      options :+= "-nowarn"
    }

    javaLanguageLevelFor(module).foreach { level =>
      options :+= "-source"
      options :+= level
    }

    if (!config.ADDITIONAL_OPTIONS_STRING.isEmpty) {
      options ++= config.ADDITIONAL_OPTIONS_STRING.split("\\s+").toSeq
    }

    options
  }

  private def javaLanguageLevelFor(module: JpsModule): Option[String] = {
    Option(JpsJavaExtensionService.getInstance.getLanguageLevel(module)).collect {
      case JDK_1_3 => "1.3"
      case JDK_1_4 => "1.4"
      case JDK_1_5 => "1.5"
      case JDK_1_6 => "1.6"
      case JDK_1_7 => "1.7"
      case JDK_1_8 => "8"
    }
  }

  private def createOutputToCacheMap(context: CompileContext): Either[String, Map[File, File]] = {
    val targetToOutput = targetsIn(context).map(target => (target, target.getOutputDir))

    outputClashesIn(targetToOutput).toLeft {
      val paths = context.getProjectDescriptor.dataManager.getDataPaths

      for ((target, output) <- targetToOutput.toMap)
      yield (output, new File(paths.getTargetDataRoot(target), "cache.dat"))
    }
  }

  private def targetsIn(context: CompileContext): Seq[ModuleBuildTarget] = {
    val targets = {
      val buildTargetIndex = context.getProjectDescriptor.getBuildTargetIndex
      JavaModuleBuildTargetType.ALL_TYPES.asScala.flatMap(buildTargetIndex.getAllTargets(_).asScala)
    }

    targets.filterNot { target =>
      val chunk = new ModuleChunk(Collections.singleton(target))
      ChunkExclusionService.isExcluded(chunk)
    }
  }

  private def outputClashesIn(targetToOutput: Seq[(ModuleBuildTarget, File)]): Option[String] = {
    val outputToTargetsMap = targetToOutput.groupBy(_._2).mapValues(_.map(_._1))

    val errors = outputToTargetsMap.collect {
      case (output, targets) if targets.length > 1 =>
        val targetNames = targets.map(_.getPresentableName).mkString(", ")
        "Output path %s is shared between: %s".format(output, targetNames)
    }

    if (errors.isEmpty) None else Some(errors.mkString("\n") +
            "\nPlease configure separate output paths to proceed with the compilation." +
            "\nTIP: you can use Project Artifacts to combine compiled classes if needed.")
  }
}
package org.jetbrains.jps.incremental.scala
package data

import java.io.File
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.{ProjectPaths, ModuleChunk}
import org.jetbrains.jps.incremental.scala.SettingsManager
import collection.JavaConverters._
import org.jetbrains.jps.incremental.scala.model.Order
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.LanguageLevel._
import org.jetbrains.jps.model.module.JpsModule

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
            .map { output =>

      val classpath = ProjectPaths.getCompilationClasspathFiles(chunk, chunk.containsTests, false, false).asScala.toSeq

      val facetSettings = Option(SettingsManager.getFacetSettings(module))

      val scalaOptions = facetSettings.map(_.getCompilerOptions.toSeq).getOrElse(Seq.empty)

      val order = facetSettings.map(_.getCompileOrder).getOrElse(Order.Mixed)

      val outputToCacheMap = createOutputToCacheMap(context)

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

  private def createOutputToCacheMap(context: CompileContext): Map[File, File] = {
    val buildTargetIndex = context.getProjectDescriptor.getBuildTargetIndex
    val paths = context.getProjectDescriptor.dataManager.getDataPaths

    val pairs = for (targetType <- JavaModuleBuildTargetType.ALL_TYPES.asScala;
                     target <- buildTargetIndex.getAllTargets(targetType).asScala) yield {
      val targetDirectory = target.getOutputDir
      val cache = new File(paths.getTargetDataRoot(target), "cache.dat")
      (targetDirectory, cache)
    }

    pairs.toMap
  }
}
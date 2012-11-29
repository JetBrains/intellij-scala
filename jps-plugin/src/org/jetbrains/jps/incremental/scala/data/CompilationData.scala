package org.jetbrains.jps.incremental.scala
package data

import java.io.File
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.scala.SettingsManager
import collection.JavaConverters._
import org.jetbrains.jps.incremental.scala.model.Order
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType

/**
 * @author Pavel Fatin
 */
case class CompilationData(sources: Seq[File],
                           classpath: Seq[File],
                           output: File,
                           options: Seq[String],
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

      val classpath = context.getProjectPaths
              .getCompilationClasspathFiles(chunk, chunk.containsTests, false, false).asScala.toSeq

      val options = Option(SettingsManager.getFacetSettings(module))
              .map(_.getCompilerOptions.toSeq).getOrElse(Seq.empty)

      val order = {
        val project = context.getProjectDescriptor.getModel.getProject
        val projectSettings = SettingsManager.getProjectSettings(project)
        projectSettings.getCompilationOrder
      }

      val outputToCacheMap = createOutputToCacheMap(context)

      val cacheFile = outputToCacheMap.get(output).getOrElse {
        throw new RuntimeException("Unknown build target output directory: " + output)
      }

      val relevantOutputToCacheMap = (outputToCacheMap - output).filter(p => classpath.contains(p._1))

      CompilationData(sources, classpath, output, options, order, cacheFile, relevantOutputToCacheMap)
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
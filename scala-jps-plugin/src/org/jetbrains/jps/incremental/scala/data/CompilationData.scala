package org.jetbrains.jps.incremental.scala
package data

import java.io.File
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.scala.SettingsManager
import collection.JavaConverters._
import org.jetbrains.jps.incremental.scala.model.Order

/**
 * @author Pavel Fatin
 */
case class CompilationData(sources: Seq[File],
                           classpath: Seq[File],
                           output: File,
                           options: Seq[String],
                           order: Order,
                           cacheFile: File)

object CompilationData {
  def from(sources: Seq[File], context: CompileContext, chunk: ModuleChunk): Either[String, CompilationData] = {
    val target = chunk.representativeTarget
    val module = target.getModule

    Option(SettingsManager.getFacetSettings(module))
            .toRight("No Scala facet in module " + module.getName)
            .flatMap { facet =>

      Option(target.getOutputDir)
              .toRight("Output directory not specified for module " + target.getModuleName)
              .map { output =>

        val classpath = context.getProjectPaths
                .getCompilationClasspathFiles(chunk, chunk.containsTests, false, false).asScala.toSeq

        val options = facet.getCompilerOptions.toSeq

        val order = {
          val project = context.getProjectDescriptor.getModel.getProject
          val projectSettings = SettingsManager.getProjectSettings(project)
          projectSettings.getCompilationOrder
        }

        val cacheFile = {
          val paths = context.getProjectDescriptor.dataManager.getDataPaths
          new File(paths.getTargetDataRoot(chunk.representativeTarget), "cache.dat")
        }

        CompilationData(sources, classpath, output, options, order, cacheFile)
      }
    }
  }
}
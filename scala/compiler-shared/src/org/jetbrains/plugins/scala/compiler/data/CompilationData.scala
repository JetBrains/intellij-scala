package org.jetbrains.plugins.scala.compiler.data

import java.io.File

import org.jetbrains.plugins.scala.compiler.CompileOrder

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
                           zincData: ZincData) {

  def allSourceFilesCount: Option[Int] =
    Some(zincData.allSources.size)
}

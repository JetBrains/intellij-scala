package org.jetbrains.plugins.scala.compiler.data

import java.io.File

import org.jetbrains.plugins.scala.compiler.CompileOrder

/**
 * @author Pavel Fatin
 */
case class CompilationData(sources: collection.Seq[File],
                           classpath: collection.Seq[File],
                           output: File,
                           scalaOptions: collection.Seq[String],
                           javaOptions: collection.Seq[String],
                           order: CompileOrder,
                           cacheFile: File,
                           outputToCacheMap: collection.Map[File, File],
                           outputGroups: collection.Seq[(File, File)],
                           zincData: ZincData)

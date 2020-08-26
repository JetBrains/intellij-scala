package org.jetbrains.jps.incremental.scala.local.zinc

import java.io.File

import org.jetbrains.plugins.scala.compiler.data.CompilationData
import sbt.internal.inc.Analysis
import xsbti.compile.CompileAnalysis

case class BinaryToSource(compileAnalysis: CompileAnalysis, compilationData: CompilationData) {
  private val analysis = compileAnalysis.asInstanceOf[Analysis]
  private val binaryToSource = analysis.relations.srcProd.reverseMap

  def classfileToSources(file: File): Set[File] =
    binaryToSource.getOrElse(file, Set.empty)

  def classfilesToSources(classfiles: Array[File]): Set[File] =
    classfiles.flatMap(classfileToSources).toSet

  private val ouputPath = compilationData.output.toPath
  
  private val extensionLenght = ".class".length

  def className(classFile: File): String =
    ouputPath.relativize(classFile.toPath).toString.replace(File.separator, ".").dropRight(extensionLenght)
}

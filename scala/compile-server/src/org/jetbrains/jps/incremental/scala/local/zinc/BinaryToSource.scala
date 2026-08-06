package org.jetbrains.jps.incremental.scala.local.zinc

import org.jetbrains.plugins.scala.compiler.data.CompilationData
import sbt.internal.inc.{Analysis, PlainVirtualFileConverter}
import xsbti.compile.CompileAnalysis

import java.nio.file.Path

case class BinaryToSource(compileAnalysis: CompileAnalysis, compilationData: CompilationData) {
  private val analysis = compileAnalysis.asInstanceOf[Analysis]
  private val binaryToSource = analysis.relations.srcProd.reverseMap

  def classfileToSources(file: Path): Set[Path] =
    binaryToSource
      .getOrElse(PlainVirtualFileConverter.converter.toVirtualFile(file), Set.empty)
      .map(PlainVirtualFileConverter.converter.toPath)

  def classfilesToSources(classfiles: Array[Path]): Set[Path] =
    classfiles.flatMap(classfileToSources).toSet

  private val ouputPath = compilationData.output
  
  private val extensionLength = ".class".length

  def className(classFile: Path): String =
    ouputPath.relativize(classFile).toString.replace(java.io.File.separator, ".").dropRight(extensionLength)
}

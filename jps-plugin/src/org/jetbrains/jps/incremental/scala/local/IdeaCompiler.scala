package org.jetbrains.jps.incremental.scala
package local

import sbt.compiler.{CompilerCache, CompilerArguments, CompileOutput, AnalyzingCompiler}
import org.jetbrains.jps.incremental.scala.data.CompilationData
import java.io.File
import xsbti.compile.DependencyChanges
import xsbti.{Position, Severity}
import xsbti.api.SourceAPI

/**
 * Nikolay.Tropin
 * 11/18/13
 */
class IdeaCompiler(scalac: AnalyzingCompiler) extends AbstractCompiler {
  def compile(compilationData: CompilationData, client: Client): Unit = {
    val progress = getProgress(client)
    val reporter = getReporter(client)
    val logger = getLogger(client)
    val clientCallback = new ClassGeneratedClientCallback(client)

    import compilationData._

    val out = CompileOutput(output)
    val cArgs = new CompilerArguments(scalac.scalaInstance, scalac.cp)
    val options = cArgs(Nil, classpath, None, scalaOptions)

    scalac.compile(sources, emptyChanges, options, out, clientCallback, reporter, CompilerCache.fresh, logger, Option(progress))
  }

}

private class ClassGeneratedClientCallback(client: Client) extends xsbti.AnalysisCallback {

  def generatedClass(source: File, module: File, name: String) {
    client.generated(source, module, name)
  }

  def problem(what: String, pos: Position, msg: String, severity: Severity, reported: Boolean) {}
  def api(sourceFile: File, source: SourceAPI) {}
  def endSource(sourcePath: File) {}
  def binaryDependency(binary: File, name: String, source: File, publicInherited: Boolean) {}
  def sourceDependency(dependsOn: File, source: File, publicInherited: Boolean) {}
  def beginSource(source: File) {}
}

private object emptyChanges extends DependencyChanges {
  val modifiedBinaries = new Array[File](0)
  val modifiedClasses = new Array[String](0)
  def isEmpty = true
}
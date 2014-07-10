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
class IdeaIncrementalCompiler(scalac: AnalyzingCompiler) extends AbstractCompiler {
  def compile(compilationData: CompilationData, client: Client): Unit = {
    val progress = getProgress(client)
    val reporter = getReporter(client)
    val logger = getLogger(client)
    val clientCallback = new ClientCallback(client)

    val out =
      if (compilationData.outputGroups.size <= 1) CompileOutput(compilationData.output)
      else CompileOutput(compilationData.outputGroups: _*)
    val cArgs = new CompilerArguments(scalac.scalaInstance, scalac.cp)
    val options = "IntellijIdea.simpleAnalysis" +: cArgs(Nil, compilationData.classpath, None, compilationData.scalaOptions)

    try scalac.compile(compilationData.sources, emptyChanges, options, out, clientCallback, reporter, CompilerCache.fresh, logger, Option(progress))
    catch {
      case _: xsbti.CompileFailed => // the error should be already handled via the `reporter`
    }
  }

}

private class ClientCallback(client: Client) extends ClientCallbackBase {

  override def generatedClass(source: File, module: File, name: String) {
    client.generated(source, module, name)
  }

  override def endSource(source: File) {
    client.processed(source)
  }

  override def nameHashing() = false
}

abstract class ClientCallbackBase extends xsbti.AnalysisCallback {
  override def sourceDependency(dependsOn: File, source: File, publicInherited: Boolean): Unit = {}
  override def binaryDependency(binary: File, name: String, source: File, publicInherited: Boolean): Unit = {}
  override def generatedClass(source: File, module: File, name: String): Unit = {}
  override def beginSource(p1: File) = {}
  override def endSource(sourcePath: File): Unit = {}
  override def api(sourceFile: File, source: SourceAPI): Unit = {}
  override def problem(what: String, pos: Position, msg: String, severity: Severity, reported: Boolean): Unit = {}
  override def usedName(p1: File, p2: String) = {}
}

private object emptyChanges extends DependencyChanges {
  val modifiedBinaries = new Array[File](0)
  val modifiedClasses = new Array[String](0)
  def isEmpty = true
}
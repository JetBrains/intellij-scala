package org.jetbrains.jps.incremental.scala
package local

import java.io.File
import java.nio.file.Path
import java.util
import java.util.Optional

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.data.CompilationData
import sbt.internal.inc.{AnalyzingCompiler, CompileOutput, CompilerArguments}
import xsbti._
import xsbti.api.{ClassLike, DependencyContext}
import xsbti.compile.{CompilerCache, DependencyChanges}

import scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 11/18/13
 */
class IdeaIncrementalCompiler(scalac: AnalyzingCompiler) extends AbstractCompiler {
  def compile(compilationData: CompilationData, client: Client): Unit = {
    val progress = getProgress(client)
    val reporter = getReporter(client)
    val logFilter = new ZincLogFilter {
      override def shouldLog(serverity: Kind, msg: String): Boolean = true
    }
    val logger = getLogger(client, logFilter)
    val clientCallback = new ClientCallback(client, compilationData.output.toPath)

    val out =
      if (compilationData.outputGroups.size <= 1) CompileOutput(compilationData.output)
      else CompileOutput(compilationData.outputGroups: _*)
    val cArgs = new CompilerArguments(scalac.scalaInstance, scalac.classpathOptions)
    val options = "IntellijIdea.simpleAnalysis" +: cArgs(Nil, compilationData.classpath, None, compilationData.scalaOptions)


    try {
      scalac.compile(compilationData.sources.toArray, emptyChanges, options.toArray, out, clientCallback, reporter, CompilerCache.fresh, logger, Optional.of(progress))
    }
    catch {
      case _: xsbti.CompileFailed => // the error should be already handled via the `reporter`
      case t: Throwable =>
        client.trace(t)
    }
  }

}

private class ClientCallback(client: Client, output: Path) extends ClientCallbackBase {

  override def generatedNonLocalClass(source: File, classFile: File, binaryClassName: String, srcClassName: String): Unit = {
    client.generated(source, classFile, binaryClassName)
  }

  override def generatedLocalClass(source: File, classFile: File): Unit = {
    val classFilePath = classFile.toPath
    if(classFilePath.startsWith(output)){
      val relative = output.relativize(classFilePath)

      val binaryClassName = relative.iterator().asScala.mkString(".").dropRight(".class".length)
      client.generated(source, classFile, binaryClassName)
    }
  }

  override def enabled(): Boolean = false
}

abstract class ClientCallbackBase extends xsbti.AnalysisCallback {
  override def api(sourceFile: File, classApi: ClassLike): Unit = {}

  override def binaryDependency(onBinary: File, onBinaryClassName: String, fromClassName: String, fromSourceFile: File, context: DependencyContext): Unit = {}

  override def classDependency(onClassName: String, sourceClassName: String, context: DependencyContext): Unit = {}

  override def generatedLocalClass(source: File, classFile: File): Unit = {}

  override def generatedNonLocalClass(source: File, classFile: File, binaryClassName: String, srcClassName: String): Unit = {}

  override def problem(what: String, position: Position, x$3: String, msg: Severity, reported: Boolean): Unit = {}

  override def startSource(source: File): Unit = {}

  override def usedName(className: String, name: String, useScopes: util.EnumSet[xsbti.UseScope]): Unit = {}

  override def apiPhaseCompleted(): Unit = {}

  override def dependencyPhaseCompleted(): Unit = {}

  override def enabled(): Boolean = false

  override def mainClass(sourceFile: File, className: String): Unit = {}

  override def classesInOutputJar(): java.util.Set[String] = java.util.Collections.emptySet()
}

private object emptyChanges extends DependencyChanges {
  val modifiedBinaries = new Array[File](0)
  val modifiedClasses = new Array[String](0)

  def isEmpty = true
}
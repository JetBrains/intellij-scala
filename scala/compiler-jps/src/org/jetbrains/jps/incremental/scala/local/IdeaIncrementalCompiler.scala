package org.jetbrains.jps.incremental.scala.local

import java.io.File
import java.nio.file.Path
import java.util
import java.util.Optional

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.local.zinc.Utils
import org.jetbrains.jps.incremental.scala.{Client, ZincLogFilter}
import org.jetbrains.plugins.scala.compiler.data.CompilationData
import sbt.internal.inc.{AnalyzingCompiler, CompileOutput, CompilerArguments}
import xsbti._
import xsbti.api.{ClassLike, DependencyContext}
import xsbti.compile.DependencyChanges

import scala.jdk.CollectionConverters._

class IdeaIncrementalCompiler(scalac: AnalyzingCompiler)
  extends AbstractCompiler {

  override def compile(compilationData: CompilationData, client: Client): Unit = {
    val progress = getProgress(client, compilationData.sources.size)
    val reporter = getReporter(client)
    val logFilter = new ZincLogFilter {
      override def shouldLog(serverity: Kind, msg: String): Boolean = true
    }
    val logger = getLogger(client, logFilter)
    val clientCallback = new ClientCallback(client, compilationData.output.toPath)

    val outputDirsCount = compilationData.outputGroups.map(_._2).distinct.size
    val out =
      if (outputDirsCount <= 1) {
        CompileOutput(compilationData.output.toPath)
      } else {
        val groups = compilationData.outputGroups.map {
          case (source, target) => (source.toPath, target.toPath)
        }.toSeq
        CompileOutput(groups: _*)
      }

    try {
      scalac.compile(
        compilationData.sources.toArray.map(file => Utils.virtualFileConverter.toVirtualFile(file.toPath)),
        compilationData.classpath.map(file => Utils.virtualFileConverter.toVirtualFile(file.toPath)).toArray,
        Utils.virtualFileConverter,
        emptyChanges,
        compilationData.scalaOptions.toArray,
        out,
        clientCallback,
        reporter,
        Optional.of(progress),
        logger
      )
    }
    catch {
      case _: xsbti.CompileFailed => // the error should be already handled via the `reporter`
      case t: Throwable =>
        client.trace(t)
    }
  }

}

private class ClientCallback(client: Client, output: Path) extends ClientCallbackBase {

  override def generatedNonLocalClass(source: VirtualFileRef,
                                      classFile: Path,
                                      binaryClassName: String,
                                      srcClassName: String): Unit =
    client.generated(Utils.virtualFileConverter.toPath(source).toFile, classFile.toFile, binaryClassName)

  override def generatedLocalClass(source: VirtualFileRef, classFile: Path): Unit =
    if (classFile.startsWith(output)) {
      val relative = output.relativize(classFile)
      val binaryClassName = relative.iterator().asScala.mkString(".").dropRight(".class".length)
      client.generated(Utils.virtualFileConverter.toPath(source).toFile, classFile.toFile, binaryClassName)
    }

  override def enabled(): Boolean = false
}

abstract class ClientCallbackBase extends xsbti.AnalysisCallback {

  override def isPickleJava: Boolean = false

  override def getPickleJarPair: Optional[T2[Path, Path]] = Optional.empty()

  override def api(sourceFile: VirtualFileRef, classApi: ClassLike): Unit = {}

  final override def api(sourceFile: File, classApi: ClassLike): Unit =
    api(Utils.virtualFileConverter.toVirtualFile(sourceFile.toPath), classApi)

  override def binaryDependency(onBinaryEntry: Path,
                                onBinaryClassName: String,
                                fromClassName: String,
                                fromSourceFile: VirtualFileRef,
                                context: DependencyContext): Unit = {}

  final override def binaryDependency(onBinary: File,
                                      onBinaryClassName: String,
                                      fromClassName: String,
                                      fromSourceFile: File,
                                      context: DependencyContext): Unit =
    binaryDependency(
      onBinary.toPath,
      onBinaryClassName,
      fromClassName,
      Utils.virtualFileConverter.toVirtualFile(fromSourceFile.toPath),
      context
    )

  override def classDependency(onClassName: String, sourceClassName: String, context: DependencyContext): Unit = {}

  override def classesInOutputJar(): util.Set[String] =
    Set.empty[String].asJava

  override def generatedLocalClass(source: File, classFile: File): Unit =
    generatedLocalClass(Utils.virtualFileConverter.toVirtualFile(source.toPath), classFile.toPath)

  final override def generatedNonLocalClass(source: File,
                                            classFile: File,
                                            binaryClassName: String,
                                            srcClassName: String): Unit =
    generatedNonLocalClass(
      Utils.virtualFileConverter.toVirtualFile(source.toPath),
      classFile.toPath,
      binaryClassName,
      srcClassName
    )

  override def problem(what: String, position: Position, x$3: String, msg: Severity, reported: Boolean): Unit = {}

  override def startSource(source: VirtualFile): Unit = {}

  final override def startSource(source: File): Unit =
    startSource(Utils.virtualFileConverter.toVirtualFile(source.toPath))

  override def usedName(className: String, name: String, useScopes: util.EnumSet[xsbti.UseScope]): Unit = {}

  override def apiPhaseCompleted(): Unit = {}

  override def dependencyPhaseCompleted(): Unit = {}

  override def enabled(): Boolean = false

  override def mainClass(sourceFile: VirtualFileRef, className: String): Unit = {}

  final override def mainClass(sourceFile: File, className: String): Unit =
    mainClass(Utils.virtualFileConverter.toVirtualFile(sourceFile.toPath), className)
}

private object emptyChanges extends DependencyChanges {
  override val modifiedBinaries: Array[File] = Array.empty
  override val modifiedClasses: Array[String] = Array.empty
  override val modifiedLibraries: Array[VirtualFileRef] = Array.empty

  override def isEmpty = true
}
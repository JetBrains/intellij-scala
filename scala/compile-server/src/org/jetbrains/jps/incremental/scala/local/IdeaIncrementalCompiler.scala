package org.jetbrains.jps.incremental.scala.local

import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.compiler.data.CompilationData
import sbt.internal.inc.{AnalyzingCompiler, CompileOutput, PlainVirtualFileConverter}
import xsbti._
import xsbti.api.{ClassLike, DependencyContext}
import xsbti.compile.DependencyChanges

import java.io.File
import java.nio.file.Path
import java.util
import java.util.Optional
import scala.jdk.CollectionConverters._

class IdeaIncrementalCompiler(scalac: AnalyzingCompiler)
  extends AbstractCompiler {

  override def compile(compilationData: CompilationData, client: Client): Unit = {
    val progress = getProgress(client, compilationData.sources.size)
    val reporter = getReporter(client)
    val logger = getLogger(client)
    val clientCallback = new ClientCallback(client, compilationData.output.toPath)

    val outputDirsCount = compilationData.outputGroups.map(_._2).distinct.size
    val out =
      if (outputDirsCount <= 1) {
        CompileOutput(compilationData.output.toPath)
      } else {
        val groups = compilationData.outputGroups.map {
          case (source, target) => (source.toPath, target.toPath)
        }
        CompileOutput(groups: _*)
      }

    try {
      scalac.compile(
        compilationData.sources.toArray.map(file => PlainVirtualFileConverter.converter.toVirtualFile(file.toPath)),
        compilationData.classpath.map(file => PlainVirtualFileConverter.converter.toVirtualFile(file.toPath)).toArray,
        PlainVirtualFileConverter.converter,
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
    client.generated(PlainVirtualFileConverter.converter.toPath(source).toFile, classFile.toFile, binaryClassName)

  override def generatedLocalClass(source: VirtualFileRef, classFile: Path): Unit =
    if (classFile.startsWith(output)) {
      val relative = output.relativize(classFile)
      val binaryClassName = relative.iterator().asScala.mkString(".").dropRight(".class".length)
      client.generated(PlainVirtualFileConverter.converter.toPath(source).toFile, classFile.toFile, binaryClassName)
    }

  override def enabled(): Boolean = false
}

abstract class ClientCallbackBase extends xsbti.AnalysisCallback2 {

  override def isPickleJava: Boolean = false

  override def getPickleJarPair: Optional[T2[Path, Path]] = Optional.empty()

  override def api(sourceFile: VirtualFileRef, classApi: ClassLike): Unit = {}

  //noinspection ScalaDeprecation
  final override def api(sourceFile: File, classApi: ClassLike): Unit =
    api(PlainVirtualFileConverter.converter.toVirtualFile(sourceFile.toPath), classApi)

  override def binaryDependency(onBinaryEntry: Path,
                                onBinaryClassName: String,
                                fromClassName: String,
                                fromSourceFile: VirtualFileRef,
                                context: DependencyContext): Unit = {}

  //noinspection ScalaDeprecation
  final override def binaryDependency(onBinary: File,
                                      onBinaryClassName: String,
                                      fromClassName: String,
                                      fromSourceFile: File,
                                      context: DependencyContext): Unit =
    binaryDependency(
      onBinary.toPath,
      onBinaryClassName,
      fromClassName,
      PlainVirtualFileConverter.converter.toVirtualFile(fromSourceFile.toPath),
      context
    )

  override def classDependency(onClassName: String, sourceClassName: String, context: DependencyContext): Unit = {}

  override def classesInOutputJar(): util.Set[String] =
    Set.empty[String].asJava

  //noinspection ScalaDeprecation
  override def generatedLocalClass(source: File, classFile: File): Unit =
    generatedLocalClass(PlainVirtualFileConverter.converter.toVirtualFile(source.toPath), classFile.toPath)

  //noinspection ScalaDeprecation
  final override def generatedNonLocalClass(source: File,
                                            classFile: File,
                                            binaryClassName: String,
                                            srcClassName: String): Unit =
    generatedNonLocalClass(
      PlainVirtualFileConverter.converter.toVirtualFile(source.toPath),
      classFile.toPath,
      binaryClassName,
      srcClassName
    )

  override def problem(what: String, position: Position, x$3: String, msg: Severity, reported: Boolean): Unit = {}

  override def problem2(
    what: String,
    pos: Position,
    msg: String,
    severity: Severity,
    reported: Boolean,
    rendered: Optional[String],
    diagnosticCode: Optional[DiagnosticCode],
    diagnosticRelatedInformation: util.List[DiagnosticRelatedInformation],
    actions: util.List[Action]
  ): Unit = {}

  override def startSource(source: VirtualFile): Unit = {}

  //noinspection ScalaDeprecation
  final override def startSource(source: File): Unit =
    startSource(PlainVirtualFileConverter.converter.toVirtualFile(source.toPath))

  override def usedName(className: String, name: String, useScopes: util.EnumSet[xsbti.UseScope]): Unit = {}

  override def apiPhaseCompleted(): Unit = {}

  override def dependencyPhaseCompleted(): Unit = {}

  override def enabled(): Boolean = false

  override def mainClass(sourceFile: VirtualFileRef, className: String): Unit = {}

  //noinspection ScalaDeprecation
  final override def mainClass(sourceFile: File, className: String): Unit =
    mainClass(PlainVirtualFileConverter.converter.toVirtualFile(sourceFile.toPath), className)
}

private object emptyChanges extends DependencyChanges {
  override val modifiedBinaries: Array[File] = Array.empty
  override val modifiedClasses: Array[String] = Array.empty
  override val modifiedLibraries: Array[VirtualFileRef] = Array.empty

  override def isEmpty = true
}

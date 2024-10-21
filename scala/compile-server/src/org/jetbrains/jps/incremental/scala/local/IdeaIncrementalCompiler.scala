package org.jetbrains.jps.incremental.scala.local

import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.compiler.data.{CompilationData, DocumentCompilationData}
import sbt.internal.inc.{AnalyzingCompiler, CompileOutput, StringVirtualFile}
import xsbti._
import xsbti.api.{ClassLike, DependencyContext}
import xsbti.compile.DependencyChanges
import xsbti.compile.analysis.{ReadSourceInfos, SourceInfo}

import java.io.File
import java.nio.file.Path
import java.util.Optional
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class IdeaIncrementalCompiler(scalac: AnalyzingCompiler)
  extends AbstractCompiler {

  override def compile(compilationData: CompilationData, client: Client): Unit = {
    val progress = getProgress(client, compilationData.sources.size)
    val reporter = getReporter(client)
    val logger = getLogger(client)
    val converter = sbt.internal.inc.PlainVirtualFileConverter.converter
    val clientCallback = new ClientCallback(client, compilationData.output.toPath, converter)

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
        compilationData.sources.toArray.map(file => converter.toVirtualFile(file.toPath)),
        compilationData.classpath.map(file => converter.toVirtualFile(file.toPath)).toArray,
        converter,
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

  def compileDocument(compilationData: DocumentCompilationData, client: Client): Unit = {
    val DocumentCompilationData(sourcePath, sourceContent, output, classpath, scalacOptions) = compilationData

    val virtualFile = StringVirtualFile(sourcePath.toString, sourceContent)

    val converter = new FileConverter {
      private val plain = sbt.internal.inc.PlainVirtualFileConverter.converter

      override def toPath(ref: VirtualFileRef): Path = ref match {
        case `virtualFile` => sourcePath
        case vfr => plain.toPath(vfr)
      }

      override def toVirtualFile(path: Path): VirtualFile = path match {
        case `sourcePath` => virtualFile
        case p => plain.toVirtualFile(p)
      }
    }

    val progress = getProgress(client, 1)
    val reporter = getReporter(client)
    val logger = getLogger(client)
    val clientCallback = new ClientCallback(client, output, converter)

    val out = CompileOutput(output)

    try {
      scalac.compile(
        Array(virtualFile),
        classpath.toArray.map(converter.toVirtualFile),
        converter,
        emptyChanges,
        scalacOptions.toArray,
        out,
        clientCallback,
        reporter,
        Optional.of(progress),
        logger
      )
    } catch {
      case _: xsbti.CompileFailed =>
      case NonFatal(t) =>
        client.trace(t)
    }
  }

}

private class ClientCallback(client: Client, output: Path, converter: FileConverter) extends ClientCallbackBase(converter) {

  override def generatedNonLocalClass(source: VirtualFileRef,
                                      classFile: Path,
                                      binaryClassName: String,
                                      srcClassName: String): Unit =
    client.generated(converter.toPath(source).toFile, classFile.toFile, binaryClassName)

  override def generatedLocalClass(source: VirtualFileRef, classFile: Path): Unit =
    if (classFile.startsWith(output)) {
      val relative = output.relativize(classFile)
      val binaryClassName = relative.iterator().asScala.mkString(".").dropRight(".class".length)
      client.generated(converter.toPath(source).toFile, classFile.toFile, binaryClassName)
    }

  override def enabled(): Boolean = false
}

abstract class ClientCallbackBase(converter: FileConverter) extends xsbti.AnalysisCallback3 {

  override def isPickleJava: Boolean = false

  override def getPickleJarPair: Optional[T2[Path, Path]] = Optional.empty()

  override def api(sourceFile: VirtualFileRef, classApi: ClassLike): Unit = {}

  //noinspection ScalaDeprecation
  final override def api(sourceFile: File, classApi: ClassLike): Unit =
    api(converter.toVirtualFile(sourceFile.toPath), classApi)

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
      converter.toVirtualFile(fromSourceFile.toPath),
      context
    )

  override def classDependency(onClassName: String, sourceClassName: String, context: DependencyContext): Unit = {}

  override def classesInOutputJar(): java.util.Set[String] =
    Set.empty[String].asJava

  //noinspection ScalaDeprecation
  override def generatedLocalClass(source: File, classFile: File): Unit =
    generatedLocalClass(converter.toVirtualFile(source.toPath), classFile.toPath)

  //noinspection ScalaDeprecation
  final override def generatedNonLocalClass(source: File,
                                            classFile: File,
                                            binaryClassName: String,
                                            srcClassName: String): Unit =
    generatedNonLocalClass(
      converter.toVirtualFile(source.toPath),
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
    diagnosticRelatedInformation: java.util.List[DiagnosticRelatedInformation],
    actions: java.util.List[Action]
  ): Unit = {}

  override def startSource(source: VirtualFile): Unit = {}

  //noinspection ScalaDeprecation
  final override def startSource(source: File): Unit =
    startSource(converter.toVirtualFile(source.toPath))

  override def usedName(className: String, name: String, useScopes: java.util.EnumSet[xsbti.UseScope]): Unit = {}

  override def apiPhaseCompleted(): Unit = {}

  override def dependencyPhaseCompleted(): Unit = {}

  override def enabled(): Boolean = false

  override def mainClass(sourceFile: VirtualFileRef, className: String): Unit = {}

  //noinspection ScalaDeprecation
  final override def mainClass(sourceFile: File, className: String): Unit =
    mainClass(converter.toVirtualFile(sourceFile.toPath), className)

  override def toVirtualFile(path: Path): VirtualFile = converter.toVirtualFile(path)

  override def getSourceInfos: ReadSourceInfos = new ReadSourceInfos {
    private val emptySourceInfo: SourceInfo = new SourceInfo {
      override def getReportedProblems: Array[Problem] = Array.empty
      override def getUnreportedProblems: Array[Problem] = Array.empty
      override def getMainClasses: Array[String] = Array.empty
    }

    override def get(sourceFile: VirtualFileRef): SourceInfo = emptySourceInfo

    override def getAllSourceInfos: java.util.Map[VirtualFileRef, SourceInfo] = java.util.Collections.emptyMap()
  }
}

private object emptyChanges extends DependencyChanges {
  override val modifiedBinaries: Array[File] = Array.empty
  override val modifiedClasses: Array[String] = Array.empty
  override val modifiedLibraries: Array[VirtualFileRef] = Array.empty

  override def isEmpty = true
}

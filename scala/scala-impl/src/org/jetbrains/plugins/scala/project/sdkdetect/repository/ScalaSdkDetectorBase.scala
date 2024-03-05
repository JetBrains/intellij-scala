package org.jetbrains.plugins.scala.project.sdkdetect.repository

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{EmptyProgressIndicator, ProgressIndicator}
import org.jetbrains.plugins.scala.project.sdkdetect.repository.ScalaSdkDetectorBase._
import org.jetbrains.plugins.scala.project.template.Artifact.{Scala3Compiler, ScalaCompiler, ScalaLibraryAndModulesArtifacts}
import org.jetbrains.plugins.scala.project.template.Kind.{Binaries, Docs, Sources}
import org.jetbrains.plugins.scala.project.template.{Artifact, ScalaSdkComponent, ScalaSdkDescriptor, SdkChoice}

import java.io.File
import java.nio.file.{Files, Path}
import java.util.stream.{Stream => JStream}
import scala.jdk.CollectionConverters.IteratorHasAsScala

abstract class ScalaSdkDetectorBase extends ScalaSdkDetector
  with ScalaSdkDetectorCompilerClasspathResolveOps {

  final def collectSdkChoices(implicit indicator: ProgressIndicator): Seq[SdkChoice] =
    collectSdkDescriptors.map(buildSdkChoice)

  protected def collectSdkDescriptors(implicit indicator: ProgressIndicator): Seq[ScalaSdkDescriptor]

  protected def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice

  /** NOTE: it closes the stream */
  protected def componentsFromJarStream(jarStream: JStream[Path])
                                       (implicit indicator: ProgressIndicator): Seq[ScalaSdkComponent] = {
    try {
      jarStream
        .iterator().asScala
        .map { f =>
          indicator.checkCanceled()
          //noinspection ReferencePassedToNls
          indicator.setText2(f.toString)
          val sdkComponent = ScalaSdkComponent.fromFile(f.toFile)
          sdkComponent.orNull
        }
        .filter(_ != null)
        .toSeq
    } finally {
      // stream returned by `java.nio.file.Files.list` or `java.nio.file.Files.walk` must be closed!
      jarStream.close()
    }
  }

  private[project] def buildFromComponents(
    components: Seq[ScalaSdkComponent],
    label: Option[String],
    indicator: ProgressIndicator = new EmptyProgressIndicator
  ): Either[Seq[CompilerClasspathResolveFailure], ScalaSdkDescriptor] = {
    val descriptorShort = buildFromComponentsShort(components, label)
    val descriptorFull = descriptorShort.flatMap(resolveExtraRequiredJars(_)(indicator))
    descriptorFull
  }

  protected def buildFromComponentsShort(
    components: Seq[ScalaSdkComponent],
    label: Option[String]
  ): Either[Seq[CompilerClasspathResolveFailure.UnresolvedArtifact], ScalaSdkDescriptor] = {
    val componentsByKind = components.groupBy(_.kind).withDefault(Function.const(Seq.empty))

    val binaryComponents  = componentsByKind(Binaries)

    val scala3VersionOpt = components.flatMap(_.version).find(_.startsWith("3"))

    val requiredBinaryArtifacts: Set[Artifact] =
      if (scala3VersionOpt.isDefined) requiredScala3BinaryArtifacts
      else requiredScala2BinaryArtifacts

    val missingBinaryArtifacts = requiredBinaryArtifacts -- binaryComponents.map(_.artifact)

    if (missingBinaryArtifacts.nonEmpty)
      Left(missingBinaryArtifacts.toSeq.map(_.prefix).sorted.map(CompilerClasspathResolveFailure.UnresolvedArtifact))
    else {
      val compilerVersion = binaryComponents.collectFirst {
        case ScalaSdkComponent(Scala3Compiler | ScalaCompiler, _, Some(version), _) => version
      }

      val sourcesComponents = componentsByKind(Sources)
      val docsComponents    = componentsByKind(Docs)

      val descriptor = ScalaSdkDescriptor(
        compilerVersion,
        label,
        compilerClasspath      = files(binaryComponents)(requiredBinaryArtifacts.contains),
        scaladocExtraClasspath = Nil, // TODO SCL-17219
        libraryFiles           = files(binaryComponents)(ScalaLibraryAndModulesArtifacts.contains),
        sourceFiles            = files(sourcesComponents)(ScalaLibraryAndModulesArtifacts.contains),
        docFiles               = files(docsComponents)(ScalaLibraryAndModulesArtifacts.contains),
        compilerBridgeJar      = None
      )

      Right(descriptor)
    }
  }

  protected def collectJarFiles(path: Path)(implicit indicator: ProgressIndicator): JStream[Path] =
    Files.walk(path).filter { indicator.checkCanceled(); _.toString.endsWith(".jar") }

  protected def progress(text2: String)(implicit indicator: ProgressIndicator): Unit = {
    indicator.checkCanceled()
    //noinspection ReferencePassedToNls
    indicator.setText2(text2)
  }

  protected final def logScalaSdkSkipped(version: Option[String], errors: Seq[String]): Unit = {
    Log.trace(
      s"Scala SDK Descriptor candidate is skipped" +
        s" (detector: ${this.getClass.getSimpleName}, scalaVersion: $version)," +
        s" errors: ${errors.zipWithIndex.map(_.swap).mkString(", ")}"
    )
  }

  protected final def logScalaSdkSkipped_UndefinedVersion(descriptor: ScalaSdkDescriptor): Unit = {
    Log.trace(
      s"Scala SDK Descriptor candidate is skipped" +
        s" (detector: ${this.getClass.getSimpleName}, compilerClasspath: ${descriptor.compilerClasspath})" +
        s" (undefined scala version)"
    )
  }
}

object ScalaSdkDetectorBase {

  private val Log = Logger.getInstance(this.getClass)

  import Artifact._

  private def requiredScala2BinaryArtifacts: Set[Artifact] =
    Set[Artifact](
      ScalaLibrary,
      ScalaCompiler,
      ScalaReflect
    )

  private def requiredScala3BinaryArtifacts: Set[Artifact] =
    Set[Artifact](
      Scala3Library,
      Scala3Compiler,
      Scala3Interfaces,
      TastyCore,
    )

  private def files(components: Seq[ScalaSdkComponent])
                         (predicate: Artifact => Boolean): Seq[File] =
    for {
      ScalaSdkComponent(artifact, _, _, file) <- components
      if predicate(artifact)
    } yield file
}








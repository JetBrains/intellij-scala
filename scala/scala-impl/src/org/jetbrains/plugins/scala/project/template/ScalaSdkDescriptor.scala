package org.jetbrains.plugins.scala.project.template

import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}

import java.io.File

/**
 * @author Pavel Fatin
 */
final case class ScalaSdkDescriptor(version: Option[String],
                                    compilerClasspath: Seq[File],
                                    libraryFiles: Seq[File],
                                    sourceFiles: Seq[File],
                                    docFiles: Seq[File])
  extends Ordered[ScalaSdkDescriptor] {

  def isScala3: Boolean = version.exists(_.startsWith("3"))

  def withExtraCompilerClasspath(files: Seq[File]): ScalaSdkDescriptor = copy(compilerClasspath = compilerClasspath ++ files)
  def withExtraLibraryFiles(files: Seq[File]): ScalaSdkDescriptor = copy(libraryFiles = libraryFiles ++ files)
  def withExtraSourcesFiles(files: Seq[File]): ScalaSdkDescriptor = copy(sourceFiles = sourceFiles ++ files)

  private val comparableVersion = version.map(Version(_))

  override def compare(that: ScalaSdkDescriptor): Int = that.comparableVersion.compare(comparableVersion)
}

object ScalaSdkDescriptor {

  import Artifact._
  import Kind._

  private[project]
  def buildFromComponents(components: Seq[ScalaSdkComponent]): Either[NlsString, ScalaSdkDescriptor] = {
    assert(components.nonEmpty)

    val componentsByKind = components.groupBy(_.kind).withDefault(Function.const(Seq.empty))

    val binaryComponents  = componentsByKind(Binaries)

    val scala3VersionOpt = components.flatMap(_.version).find(_.startsWith("3"))

    val requiredBinaryArtifacts: Set[Artifact] =
      if (scala3VersionOpt.isDefined) requiredScala3BinaryArtifacts
      else requiredScala2BinaryArtifacts

    val missingBinaryArtifacts = requiredBinaryArtifacts -- binaryComponents.map(_.artifact)

    if (missingBinaryArtifacts.nonEmpty)
      Left(ScalaBundle.nls("not.found.missing.artifacts", missingBinaryArtifacts.map(_.prefix + "*.jar").mkString(", ")))
    else {
      val compilerVersion = binaryComponents.collectFirst {
        case ScalaSdkComponent(Scala3Compiler | ScalaCompiler, _, Some(version), _) => version
      }

      val sourcesComponents = componentsByKind(Sources)
      val docsComponents    = componentsByKind(Docs)

      val descriptor = ScalaSdkDescriptor(
        compilerVersion,
        compilerClasspath = files(binaryComponents)(requiredBinaryArtifacts.contains),
        libraryFiles      = files(binaryComponents)(ScalaLibraryAndModulesArtifacts.contains),
        sourceFiles       = files(sourcesComponents)(ScalaLibraryAndModulesArtifacts.contains),
        docFiles          = files(docsComponents)(ScalaLibraryAndModulesArtifacts.contains)
      )

      Right(descriptor)
    }
  }

  private[this] def requiredScala2BinaryArtifacts: Set[Artifact] =
    Set[Artifact](
      ScalaLibrary,
      ScalaCompiler,
      ScalaReflect
    )

  private[this] def requiredScala3BinaryArtifacts: Set[Artifact] =
    Set[Artifact](
      Scala3Library,
      Scala3Compiler,
      Scala3Interfaces,
      TastyCore,
    )

  private[this] def files(components: Seq[ScalaSdkComponent])
                         (predicate: Artifact => Boolean): Seq[File] =
    for {
      ScalaSdkComponent(artifact, _, _, file) <- components
      if predicate(artifact)
    } yield file
}
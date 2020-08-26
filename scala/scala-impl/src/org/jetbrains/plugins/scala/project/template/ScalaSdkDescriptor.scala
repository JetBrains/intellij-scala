package org.jetbrains.plugins.scala
package project
package template

import java.io.File

import org.jetbrains.plugins.scala.ScalaBundle

/**
 * @author Pavel Fatin
 */
final case class ScalaSdkDescriptor(version: Option[String],
                                    compilerClasspath: collection.Seq[File],
                                    libraryFiles: collection.Seq[File],
                                    sourceFiles: collection.Seq[File],
                                    docFiles: collection.Seq[File])
  extends Ordered[ScalaSdkDescriptor] {

  private val comparableVersion = version.map(Version(_))

  override def compare(that: ScalaSdkDescriptor): Int = that.comparableVersion.compare(comparableVersion)
}

object ScalaSdkDescriptor {

  import Artifact._
  import Kind._

  def buildFromComponents(components: collection.Seq[ScalaSdkComponent]): Either[NlsString, ScalaSdkDescriptor] = {
    val componentsByKind = components.groupBy(_.kind)
      .withDefault(Function.const(Seq.empty))

    def filesByKind(kind: Kind) =
      files(componentsByKind(kind))()

    val binaryComponents = componentsByKind(Binaries)

    requiredBinaryArtifacts -- binaryComponents.map(_.artifact) match {
      case missingBinaryArtifacts if missingBinaryArtifacts.nonEmpty =>
        Left(ScalaBundle.nls("not.found.missing.artifacts", missingBinaryArtifacts.map(_.prefix + "*.jar").mkString(", ")))
      case _ =>
        val libraryVersion = binaryComponents.collectFirst {
          case ScalaSdkComponent(ScalaLibrary, _, Some(version), _) => version
        }

        val descriptor = ScalaSdkDescriptor(
          libraryVersion,
          files(binaryComponents)(requiredBinaryArtifacts),
          files(binaryComponents)(),
          filesByKind(Sources),
          filesByKind(Docs)
        )

        Right(descriptor)
    }
  }

  private[this] def requiredBinaryArtifacts = Set[Artifact](
    ScalaLibrary,
    ScalaCompiler,
    ScalaReflect
  )

  private[this] def files(components: collection.Seq[ScalaSdkComponent])
                         (predicate: Artifact => Boolean = ScalaArtifacts - ScalaCompiler): collection.Seq[File] =
    for {
      ScalaSdkComponent(artifact, _, _, file) <- components
      if predicate(artifact)
    } yield file
}
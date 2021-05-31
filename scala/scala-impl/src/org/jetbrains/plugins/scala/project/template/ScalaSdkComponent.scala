package org.jetbrains.plugins.scala.project.template

import java.io.File
import java.util.function.Predicate

case class ScalaSdkComponent(artifact: Artifact,
                             kind: Kind,
                             version: Option[String],
                             file: File)

object ScalaSdkComponent {

  case class ArtifactPattern(artifact: Artifact, kind: Kind, predicate: Predicate[String])

  private def buildAllKindsPatterns(artifacts: Set[Artifact]): Set[ArtifactPattern] =
    for {
      artifact <- artifacts
      kind <- Kind.values
    } yield ArtifactPattern(artifact, kind, kind.getPattern(artifact))

  private val ScalaArtifactsAllKindsPatterns: Set[ArtifactPattern] =
    buildAllKindsPatterns(Artifact.ScalaArtifacts)

  def fromFile(file: File, patterns: Set[ArtifactPattern] = ScalaArtifactsAllKindsPatterns): Option[ScalaSdkComponent] = {
    val pattern = patterns.find(_.predicate.test(file.getName))
    pattern.map {
      case ArtifactPattern(artifact, kind, _) =>
        val version = artifact.versionOf(file)
        ScalaSdkComponent(artifact, kind, version, file)
    }
  }

  def fromFiles(files: Seq[File], patterns: Set[ArtifactPattern] = ScalaArtifactsAllKindsPatterns): Seq[ScalaSdkComponent] =
    for {
      file <- files

      fileName = file.getName
      if file.isFile && fileName.endsWith(".jar")

      ArtifactPattern(artifact, kind, pattern) <- patterns
      if pattern.test(fileName)
    } yield {
      val version = artifact.versionOf(file)
      ScalaSdkComponent(artifact, kind, version, file)
    }
}

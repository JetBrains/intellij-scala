package org.jetbrains.plugins.scala.project.template

import java.nio.file.{Files, Path}
import java.util.function.Predicate

case class ScalaSdkComponent(artifact: Artifact,
                             kind: Kind,
                             version: Option[String],
                             file: Path)

object ScalaSdkComponent {

  case class ArtifactPattern(artifact: Artifact, kind: Kind, predicate: Predicate[String])

  private def buildAllKindsPatterns(artifacts: Set[Artifact]): Set[ArtifactPattern] =
    for {
      artifact <- artifacts
      kind <- Kind.values
    } yield ArtifactPattern(artifact, kind, kind.getPattern(artifact))

  private val ScalaArtifactsAllKindsPatterns: Set[ArtifactPattern] =
    buildAllKindsPatterns(Artifact.ScalaArtifacts)

  def fromFile(file: Path, patterns: Set[ArtifactPattern] = ScalaArtifactsAllKindsPatterns): Option[ScalaSdkComponent] = {
    val pattern = patterns.find(_.predicate.test(file.getFileName.toString))
    pattern.map {
      case ArtifactPattern(artifact, kind, _) =>
        val version = artifact.versionOf(file)
        ScalaSdkComponent(artifact, kind, version, file)
    }
  }

  def fromFiles(files: Seq[Path], patterns: Set[ArtifactPattern] = ScalaArtifactsAllKindsPatterns): Seq[ScalaSdkComponent] =
    for {
      file <- files

      fileName = file.getFileName.toString
      if Files.isRegularFile(file) && fileName.endsWith(".jar")

      ArtifactPattern(artifact, kind, pattern) <- patterns
      if pattern.test(fileName)
    } yield {
      val version = artifact.versionOf(file)
      ScalaSdkComponent(artifact, kind, version, file)
    }
}

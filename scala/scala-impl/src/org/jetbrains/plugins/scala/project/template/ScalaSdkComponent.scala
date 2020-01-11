package org.jetbrains.plugins.scala.project.template

import java.io.File
import java.util.function.Predicate

case class ScalaSdkComponent(artifact: Artifact,
                             kind: Kind,
                             version: Option[String],
                             file: File)

object ScalaSdkComponent {

  type ArtifactPattern = (Artifact, Kind, Predicate[String])

  private val defaultArtifactPatterns: Set[ArtifactPattern] = for {
    artifact <- Artifact.ScalaArtifacts
    kind <- Kind.values
  } yield (artifact, kind, kind.getPattern(artifact))

  def fromFile(file: File, patterns: Set[ArtifactPattern] = defaultArtifactPatterns): Option[ScalaSdkComponent] = {
    patterns.collectFirst {
      case (artifact, kind, pattern) if pattern.test(file.getName) =>
        ScalaSdkComponent(artifact, kind, artifact.versionOf(file), file)
    }
  }

  def discoverIn(files: Seq[File],
                 artifacts: Set[Artifact] = Artifact.ScalaArtifacts): Seq[ScalaSdkComponent] = {
    val patterns = for {
      artifact <- artifacts
      kind <- Kind.values
    } yield (
      artifact,
      kind,
      kind.getPattern(artifact)
    )

    for {
      file <- files

      fileName = file.getName
      if file.isFile && fileName.endsWith(".jar")

      (artifact, kind, pattern) <- patterns
      if pattern.test(fileName)

    } yield ScalaSdkComponent(
      artifact,
      kind,
      artifact.versionOf(file),
      file
    )
  }
}

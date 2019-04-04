package org.jetbrains.plugins.scala
package project
package template

import java.io.File

case class Component(artifact: Artifact,
                     kind: Kind,
                     version: Option[String],
                     file: File)

object Component {

  def discoverIn(files: Seq[File],
                 artifacts: Set[Artifact] = Artifact.ScalaArtifacts): Seq[Component] = {
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

    } yield Component(
      artifact,
      kind,
      artifact.versionOf(file),
      file
    )
  }
}

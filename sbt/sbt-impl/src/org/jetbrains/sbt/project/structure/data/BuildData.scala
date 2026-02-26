package org.jetbrains.sbt.project.structure.data

import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.sbt.project.structure.data.DataSerializers.*
import org.jetbrains.sbt.project.structure.data.Helpers.uri

import java.net.URI

sealed abstract class BuildData extends Product {
  val uri: URI
  val imports: Seq[String]
  val classes: Seq[InterpretablePath]
  val docs: Seq[InterpretablePath]
  val sources: Seq[InterpretablePath]
}

// hack a case class with private constructor to ensure some invariants in constructions
object BuildData:
  private case class BuildDataImpl (uri: URI, imports: Seq[String], classes: Seq[InterpretablePath], docs: Seq[InterpretablePath], sources: Seq[InterpretablePath]) extends BuildData
  private def sort(files: Seq[InterpretablePath]): Seq[InterpretablePath] = files.sorted

  def apply(uri: URI, imports: Seq[String], classes: Seq[InterpretablePath], docs: Seq[InterpretablePath], sources: Seq[InterpretablePath]): BuildData =
    BuildDataImpl(
      uri.normalize(),
      imports.sorted,
      sort(classes),
      sort(docs),
      sort(sources)
    )

  given (PathConstructor[String], EelDescriptor) => XmlDeserializer[BuildData] = what =>
    val uri = (what \ "uri").map(_.text.uri).head
    val imports = (what \ ImportElementName).map(_.text)
    val classes = (what \ ClassesElementName).map(e => InterpretablePath.construct(e.text))
    val docs = (what \ DocsElementName).map(e => InterpretablePath.construct(e.text))
    val sources = (what \ SourcesElementName).map(e => InterpretablePath.construct(e.text))
    Right(BuildData(uri, imports, classes, docs, sources))

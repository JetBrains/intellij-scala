package org.jetbrains.sbt.project.structure.data

case class ModuleIdentifier(organization: String,
                            name: String,
                            revision: String,
                            artifactType: String,
                            classifier: String):
  def key: Iterable[String] = productIterator.to(Iterable).asInstanceOf[Iterable[String]]

object ModuleIdentifier:
  given XmlDeserializer[ModuleIdentifier] = what =>
    val organization = (what \ "@organization").text
    val name = (what \ "@name").text
    val revision = (what \ "@revision").text
    val artifactType = (what \ "@artifactType").text
    val classifier = (what \ "@classifier").text
    Right(ModuleIdentifier(organization, name, revision, artifactType, classifier))

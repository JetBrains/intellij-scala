package org.jetbrains.sbt.project.structure.data

import org.jetbrains.sbt.project.structure.data.Helpers.!

import scala.xml.Node

case class ConfigurationData(id: String,
                             sources: Seq[DirectoryData],
                             resources: Seq[DirectoryData],
                             excludes: Seq[InterpretablePath],
                             classes: InterpretablePath)

object ConfigurationData:
  given PathConstructor[String] => XmlDeserializer[ConfigurationData] = new XmlDeserializer[ConfigurationData]:
    override def deserialize(what: Node): Either[Throwable, ConfigurationData] =
      val id = (what \ "@id").text
      val sources = (what \ "sources").map(parseDirectory)
      val resources = (what \ "resources").map(parseDirectory)
      val excludes = (what \ "exclude").map(e => InterpretablePath.construct(e.text))
      val classes = InterpretablePath.construct((what ! "classes").text)
      Right(ConfigurationData(id, sources, resources, excludes, classes))

    private def parseDirectory(node: Node): DirectoryData =
      val managed = (node \ "@managed").headOption.exists(_.text.toBoolean)
      DirectoryData(InterpretablePath.construct(node.text), managed)

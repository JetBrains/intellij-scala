package org.jetbrains.sbt.project.structure.data

import org.jetbrains.sbt.project.structure.data.XmlDeserializer.deserialize

import scala.xml.Node

case class ModuleData(id: ModuleIdentifier,
                      binaries: Set[InterpretablePath],
                      docs: Set[InterpretablePath],
                      sources: Set[InterpretablePath])

object ModuleData:
  given PathConstructor[String] => XmlDeserializer[ModuleData] = new XmlDeserializer[ModuleData]:
    override def deserialize(what: Node): Either[Throwable, ModuleData] =
      what.deserialize[ModuleIdentifier].map(deserializeModuleData(what, _))

    private def deserializeModuleData(what: Node, id: ModuleIdentifier): ModuleData =
      val binaries = (what \ "jar").map(n => InterpretablePath.construct(n.text)).toSet
      val docs = (what \ "doc").map(n => InterpretablePath.construct(n.text)).toSet
      val sources = (what \ "src").map(n => InterpretablePath.construct(n.text)).toSet
      ModuleData(id, binaries, docs, sources)


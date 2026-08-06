package org.jetbrains.sbt.project.structure.data

import org.jetbrains.sbt.project.structure.data.XmlDeserializer.deserialize

import scala.xml.Node

case class ModuleDependencyData(id: ModuleIdentifier, configurations: Seq[Configuration])

object ModuleDependencyData:
  given XmlDeserializer[ModuleDependencyData] = new XmlDeserializer[ModuleDependencyData]:
    override def deserialize(what: Node): Either[Throwable, ModuleDependencyData] =
      what.deserialize[ModuleIdentifier].map(deserializeModuleDependencyData(what, _))

    private def deserializeModuleDependencyData(what: Node, id: ModuleIdentifier): ModuleDependencyData =
      val configurations = (what \ "@configurations").headOption.map(n => Configuration.fromString(n.text))
      ModuleDependencyData(id, configurations.getOrElse(Seq.empty))

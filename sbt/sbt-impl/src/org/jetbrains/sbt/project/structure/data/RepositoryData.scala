package org.jetbrains.sbt.project.structure.data

import org.jetbrains.sbt.project.structure.data.XmlDeserializer.deserializeNodeSeq

case class RepositoryData(modules: Seq[ModuleData])

object RepositoryData:
  given PathConstructor[String] => XmlDeserializer[RepositoryData] = what =>
    val modules = (what \ "module").deserializeNodeSeq[ModuleData]
    Right(RepositoryData(modules))

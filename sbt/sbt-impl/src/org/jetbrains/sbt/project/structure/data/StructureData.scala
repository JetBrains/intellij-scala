package org.jetbrains.sbt.project.structure.data

import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.sbt.project.structure.data.XmlDeserializer.deserializeNodeSeq

import scala.xml.Node

case class StructureData(sbtVersion: String,
                         builds: Seq[BuildData],
                         projects: Seq[ProjectData],
                         repository: Option[RepositoryData],
                         localCachePath: Option[InterpretablePath])

object StructureData:
  given EelDescriptor => XmlDeserializer[StructureData] = new XmlDeserializer[StructureData]:
    private given PathConstructor[String]:
      override def construct(str: String): InterpretablePath = new InterpretablePath(str)

    override def deserialize(what: Node): Either[Throwable, StructureData] =
      val sbtVersion = (what \ "@sbt").text
      val builds = (what \ "build").deserializeNodeSeq[BuildData]
      val projects = (what \ "project").deserializeNodeSeq[ProjectData]
      val repository = (what \ "repository").deserializeNodeSeq[RepositoryData].headOption
      val localCachePath = (what \ "localCachePath").headOption.map(_.text).map(InterpretablePath.construct)
      Either.cond(
        sbtVersion.nonEmpty,
        StructureData(sbtVersion, builds, projects, repository, localCachePath),
        new Error("<structure> property 'sbt' is empty")
      )

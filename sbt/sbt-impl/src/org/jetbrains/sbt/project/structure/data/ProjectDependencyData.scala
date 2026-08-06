package org.jetbrains.sbt.project.structure.data

import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.sbt.project.structure.data.Helpers.uri

import java.net.URI

case class ProjectDependencyData(project: String, buildURI: Option[URI], configurations: Seq[Configuration])

object ProjectDependencyData:
  given EelDescriptor => XmlDeserializer[ProjectDependencyData] = what =>
    val project = what.text
    val buildURI = (what \ "@buildURI").text.uri
    val configurations = (what \ "@configurations").headOption.map(n => Configuration.fromString(n.text))
    Right(ProjectDependencyData(project, Some(buildURI), configurations.getOrElse(Seq.empty)))

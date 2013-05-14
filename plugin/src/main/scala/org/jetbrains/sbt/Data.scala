package org.jetbrains.sbt

import java.io.File
import xml.Elem

/**
 * @author Pavel Fatin
 */
case class StructureData(project: ProjectData, repository: RepositoryData) {
  def toXML: Elem = {
    <structure>
      {project.toXML}
      {repository.toXML}
    </structure>
  }
}

case class ProjectData(name: String, base: File, configurations: Seq[ConfigurationData], scala: Option[ScalaData], projects: Seq[ProjectData]) {
  def toXML: Elem = {
    <project>
      <name>{name}</name>
      <base>{base}</base>
      {scala.map(_.toXML).getOrElse("")}
      {configurations.map(_.toXML)}
      {projects.map(_.toXML)}
    </project>
  }
}

case class ConfigurationData(id: String, sources: Seq[File], resources: Seq[File], classes: File, modules: Seq[ModuleIdentifier], jars: Seq[File]) {
  def toXML: Elem = {
    <configuration id={id}>
      {sources.map { directory =>
        <sources>{directory}</sources>
      }}
      {resources.map { directory =>
        <resources>{directory}</resources>
      }}
      <classes>{classes}</classes>
      {modules.map { module =>
        <module organization={module.organization} name={module.name} revision={module.revision}/>
       }}
      {jars.map { jar =>
        <jar>{jar}</jar>
      }}
    </configuration>
  }
}

case class ScalaData(version: String, libraryJar: File, compilerJar: File, extraJars: Seq[File]) {
  def toXML: Elem = {
    <scala>
      <version>{version}</version>
      <library>{libraryJar}</library>
      <compiler>{compilerJar}</compiler>
      {extraJars.map { jar =>
        <extra>{jar}</extra>
      }}
    </scala>
  }
}

case class ModuleIdentifier(organization: String, name: String, revision: String) {
  def toXML: Elem = {
    <module organization={organization} name={name} revision={revision}/>
  }
}

case class ModuleData(id: ModuleIdentifier, binaries: Seq[File], docs: Seq[File], sources: Seq[File]) {
  def toXML: Elem = {
    val artifacts =
      binaries.map(it => <jar>{it}</jar>) ++
      docs.map(it => <doc>{it}</doc>) ++
      sources.map(it => <src>{it}</src>)

    id.toXML.copy(child = artifacts)
  }
}

case class RepositoryData(base: File, modules: Seq[ModuleData]) {
  def toXML: Elem = {
    <repository>
      {modules.sortBy(it => (it.id.organization, it.id.name)).map(_.toXML)}
    </repository>
  }
}

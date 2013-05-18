package org.jetbrains.sbt

import java.io.File
import xml.Elem
import FS._

/**
 * @author Pavel Fatin
 */
case class FS(home: File, base: Option[File] = None) {
  def withBase(base: File): FS = copy(base = Some(base))

  def path(file: File): String = file.getAbsolutePath
}

object FS {
  implicit def toRichFile(file: File)(implicit fs: FS) = new {
    def path: String = {
      val home = toPath(fs.home)
      val base = fs.base.map(toPath)
      val path = toPath(file).replace(home, "~")
      base.map(it => path.replace(it + "/", "")).getOrElse(path)
    }

    def absolutePath: String = toPath(file)
  }

  def toPath(file: File) = file.getAbsolutePath.replace('\\', '/')
}

case class StructureData(project: ProjectData, repository: RepositoryData) {
  def toXML(home: File): Elem = {
    val fs = new FS(home)

    <structure>
      {project.toXML(fs.withBase(project.base))}
      {repository.toXML(fs)}
    </structure>
  }
}

case class ProjectData(name: String, organization: String, version: String, base: File, configurations: Seq[ConfigurationData], scala: Option[ScalaData], projects: Seq[ProjectData]) {
  def toXML(implicit fs: FS): Elem = {
    <project>
      <name>{name}</name>
      <organization>{organization}</organization>
      <version>{version}</version>
      <base>{base.absolutePath}</base>
      {scala.map(_.toXML).getOrElse("")}
      {configurations.map(_.toXML)}
      {projects.map(it => it.toXML(fs.withBase(it.base)))}
    </project>
  }
}

case class ConfigurationData(id: String, sources: Seq[File], resources: Seq[File], classes: File, projects: Seq[String], modules: Seq[ModuleIdentifier], jars: Seq[File]) {
  def toXML(implicit fs: FS): Elem = {
    <configuration id={id}>
      {sources.map { directory =>
        <sources>{directory.path}</sources>
      }}
      {resources.map { directory =>
        <resources>{directory.path}</resources>
      }}
      <classes>{classes.path}</classes>
      {projects.map { project =>
        <dependency>{project}</dependency>
      }}
      {modules.map { module =>
        <module organization={module.organization} name={module.name} revision={module.revision}/>
       }}
      {jars.map { jar =>
        <jar>{jar.path}</jar>
      }}
    </configuration>
  }
}

case class ScalaData(version: String, libraryJar: File, compilerJar: File, extraJars: Seq[File]) {
  def toXML(implicit fs: FS): Elem = {
    <scala>
      <version>{version}</version>
      <library>{libraryJar.path}</library>
      <compiler>{compilerJar.path}</compiler>
      {extraJars.map { jar =>
        <extra>{jar.path}</extra>
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
  def toXML(implicit fs: FS): Elem = {
    val artifacts =
      binaries.map(it => <jar>{it.path}</jar>) ++
      docs.map(it => <doc>{it.path}</doc>) ++
      sources.map(it => <src>{it.path}</src>)

    id.toXML.copy(child = artifacts)
  }
}

case class RepositoryData(base: File, modules: Seq[ModuleData]) {
  def toXML(implicit fs: FS): Elem = {
    <repository>
      {modules.sortBy(it => (it.id.organization, it.id.name)).map(_.toXML)}
    </repository>
  }
}

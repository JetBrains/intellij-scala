package org.jetbrains.sbt

import java.io.File
import scala.xml.Elem
import FS._

/**
 * @author Pavel Fatin
 */
case class FS(home: File, base: Option[File] = None) {
  def withBase(base: File): FS = copy(base = Some(base))
}

object FS {
  val Home = "~/"
  val Base = ""

  private val Windows = System.getProperty("os.name").startsWith("Win")

  implicit def toRichFile(file: File)(implicit fs: FS) = new {
    def path: String = {
      val home = toPath(fs.home)
      val base = fs.base.map(toPath)

      val path = toPath(file)
      replace(base.map(it => replace(path, it + "/", Base)).getOrElse(path), home + "/", Home)
    }

    def absolutePath: String = toPath(file)
  }

  private def replace(path: String, root: String, replacement: String) = {
    val (target, prefix) = if (Windows) (path.toLowerCase, root.toLowerCase) else (path, root)
    if (target.startsWith(prefix)) replacement + path.substring(root.length) else path
  }

  def toPath(file: File) = file.getAbsolutePath.replace('\\', '/')
}

case class StructureData(scala: ScalaData, project: ProjectData, repository: Option[RepositoryData]) {
  def toXML(home: File): Elem = {
    val fs = new FS(home)

    <structure>
      {project.toXML(fs.withBase(project.base))}
      {repository.map(_.toXML(fs)).toSeq}
    </structure>
  }
}

case class ProjectData(name: String, organization: String, version: String, base: File, build: BuildData, configurations: Seq[ConfigurationData], java: Option[JavaData], scala: Option[ScalaData], projects: Seq[ProjectData]) {
  def toXML(implicit fs: FS): Elem = {
    <project>
      <name>{name}</name>
      <organization>{organization}</organization>
      <version>{version}</version>
      <base>{base.absolutePath}</base>
      {build.toXML}
      {java.map(_.toXML).toSeq}
      {scala.map(_.toXML).toSeq}
      {configurations.map(_.toXML)}
      {projects.map(it => it.toXML(fs.withBase(it.base)))}
    </project>
  }
}

case class BuildData(classpath: Seq[File], imports: Seq[String]) {
  def toXML(implicit fs: FS): Elem = {
    <build>
      {classpath.map(_.path).filter(_.startsWith(FS.Home)).map { it =>
        <classes>{it}</classes>
      }}
      {imports.map { it =>
        <import>{it}</import>
      }}
    </build>
  }
}

case class ConfigurationData(id: String, sources: Seq[File], resources: Seq[File], classes: File, dependencies: Seq[String], modules: Seq[ModuleIdentifier], jars: Seq[File]) {
  def toXML(implicit fs: FS): Elem = {
    <configuration id={id}>
      {sources.map { directory =>
        <sources>{directory.path}</sources>
      }}
      {resources.map { directory =>
        <resources>{directory.path}</resources>
      }}
      <classes>{classes.path}</classes>
      {dependencies.map { dependency =>
        <dependency>{dependency}</dependency>
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

case class JavaData(home: File, options: Seq[String]) {
  def toXML(implicit fs: FS): Elem = {
    <java>
      <home>{home.path}</home>
      {options.map { option =>
        <option>{option}</option>
      }}
    </java>
  }
}

case class ScalaData(version: String, libraryJar: File, compilerJar: File, extraJars: Seq[File], options: Seq[String]) {
  def toXML(implicit fs: FS): Elem = {
    <scala>
      <version>{version}</version>
      <library>{libraryJar.path}</library>
      <compiler>{compilerJar.path}</compiler>
      {extraJars.map { jar =>
        <extra>{jar.path}</extra>
      }}
      {options.map { option =>
        <option>{option}</option>
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

case class RepositoryData(modules: Seq[ModuleData]) {
  def toXML(implicit fs: FS): Elem = {
    <repository>
      {modules.sortBy(it => (it.id.organization, it.id.name)).map(_.toXML)}
    </repository>
  }
}

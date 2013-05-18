package org.jetbrains.sbt
package project.model

import scala.xml.Node
import java.io.File
import FS._

/**
 * @author Pavel Fatin
 */

case class FS(home: File, base: Option[File] = None) {
  def withBase(base: File): FS = copy(base = Some(base))
}

object FS {
  def file(path: String)(implicit fs: FS): File = {
    if (path.startsWith("~/")) {
      new File(fs.home, path.substring(2))
    } else {
      fs.base.map(new File(_, path)).getOrElse(new File(path))
    }
  }
}

object Parser {
  def parse(node: Node, home: File): StructureData = {
    implicit val fs = new FS(home)

    val project = parseProject(node ! "project")
    val repository = parseRepository(node ! "repository")

    StructureData(project, repository)
  }

  private def parseProject(node: Node)(implicit fs: FS): ProjectData = {
    val name = (node \ "name").text
    val organization = (node \ "organization").text
    val version = (node \ "version").text
    val base = new File((node \ "base").text)
    val configurations = (node \ "configuration").map(parseConfiguration(_)(fs.withBase(base)))
    val scala = (node \ "scala").headOption.map(parseScala(_)(fs.withBase(base)))
    val projects = (node \ "project").map(parseProject)

    ProjectData(name, organization, version, base, configurations, scala, projects)
  }

  private def parseScala(node: Node)(implicit fs: FS): ScalaData = {
    val version = (node \ "version").text
    val library = file((node \ "library").text)
    val compiler = file((node \ "compiler").text)
    val extra = (node \ "extra").map(e => file(e.text))

    ScalaData(version, library, compiler, extra)
  }

  private def parseConfiguration(node: Node)(implicit fs: FS): ConfigurationData = {
    val id = (node \ "@id").text
    val sources = (node \ "sources").map(e => file(e.text))
    val resources = (node \ "resources").map(e => file(e.text))
    val classes = file((node ! "classes").text)
    val dependencies = (node \ "dependency").map(_.text)
    val modules = (node \ "module").map(parseModuleIdentifier)
    val jars = (node \ "jar").map(e => file(e.text))

    ConfigurationData(id, sources, resources, classes, dependencies, modules, jars)
  }

  private def parseModuleIdentifier(node: Node): ModuleIdData = {
    val organization = (node \ "@organization").text
    val name = (node \ "@name").text
    val revision = (node \ "@revision").text

    ModuleIdData(organization, name, revision)
  }

  private def parseRepository(node: Node)(implicit fs: FS): RepositoryData = {
    val modules = (node \ "module").map { it =>
      val identifier = parseModuleIdentifier(node)

      val binaries = (it \ "jar").map(e => file(e.text))
      val docs = (it \ "doc").map(e => file(e.text))
      val sources = (it \ "src").map(e => file(e.text))

      ModuleData(identifier, binaries, docs, sources)
    }

    RepositoryData(new File("."), modules)
  }

  private implicit class NodeExt(node: Node) {
    def !(name: String): Node = (node \ name) match {
      case Seq() => throw new RuntimeException(s"No $name node in $node")
      case Seq(child) => child
      case _ => throw new RuntimeException(s"Multiple $name nodes in $node")
    }
  }
}

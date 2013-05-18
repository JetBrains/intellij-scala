package org.jetbrains.sbt
package project.model

import scala.xml.Node
import java.io.File

/**
 * @author Pavel Fatin
 */
object Parser {
  def parse(node: Node): StructureData = {
    val project = parseProject(node ! "project")
    val repository = parseRepository(node ! "repository")

    StructureData(project, repository)
  }

  private def parseProject(node: Node): ProjectData = {
    val name = (node \ "name").text
    val organization = (node \ "organization").text
    val version = (node \ "version").text
    val base = new File((node \ "base").text)
    val configurations = (node \ "configuration").map(parseConfiguration)
    val scala = (node \ "scala").headOption.map(parseScala)
    val projects = (node \ "project").map(parseProject)

    ProjectData(name, organization, version, base, configurations, scala, projects)
  }

  private def parseScala(node: Node): ScalaData = {
    val version = (node \ "version").text
    val library = new File((node \ "library").text)
    val compiler = new File((node \ "compiler").text)
    val extra = (node \ "extra").map(e => new File(e.text))

    ScalaData(version, library, compiler, extra)
  }

  private def parseConfiguration(node: Node): ConfigurationData = {
    val id = (node \ "@id").text
    val sources = (node \ "sources").map(e => new File(e.text))
    val resources = (node \ "resources").map(e => new File(e.text))
    val classes = new File((node ! "classes").text)
    val dependencies = (node \ "dependency").map(_.text)
    val modules = (node \ "module").map(parseModuleIdentifier)
    val jars = (node \ "jar").map(e => new File(e.text))

    ConfigurationData(id, sources, resources, classes, dependencies, modules, jars)
  }

  private def parseModuleIdentifier(node: Node): ModuleIdData = {
    val organization = (node \ "@organization").text
    val name = (node \ "@name").text
    val revision = (node \ "@revision").text

    ModuleIdData(organization, name, revision)
  }

  private def parseRepository(node: Node): RepositoryData = {
    val modules = (node \ "module").map { it =>
      val identifier = parseModuleIdentifier(node)

      val binaries = (it \ "jar").map(e => new File(e.text))
      val docs = (it \ "doc").map(e => new File(e.text))
      val sources = (it \ "src").map(e => new File(e.text))

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

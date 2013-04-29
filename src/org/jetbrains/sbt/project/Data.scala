package org.jetbrains.sbt
package project

import java.io.File
import scala.xml.Node

object Data {
  case class Structure(project: Project, repository: Repository)

  case class Project(name: String, base: File, configurations: Seq[Configuration], scala: Option[Scala], projects: Seq[Project])

  case class Configuration(id: String, sources: Seq[File], classes: File, modules: Seq[ModuleIdentifier], jars: Seq[File])

  case class Scala(version: String, libraryJar: File, compilerJar: File, extraJars: Seq[File])

  case class ModuleIdentifier(organization: String, name: String, revision: String)

  case class ModuleData(id: ModuleIdentifier, binaries: Seq[File], docs: Seq[File], sources: Seq[File])

  case class Repository(base: File, modules: Seq[ModuleData])

  def parse(node: Node): Structure = {
    val project = parseProject(node ! "project")
    val repository = parseRepository(node ! "repository")

    Structure(project, repository)
  }

  private def parseProject(node: Node): Project = {
    val name = (node \ "name").text
    val base = new File((node \ "base").text)
    val configurations = (node \ "configuration").map(parseConfiguration)
    val scala = (node \ "scala").headOption.map(parseScala)
    val projects = (node \ "project").map(parseProject)

    Project(name, base, configurations, scala, projects)
  }

  private def parseScala(node: Node): Scala = {
    val version = (node \ "version").text
    val library = new File((node \ "library").text)
    val compiler = new File((node \ "compiler").text)
    val extra = (node \ "extra").map(e => new File(e.text))

    Scala(version, library, compiler, extra)
  }

  private def parseConfiguration(node: Node): Configuration = {
    val id = (node \ "@id").text
    val sources = (node \ "sources").map(e => new File(e.text))
    val classes = new File((node ! "classes").text)
    val modules = (node \ "module").map(parseModuleIdentifier)
    val jars = (node \ "jar").map(e => new File(e.text))

    Configuration(id, sources, classes, modules, jars)
  }

  private def parseModuleIdentifier(node: Node): ModuleIdentifier = {
    val organization = (node \ "@organization").text
    val name = (node \ "@name").text
    val revision = (node \ "@revision").text

    ModuleIdentifier(organization, name, revision)
  }

  private def parseRepository(node: Node): Repository = {
    val modules = (node \ "module").map { it =>
      val identifier = parseModuleIdentifier(node)

      val binaries = (it \ "jar").map(e => new File(e.text))
      val docs = (it \ "doc").map(e => new File(e.text))
      val sources = (it \ "src").map(e => new File(e.text))

      ModuleData(identifier, binaries, docs, sources)
    }

    Repository(new File("."), modules)
  }

  private implicit class NodeExt(node: Node) {
    def !(name: String): Node = (node \ name) match {
      case Seq() => throw new RuntimeException(s"No ${name} node in ${node}")
      case Seq(child) => child
      case _ => throw new RuntimeException(s"Multiple ${name} nodes in ${node}")
    }
  }
}
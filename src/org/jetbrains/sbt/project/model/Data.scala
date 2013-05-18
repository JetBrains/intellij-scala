package org.jetbrains.sbt
package project.model

import java.io.File

case class Structure(project: Project, repository: Repository)

case class Project(name: String, organization: String, version: String, base: File, configurations: Seq[Configuration], scala: Option[Scala], projects: Seq[Project])

case class Configuration(id: String, sources: Seq[File], resources: Seq[File], classes: File, dependencies: Seq[String], modules: Seq[ModuleId], jars: Seq[File])

case class Scala(version: String, libraryJar: File, compilerJar: File, extraJars: Seq[File])

case class ModuleId(organization: String, name: String, revision: String)

case class Module(id: ModuleId, binaries: Seq[File], docs: Seq[File], sources: Seq[File])

case class Repository(base: File, modules: Seq[Module])

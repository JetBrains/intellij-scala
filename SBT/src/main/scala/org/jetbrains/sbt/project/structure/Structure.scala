package org.jetbrains.sbt
package project.structure

import java.io.File

case class Structure(projects: Seq[Project], repository: Option[Repository])

case class Project(id: String, name: String, organization: String, version: String, base: File, target: File, build: Build, configurations: Seq[Configuration], java: Option[Java], scala: Option[Scala], dependencies: Dependencies)

case class Build(imports: Seq[String], classes: Seq[File], docs: Seq[File], sources: Seq[File])

case class Configuration(id: String, sources: Seq[Directory], resources: Seq[Directory], classes: File)

case class Java(home: Option[File], options: Seq[String])

case class Scala(version: String, libraryJar: File, compilerJar: File, extraJars: Seq[File], options: Seq[String])

case class Dependencies(projects: Seq[ProjectDependency], modules: Seq[ModuleDependency], jars: Seq[JarDependency])

case class ProjectDependency(project: String, configurations: Seq[String])

case class ModuleDependency(id: ModuleId, configurations: Seq[String])

case class JarDependency(file: File, configurations: Seq[String])

case class ModuleId(organization: String, name: String, revision: String)

case class Module(id: ModuleId, binaries: Seq[File], docs: Seq[File], sources: Seq[File])

case class Repository(base: File, modules: Seq[Module])

case class Directory(file: File, managed: Boolean)
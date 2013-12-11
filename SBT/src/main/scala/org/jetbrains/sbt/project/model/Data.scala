package org.jetbrains.sbt
package project.model

import java.io.File

case class Structure(projects: Seq[Project], repository: Option[Repository])

case class Project(id: String, name: String, organization: String, version: String, base: File, build: Build, configurations: Seq[Configuration], java: Option[Java], scala: Option[Scala], dependencies: Seq[String])

case class Build(classpath: Seq[File], imports: Seq[String])

case class Configuration(id: String, sources: Seq[Directory], resources: Seq[Directory], classes: File, modules: Seq[ModuleId], jars: Seq[File])

case class Java(home: File, options: Seq[String])

case class Scala(version: String, libraryJar: File, compilerJar: File, extraJars: Seq[File], options: Seq[String])

case class ModuleId(organization: String, name: String, revision: String)

case class Module(id: ModuleId, binaries: Seq[File], docs: Seq[File], sources: Seq[File])

case class Repository(base: File, modules: Seq[Module])

case class Directory(file: File, managed: Boolean)
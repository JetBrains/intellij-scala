package org.jetbrains.sbt
package project.model

import java.io.File

case class StructureData(project: ProjectData, repository: RepositoryData)

case class ProjectData(name: String, organization: String, version: String, base: File, configurations: Seq[ConfigurationData], scala: Option[ScalaData], projects: Seq[ProjectData])

case class ConfigurationData(id: String, sources: Seq[File], resources: Seq[File], classes: File, dependencies: Seq[String], modules: Seq[ModuleIdData], jars: Seq[File])

case class ScalaData(version: String, libraryJar: File, compilerJar: File, extraJars: Seq[File])

case class ModuleIdData(organization: String, name: String, revision: String)

case class ModuleData(id: ModuleIdData, binaries: Seq[File], docs: Seq[File], sources: Seq[File])

case class RepositoryData(base: File, modules: Seq[ModuleData])

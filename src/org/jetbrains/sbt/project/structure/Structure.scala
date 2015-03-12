package org.jetbrains.sbt
package project.structure

import java.io.File

import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.structure.Play2Keys.AllKeys.ParsedValue

case class Structure(projects: Seq[Project], repository: Option[Repository], localCachePath: Option[String], sbtVersion: String)

case class Project(id: String, name: String, organization: String, version: String, base: File,
                   basePackages: Seq[String], target: File, build: Build, configurations: Seq[Configuration],
                   java: Option[Java], scala: Option[Scala], android: Option[Android],dependencies: Dependencies,
                   resolvers: Set[Resolver], play2: Option[Play2])

case class Build(imports: Seq[String], classes: Seq[File], docs: Seq[File], sources: Seq[File])

case class Configuration(id: String, sources: Seq[Directory], resources: Seq[Directory], excludes: Seq[File], classes: File)

case class Java(home: Option[File], options: Seq[String])

case class Scala(version: Version, libraryJar: File, compilerJar: File, extraJars: Seq[File], options: Seq[String])

case class Android(version: String, manifestFile: File, apkPath: File, resPath: File, assetsPath: File, genPath: File, libsPath: File, isLibrary: Boolean, proguardConfig: Seq[String])

case class Play2(keys: Map[String, Map[String, ParsedValue[_]]])

case class Dependencies(projects: Seq[ProjectDependency], modules: Seq[ModuleDependency], jars: Seq[JarDependency])

case class ProjectDependency(project: String, configurations: Seq[String])

case class ModuleDependency(id: ModuleId, configurations: Seq[String])

case class JarDependency(file: File, configurations: Seq[String])

case class ModuleId(organization: String, name: String, revision: String, artifactType: String, classifier: Option[String])

case class Module(id: ModuleId, binaries: Seq[File], docs: Seq[File], sources: Seq[File])

case class Repository(base: File, modules: Seq[Module])

case class Directory(file: File, managed: Boolean)

case class Resolver(name: String, root: String)
package org.jetbrains.sbt.project.structure.data

import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.sbt.project.structure.data.Helpers.uri
import org.jetbrains.sbt.project.structure.data.XmlDeserializer.{deserializeNodeSeq, deserializeOne}

import java.net.URI
import scala.util.Try

case class ProjectData(
  id: String,
  buildURI: URI,
  name: String,
  organization: String,
  version: String,
  base: InterpretablePath,
  packagePrefix: Option[String],
  basePackages: Seq[String],
  target: InterpretablePath,
  configurations: Seq[ConfigurationData],
  java: Option[JavaData],
  scala: Option[ScalaData],
  kotlin: Option[KotlinData],
  compileOrder: String,
  dependencies: DependencyData,
  resolvers: Set[ResolverData],
  play2: Option[Play2Data],
  settings: Seq[SettingData],
  tasks: Seq[TaskData],
  commands: Seq[CommandData],
  mainSourceDirectories: Seq[InterpretablePath],
  testSourceDirectories: Seq[InterpretablePath],
  generatedManagedSources: Boolean
)

object ProjectData:
  given (PathConstructor[String], EelDescriptor) => XmlDeserializer[ProjectData] = what =>
    val id = (what \ "id").text
    val buildURI = (what \ "buildURI").text.uri
    val name = (what \ "name").text
    val organization = (what \ "organization").text
    val version = (what \ "version").text
    val base = InterpretablePath.construct((what \ "base").text)
    val testSourceDirectories = (what \ "testSourceDir").map(_.text).map(InterpretablePath.construct)
    val mainSourceDirectories = (what \ "mainSourceDir").map(_.text).map(InterpretablePath.construct)
    val packagePrefix = (what \ "packagePrefix").headOption.map(_.text)
    val basePackages = (what \ "basePackage").map(_.text)
    val target = InterpretablePath.construct((what \ "target").text)

    val configurations = (what \ "configuration").deserializeNodeSeq[ConfigurationData]
    val java = (what \ "java").deserializeNodeSeq[JavaData].headOption
    val scala = (what \ "scala").deserializeNodeSeq[ScalaData].headOption
    val kotlin = (what \ "kotlin").deserializeNodeSeq[KotlinData].headOption
    val compileOrder = (what \ "compileOrder").text
    val resolvers = (what \ "resolver").deserializeNodeSeq[ResolverData].toSet
    val play2 = (what \ "play2").deserializeNodeSeq[Play2Data].headOption

    val settings = (what \ "setting").deserializeNodeSeq[SettingData]
    val tasks = (what \ "task").deserializeNodeSeq[TaskData]
    val commands = (what \ "command").deserializeNodeSeq[CommandData]

    val generatedManagedSources = (what \ "generatedManagedSources")
      .headOption
      .map(_.text)
      .flatMap(text => Try(text.toBoolean).toOption)
      .getOrElse(false)

    val tryDeps = (what \ "dependencies").deserializeOne[DependencyData]
    tryDeps.map: dependencies =>
      ProjectData(id, buildURI, name, organization, version, base, packagePrefix, basePackages,
        target, configurations, java, scala, kotlin, compileOrder,
        dependencies, resolvers, play2, settings, tasks, commands, mainSourceDirectories, testSourceDirectories, generatedManagedSources)

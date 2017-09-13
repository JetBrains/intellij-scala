package org.jetbrains.plugins.cbt.project.model

import java.io.File

import org.jetbrains.plugins.cbt.project.model.CbtProjectInfo.ModuleType.{Build, Default, Extra, Test}

import scala.xml.Node

object CbtProjectInfo {

  def apply(xml: Node): Project =
    Deserializer(xml)

  case class Project(name: String,
                     root: File,
                     modules: Seq[Module],
                     libraries: Seq[Library],
                     cbtLibraries: Seq[Library],
                     scalaCompilers: Seq[ScalaCompiler])

  case class Module(name: String,
                    root: File,
                    scalaVersion: String,
                    sourceDirs: Seq[File],
                    target: File,
                    moduleType: ModuleType,
                    binaryDependencies: Seq[BinaryDependency],
                    moduleDependencies: Seq[ModuleDependency],
                    classpath: Seq[File],
                    parentBuild: Option[String],
                    scalacOptions: Seq[String])

  case class Library(name: String, jars: Seq[LibraryJar])

  case class BinaryDependency(name: String)

  case class ModuleDependency(name: String)

  case class ScalaCompiler(version: String, jars: Seq[File])

  case class LibraryJar(jar: File, jarType: JarType)

  abstract class JarType(name: String)
  object JarType {
    object Binary extends JarType("binary")
    object Source extends JarType("source")

    def apply(name: String): JarType = name match {
      case "binary" => Binary
      case "source" => Source
    }
  }

  abstract class ModuleType(name: String)
  object ModuleType {
    object Default extends ModuleType("default")
    object Extra extends ModuleType("extra")
    object Test extends ModuleType("test")
    object Build extends ModuleType("build")

    def apply(name: String): ModuleType = name match {
      case "default" => Default
      case "extra" => Extra
      case "test" => Test
      case "build" => Build
    }
  }
}

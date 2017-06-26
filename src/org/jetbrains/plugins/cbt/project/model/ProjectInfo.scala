package org.jetbrains.plugins.cbt.project.model

import java.io.File

import scala.xml.Node

object ProjectInfo {

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
                    binaryDependencies: Seq[BinaryDependency],
                    moduleDependencies: Seq[ModuleDependency],
                    classpath: Seq[File],
                    parentBuild: Option[String],
                    scalacOptions: Seq[String])

  case class Library(name: String, jars: Seq[File])

  case class BinaryDependency(name: String)

  case class ModuleDependency(name: String)

  case class ScalaCompiler(version: String, jars: Seq[File])
}

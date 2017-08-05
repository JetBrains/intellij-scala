package org.jetbrains.plugins.cbt.project.model

import java.io.File

import org.jetbrains.plugins.cbt._
import org.jetbrains.plugins.cbt.project.model.CbtProjectInfo._

import scala.xml._

object Deserializer {
  def apply(xml: Node): Project =
    deserialize(xml)

  private def deserialize(node: Node): Project =
    deserializeProject(node)

  private def deserializeProject(node: Node): Project = {
    Project((node \ "@name").value,
      root = new File((node \ "@root").value),
      modules = (node \ "modules" \ "module").map(deserializeModule),
      libraries = (node \ "libraries" \ "library").map(deserializeLibrary),
      cbtLibraries = (node \ "cbtLibraries" \ "library").map(deserializeLibrary),
      scalaCompilers = (node \ "scalaCompilers" \ "compiler").map(deserializeCompiler)
    )
  }

  private def deserializeModule(node: Node): Module =
    Module(name = (node \ "@name").value,
      root = new File((node \ "@root").value),
      scalaVersion = (node \ "@scalaVersion").value,
      moduleType = ModuleType((node \ "@type").value),
      sourceDirs = (node \ "sourceDirs" \ "dir").map(_.value.toFile),
      target = (node \ "@target").value.toFile,
      binaryDependencies = (node \ "dependencies" \ "binaryDependency").map(deserializeBinaryDependency),
      moduleDependencies =
        (node \ "dependencies" \ "moduleDependency" ++ node \ "parentBuild").map(deserializeModuleDependency),
      classpath = (node \ "classpath" \ "classpathItem").map(_.value.toFile),
      parentBuild = (node \ "parentBuild").headOption.map(_.value),
      scalacOptions = (node \ "scalacOptions" \ "option").map(_.value))

  private def deserializeBinaryDependency(node: Node): BinaryDependency =
    BinaryDependency(node.value)

  private def deserializeModuleDependency(node: Node): ModuleDependency =
    ModuleDependency(node.value)

  private def deserializeLibrary(node: Node): Library = {
    val jars = (node \ "jar")
      .map(j => LibraryJar(j.value.toFile, JarType((j \ "@type").value)))
    Library(name = (node \ "@name").value, jars = jars)
  }

  private def deserializeCompiler(node: Node): ScalaCompiler =
    ScalaCompiler(version = (node \ "@version").value,
      jars = (node \ "jar").map(_.value.toFile))
}

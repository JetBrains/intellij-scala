package org.jetbrains.plugins.cbt.project.model

import java.io.File

import org.jetbrains.plugins.cbt.project.model.CbtProjectInfo._

import scala.xml._

object Deserializer {

  private implicit class XmlOps(val xml: NodeSeq) {
    def value: String =
      xml.text.trim
  }

  private implicit class StringOps(val str: String) {
    def toFile: File =
      new File(str)
  }

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
      sourceDirs = (node \ "sourceDirs" \ "dir").map(_.value.toFile),
      target = (node \ "@target").value.toFile,
      binaryDependencies = (node \ "dependencies" \ "binaryDependency").map(deserializeBinaryDependency),
      moduleDependencies = (node \ "dependencies" \ "moduleDependency").map(deserializeModuleDependency),
      classpath = (node \ "classpath" \ "classpathItem").map(_.value.toFile),
      parentBuild = (node \ "parentBuild").headOption.map(_.value),
      scalacOptions = (node \ "scalacOptions" \ "option").map(_.value))

  private def deserializeBinaryDependency(node: Node): BinaryDependency =
    BinaryDependency(node.value)

  private def deserializeModuleDependency(node: Node): ModuleDependency =
    ModuleDependency(node.value)

  private def deserializeLibrary(node: Node): Library =
    Library(name = (node \ "@name").value,
      jars = (node \ "jar").map(_.value.toFile))

  private def deserializeCompiler(node: Node): ScalaCompiler =
    ScalaCompiler(version = (node \ "@version").value,
      jars = (node \ "jar").map(_.value.toFile))
}

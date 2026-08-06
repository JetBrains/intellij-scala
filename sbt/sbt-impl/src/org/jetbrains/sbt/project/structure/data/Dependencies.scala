package org.jetbrains.sbt.project.structure.data

import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.sbt.project.structure.data.XmlDeserializer.deserializeNodeSeq

case class Dependencies[T](forProduction: Seq[T], forTest: Seq[T])

object Dependencies:
  private def dependenciesSerializer[A](nodeName: String)(using XmlDeserializer[A]): XmlDeserializer[Dependencies[A]] = what =>
    val testDependencies = (what \ "forTest" \ nodeName).deserializeNodeSeq[A]
    val compileDependencies = (what \ "forProduction" \ nodeName).deserializeNodeSeq[A]
    Right(Dependencies(compileDependencies, testDependencies))

  given jarDependenciesSerializer: PathConstructor[String] => XmlDeserializer[Dependencies[JarDependencyData]] =
    dependenciesSerializer("jar")

  given moduleDependenciesSerializer: XmlDeserializer[Dependencies[ModuleDependencyData]] =
    dependenciesSerializer("module")

  given (EelDescriptor) => XmlDeserializer[Dependencies[ProjectDependencyData]] =
    dependenciesSerializer("project")


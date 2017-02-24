package org.jetbrains.plugins.scala.project.template

import org.jetbrains.plugins.scala.project.Version

/**
  * @author Pavel Fatin
  */
object DottyComponents {
  def isDottyComponent(component: Component): Boolean = component match {
    case Component(Artifact.ScalaLibrary, _, Some(Version("2.11.5")), _) => true
    case Component(Artifact.ScalaReflect, _, Some(Version("2.11.5")), _) => true
    case Component(Artifact.ScalaCompiler, _, Some(Version("2.11.5-20151022-113908-7fb0e653fd")), _) => true
    case Component(Artifact.Dotty, _, Some(Version("0.1-SNAPSHOT")), _) => true
    case Component(Artifact.JLine, _, Some(Version("2.12")), _) => true
    case Component(Artifact.DottyInterfaces, _, _, _) => true
    case _ => false
  }
}

package org.jetbrains.plugins.scala
package debugger

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

import scala.collection.mutable

trait ScalaSdkOwner {

  import base.libraryLoaders.LibraryLoader

  implicit val version: ScalaVersion

  implicit protected def module: Module

  def project: Project = module.getProject

  protected def librariesLoaders: Seq[LibraryLoader]

  private lazy val myLoaders = mutable.ListBuffer.empty[LibraryLoader]

  protected def setUpLibraries(): Unit = librariesLoaders.foreach { loader =>
    myLoaders += loader
    loader.init
  }

  protected def disposeLibraries(): Unit = {
    myLoaders.foreach(_.clean)
    myLoaders.clear()
  }

}

// Java compatibility
trait DefaultScalaSdkOwner extends ScalaSdkOwner with TestFixtureProvider {
  override implicit val version: ScalaVersion = Scala_2_10
}

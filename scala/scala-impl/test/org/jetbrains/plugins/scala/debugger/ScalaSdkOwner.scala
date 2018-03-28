package org.jetbrains.plugins.scala.debugger

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.TestFixtureProvider
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader

import scala.collection.mutable.ListBuffer

case object Scala_2_13 extends ScalaVersion {
  override final val major: String = "2.13"
  override final val minor: String = "2.13.0-M4-pre-20d3c21"
}

trait ScalaSdkOwner {
  implicit val version: ScalaVersion

  implicit protected def module: Module

  def project: Project = module.getProject

  protected def librariesLoaders: Seq[LibraryLoader]

  private lazy val myLoaders: ListBuffer[LibraryLoader] = ListBuffer()

  protected def setUpLibraries(): Unit = {
    librariesLoaders.foreach { loader =>
      myLoaders += loader
      loader.init
    }
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

package org.jetbrains.plugins.scala
package base

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

trait ScalaSdkOwner {

  implicit val version: ScalaVersion

  protected def librariesLoaders: Seq[LibraryLoader]

  protected lazy val myLoaders: ListBuffer[LibraryLoader] = mutable.ListBuffer.empty[LibraryLoader]

  protected def setUpLibraries(implicit module: Module): Unit = {

    librariesLoaders.foreach { loader =>
      myLoaders += loader
      loader.init
    }
  }

  protected def disposeLibraries(implicit module: Module): Unit = {
    myLoaders.foreach(_.clean)
    myLoaders.clear()
  }

}

// Java compatibility
trait DefaultScalaSdkOwner extends ScalaSdkOwner with TestFixtureProvider {
  override implicit val version: ScalaVersion = Scala_2_13
}

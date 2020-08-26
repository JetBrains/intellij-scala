package org.jetbrains.plugins.scala.base

import com.intellij.openapi.module.Module
import junit.framework.Test
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader

import scala.collection.mutable

trait LibrariesOwner {
  self: Test with ScalaVersionProvider =>

  protected def librariesLoaders: collection.Seq[LibraryLoader]

  private lazy val myLoaders = mutable.ListBuffer.empty[LibraryLoader]

  protected def setUpLibraries(implicit module: Module): Unit =
    librariesLoaders.foreach { loader =>
      myLoaders += loader
      loader.init(module, version)
    }

  protected def disposeLibraries(implicit module: Module): Unit = {
    myLoaders.foreach(_.clean)
    myLoaders.clear()
  }
}

package org.jetbrains.plugins.scala.base

import com.intellij.openapi.module.Module
import junit.framework.Test
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader

import scala.collection.mutable

trait LibrariesOwner {
  self: Test & ScalaVersionProvider =>

  protected def librariesLoaders: Seq[LibraryLoader]
  final def librariesLoadersPublic: Seq[LibraryLoader] = librariesLoaders

  // A non-generic state accessor avoids Scala 3's raw trait-field accessors in Java subclasses.
  private object Loaders {
    val buffer: mutable.ListBuffer[LibraryLoader] = mutable.ListBuffer.empty
  }

  protected def setUpLibraries(module: Module): Unit =
    librariesLoaders.foreach { loader =>
      Loaders.buffer += loader
      loader.init(using module, version)
    }

  protected def disposeLibraries(module: Module): Unit = {
    Loaders.buffer.foreach(_.clean(using module))
    Loaders.buffer.clear()
  }
}

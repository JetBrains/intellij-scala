package org.jetbrains.plugins.scala.base

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader

trait LibraryRegistrar {
  def registerLibrary(loader: LibraryLoader)
}

class DirectRegistrar(module: Module) extends LibraryRegistrar {
  override def registerLibrary(loader: LibraryLoader): Unit = ???
}

class LightFixtureRegistrar()
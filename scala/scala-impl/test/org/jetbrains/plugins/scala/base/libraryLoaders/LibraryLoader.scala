package org.jetbrains.plugins.scala
package base
package libraryLoaders

import com.intellij.openapi.module.Module

/**
  * @author adkozlov
  */
trait LibraryLoader {
  def init(implicit module: Module, version: ScalaVersion)

  def clean(implicit module: Module): Unit = ()
}

package org.jetbrains.plugins.scala
package base
package libraryLoaders

import com.intellij.openapi.module.Module

trait LibraryLoader {
  def init(implicit module: Module, version: ScalaVersion): Unit

  def clean(implicit module: Module): Unit = ()
}

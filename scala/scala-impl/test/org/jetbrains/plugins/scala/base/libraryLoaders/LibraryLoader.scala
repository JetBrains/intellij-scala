package org.jetbrains.plugins.scala
package base
package libraryLoaders

import com.intellij.openapi.module.Module

/**
 * Note, the library loader is per-module.
 *
 * This means if, for example, the library loader represents JDK ([[SmartJDKLoader]] and inheritors),
 * then it will register the JDK for the module, but not for the project
 */
trait LibraryLoader {
  def init(implicit module: Module, version: ScalaVersion): Unit

  def clean(implicit module: Module): Unit = ()
}

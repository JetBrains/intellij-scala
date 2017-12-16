package org.jetbrains.plugins.scala.base.libraryLoaders

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.debugger.ScalaVersion

import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManager._

class IvyManagedLoader(dependencies: Dependency*) extends LibraryLoader {
  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    DependencyManager(dependencies:_*).loadAll
  }
}

object IvyManagedLoader {
  def apply(dependencies: Dependency*): IvyManagedLoader = new IvyManagedLoader(dependencies:_*)
}

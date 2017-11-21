package org.jetbrains.plugins.scala.base.libraryLoaders

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.debugger.ScalaVersion

/**
  * @author adkozlov
  */
trait LibraryLoader {
  def init(implicit module: Module, version: ScalaVersion)

  def clean(implicit module: Module): Unit = ()
}

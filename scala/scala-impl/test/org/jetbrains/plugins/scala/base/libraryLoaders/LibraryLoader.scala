package org.jetbrains.plugins.scala.base.libraryLoaders

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.debugger.ScalaVersion

/**
  * @author adkozlov
  */
trait LibraryLoader extends Disposable {
  implicit val module: Module

  def init(implicit version: ScalaVersion): Unit

  def clean(): Unit = {}

  override def dispose(): Unit = clean()
}

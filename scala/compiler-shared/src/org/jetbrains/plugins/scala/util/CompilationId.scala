package org.jetbrains.plugins.scala.util

object CompilationId {

  def generate(): CompilationId =
    System.currentTimeMillis()
}

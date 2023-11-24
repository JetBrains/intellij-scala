package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.module.Module

trait SyntheticModule { self: Module =>
  def underlying: Module
}

package org.jetbrains.plugins.scala.project.settings

import com.intellij.openapi.module.Module

trait CompilerProfileAwareModule {
  this: Module =>

  def compilerProfileName: String
}

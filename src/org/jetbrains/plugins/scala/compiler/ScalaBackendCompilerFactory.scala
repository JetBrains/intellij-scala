package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.BackendCompilerFactory
import com.intellij.compiler.impl.javaCompiler.BackendCompiler
import com.intellij.openapi.project.Project

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.09.2008
 */

class ScalaBackendCompilerFactory extends BackendCompilerFactory {
  def create(project: Project): BackendCompiler = new ScalacCompiler(project)
}
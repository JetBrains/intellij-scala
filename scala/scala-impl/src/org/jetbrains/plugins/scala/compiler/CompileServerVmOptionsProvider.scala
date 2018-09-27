package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

abstract class CompileServerVmOptionsProvider {
  def vmOptionsFor(project: Project): Seq[String]
}

object CompileServerVmOptionsProvider
  extends ExtensionPointDeclaration[CompileServerVmOptionsProvider]("org.intellij.scala.compileServerVmOptionsProvider")

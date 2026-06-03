package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.compiler.CompilationUnitId.ModuleId

case class CompilationUnitId(moduleId: ModuleId,
                             testScope: Boolean)

object CompilationUnitId {
  type ModuleId = String
}
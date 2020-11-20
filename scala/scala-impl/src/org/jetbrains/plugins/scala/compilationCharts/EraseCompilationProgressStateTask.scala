package org.jetbrains.plugins.scala.compilationCharts

import com.intellij.openapi.compiler.{CompileContext, CompileTask}

class EraseCompilationProgressStateTask
  extends CompileTask {

  override def execute(context: CompileContext): Boolean = {
    CompilationProgressStateManager.erase(context.getProject)
    true
  }
}

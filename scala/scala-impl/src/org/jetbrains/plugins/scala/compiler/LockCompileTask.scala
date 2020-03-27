package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.compiler.{CompileContext, CompileTask}

class LockCompileTask
  extends CompileTask {

  override def execute(context: CompileContext): Boolean = {
    CompilerLock.lock(context.getProject)
    true
  }
}

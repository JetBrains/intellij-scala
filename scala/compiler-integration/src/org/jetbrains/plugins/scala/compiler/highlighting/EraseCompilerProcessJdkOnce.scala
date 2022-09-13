package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.{CompileContext, CompileTask}
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * See registry key description
 */
class EraseCompilerProcessJdkOnce
  extends CompileTask {

  import EraseCompilerProcessJdkOnce.RegistryKey

  override def execute(context: CompileContext): Boolean = {
    val isUnitTestMode = ApplicationManager.getApplication.isUnitTestMode
    val needErase = Registry.is(RegistryKey)
    if (!isUnitTestMode && context.getProject.hasScala && needErase) {
      Registry.get("compiler.process.jdk").setValue("")
      Registry.get(RegistryKey).setValue(false)
    }
    true
  }
}

object EraseCompilerProcessJdkOnce {
  private final val RegistryKey = "scala.erase.compiler.process.jdk.once"
}

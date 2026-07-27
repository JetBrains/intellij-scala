package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.compile.ScalaCompileTask

/**
 * See registry key description
 */
class EraseCompilerProcessJdkOnce extends ScalaCompileTask {

  import EraseCompilerProcessJdkOnce.RegistryKey

  override protected def run(context: CompileContext): Boolean = {
    val isUnitTestMode = ApplicationManager.getApplication.isUnitTestMode
    val needErase = Registry.is(RegistryKey)
    if (!isUnitTestMode && context.getProject.hasScala && needErase) {
      Registry.get("compiler.process.jdk").setValue("")
      Registry.get(RegistryKey).setValue(false)
    }
    true
  }

  override protected def presentableName: String = "Erasing compiler process jdk registry value"

  override protected def log: Logger = EraseCompilerProcessJdkOnce.Log
}

object EraseCompilerProcessJdkOnce {
  private final val RegistryKey = "scala.erase.compiler.process.jdk.once"

  private val Log: Logger = Logger.getInstance(classOf[EraseCompilerProcessJdkOnce])
}

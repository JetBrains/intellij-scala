package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

import java.io.File

/**
 * There was a request from a proprietary plugin developers to add this extension point: <br>
 * <p><i>
 * The main reason why we can’t use standard build tools (not just for Scala) is that we never store source code
 * (and .class files) on a hard drive. We definitely want to evaluate expressions in the debugger, so being able to plug
 * in our own implementation would be great.
 * </i></p>
 * <br>
 * NOTE: only single implementation will be used (first found implementation)
 */
@ApiStatus.Internal
trait EvaluatorCompileHelper {

  /**
   * @return Array of all classfiles generated from a given source with corresponding dot-separated full qualified names
   *         (like "java.lang.Object" or "scala.None$")
   */
  def compile(fileText: String, module: Module): Array[(File, String)]
}

object EvaluatorCompileHelper
  extends ExtensionPointDeclaration[EvaluatorCompileHelper](
    "org.intellij.scala.evaluatorCompileHelper"
  ) {

  def needCompileServer: Boolean = implementations.isEmpty
}
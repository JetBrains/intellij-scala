package org.jetbrains.plugins.scala.debugger.evaluation

import java.io.File

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

/**
 * Nikolay.Tropin
 * 2014-11-27
 */
object EvaluatorCompileHelper {
  val EP_NAME: ExtensionPointName[EvaluatorCompileHelper] = ExtensionPointName.create[EvaluatorCompileHelper]("org.intellij.scala.evaluatorCompileHelper")

  def needCompileServer: Boolean = EP_NAME.getExtensions.isEmpty
}

trait EvaluatorCompileHelper {

  /**
   * @return Array of all classfiles generated from a given source with corresponding dot-separated full qualified names
   *         (like "java.lang.Object" or "scala.None$")
   */
  def compile(fileText: String, module: Module): Array[(File, String)]
}
package org.jetbrains.sbt
package project.module

import com.intellij.openapi.module.Module

/**
 * @author Pavel Fatin
 */
object SbtModule {
  private val ImportsKey = "sbt.imports"

  private val Delimiter = ", "

  def getImportsFrom(module: Module): Seq[String] =
    Option(module.getOptionValue(ImportsKey)).fold(Sbt.DefaultImplicitImports)(_.split(Delimiter))

  def setImportsTo(module: Module, imports: Seq[String]) =
    module.setOption(ImportsKey, imports.mkString(Delimiter))
}

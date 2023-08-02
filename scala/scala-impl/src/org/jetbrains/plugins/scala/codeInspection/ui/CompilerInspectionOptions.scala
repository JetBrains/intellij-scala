package org.jetbrains.plugins.scala.codeInspection.ui

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.project.ModuleExt

object CompilerInspectionOptions {

  val AlwaysEnabled: Int = 0
  val ComplyToCompilerOption: Int = 1
  val AlwaysDisabled: Int = 2

  def isCompilerOptionPresent(elem: ScalaPsiElement, compilerOptionName: String): Boolean =
    elem.module.exists(_.scalaCompilerSettings.additionalCompilerOptions.contains(compilerOptionName))

  def isInspectionAllowed(elem: ScalaPsiElement, checkCompilerOption: Boolean, compilerOptionName: String): Boolean =
    !checkCompilerOption || isCompilerOptionPresent(elem, compilerOptionName)

  def isInspectionAllowed(elem: ScalaPsiElement, checkCompilerOption: Int, compilerOptionName: String): Boolean =
    checkCompilerOption match {
      case AlwaysEnabled          => true
      case ComplyToCompilerOption => isCompilerOptionPresent(elem, compilerOptionName)
      case _                      => false
    }
}

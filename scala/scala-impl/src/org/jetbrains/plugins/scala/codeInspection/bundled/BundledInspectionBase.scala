package org.jetbrains.plugins.scala.codeInspection.bundled

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement

/**
  * User: Dmitry.Naydanov
  * Date: 03.10.16.
  */
abstract class BundledInspectionBase {
  final def getId: String = this.getClass.getName  
  
  def getName: String
  
  def getDescription: String

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any]
}
package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi._

class ScalaResolveResult(element : PsiElement) extends ResolveResult  {
  def getElement() = element

  def isValidResult() = true
}
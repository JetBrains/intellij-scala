package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi._

class ScalaResolveResult(element : PsiElement) extends ResolveResult  {
  def getElement() = element

  def isValidResult() = true
}

import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScDesignated

class ScalaClassRefResolveResult(val element : ScDesignated, val substitutor : ScSubstitutor) extends ScalaResolveResult(element) {
  def this(element : ScDesignated) = this(element, ScSubstitutor.empty)
}
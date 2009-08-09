package org.jetbrains.plugins.scala
package codeInsight.template.util

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, ResolveState}
import lang.resolve.{ScalaResolveResult, StdKinds}

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.2009
 */

object MacroUtil {

  /**
   * @param element from which position we look at locals
   * @return visible variables and values from element position
   */
  def getVariablesForScope(element: PsiElement): Array[ScalaResolveResult] = {
    val completionProcessor = new VariablesCompletionProcessor(StdKinds.valuesRef)
    PsiTreeUtil.treeWalkUp(completionProcessor, element, null, ResolveState.initial)
    completionProcessor.candidates
  }
}
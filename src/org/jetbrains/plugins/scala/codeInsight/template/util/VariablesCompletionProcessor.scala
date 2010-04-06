package org.jetbrains.plugins.scala
package codeInsight.template.util

import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.psi._

import lang.resolve.ResolveTargets
import lang.resolve.processor.BaseProcessor

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.2009
 */
class VariablesCompletionProcessor(override val kinds: Set[ResolveTargets.Value]) extends BaseProcessor(kinds) {
  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (kindMatches(element)) {
      candidatesSet += new ScalaResolveResult(named)
    }
    return true
  }
}
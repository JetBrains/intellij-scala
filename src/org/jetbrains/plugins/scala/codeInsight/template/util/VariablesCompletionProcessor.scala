package org.jetbrains.plugins.scala.codeInsight.template.util

import _root_.org.jetbrains.plugins.scala.lang.resolve.BaseProcessor
import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.psi._

import _root_.scala.collection.Set
import _root_.scala.collection.mutable.HashSet
import lang.resolve.ResolveTargets

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
package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.scope._
import com.intellij.psi._

import _root_.scala.collection.Set
import _root_.scala.collection.immutable.HashSet

class CompletionProcessor(override val kinds: Set[ResolveTargets.Value]) extends BaseProcessor(kinds) {

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    if (kindMatches(element)) {
      val named = element.asInstanceOf[PsiNamedElement]
      candidatesSet += new ScalaResolveResult (named)
    }
    return true
  }
}
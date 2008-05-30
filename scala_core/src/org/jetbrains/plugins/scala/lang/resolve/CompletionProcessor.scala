package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.scope._
import com.intellij.psi._

import _root_.scala.collection.Set
import _root_.scala.collection.immutable.HashSet

class CompletionProcessor(override val kinds: Set[ResolveTargets]) extends BaseProcessor(kinds) {

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (named != null) {
      candidates += new ScalaResolveResult(named)
    }
    return true
  }
}
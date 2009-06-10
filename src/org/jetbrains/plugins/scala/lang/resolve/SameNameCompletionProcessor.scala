package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.scope._
import com.intellij.psi._

import _root_.scala.collection.Set
import _root_.scala.collection.mutable.HashSet

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.01.2009
 */

class SameNameCompletionProcessor(override val kinds: Set[ResolveTargets.Value], val name: String) extends BaseProcessor(kinds) {
  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (kindMatches(element)) {
      val n = named.getName
      if (n == name) {
        candidatesSet += new ScalaResolveResult(named)
      }
    }
    return true
  }
}

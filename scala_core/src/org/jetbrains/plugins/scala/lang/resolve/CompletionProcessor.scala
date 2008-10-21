package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.scope._
import com.intellij.psi._

import _root_.scala.collection.Set
import _root_.scala.collection.mutable.HashSet

class CompletionProcessor(override val kinds: Set[ResolveTargets.Value]) extends BaseProcessor(kinds) {
  val names = new HashSet[String]
  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (kindMatches(element)) {
      val name = named.getName
      if (!names.contains(name)) {
        names += name
        candidatesSet += new ScalaResolveResult(named)
      }
    }
    return true
  }
}
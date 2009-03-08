package org.jetbrains.plugins.scala.lang.resolve

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, Signature, ScSubstitutor}
import com.intellij.psi.scope._
import com.intellij.psi._

import _root_.scala.collection.Set
import _root_.scala.collection.mutable.HashSet
import psi.api.base.patterns.ScBindingPattern
class CompletionProcessor(override val kinds: Set[ResolveTargets.Value]) extends BaseProcessor(kinds) {
  private val signatures = new HashSet[Signature]

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    def substitutor: ScSubstitutor = {
      state.get(ScSubstitutor.key) match {
        case null => ScSubstitutor.empty
        case x => x
      }
    }
    val named = element.asInstanceOf[PsiNamedElement]
    if (kindMatches(element)) {
      element match {
        case method: PsiMethod => {
          val sign = new PhysicalSignature(method, substitutor)
          if (!signatures.contains(sign)) {
            signatures += sign
            candidatesSet += new ScalaResolveResult(named)
          }
        }
        case patt: ScBindingPattern => {
          import Suspension._
          val sign = new Signature(patt.getName, Seq.empty, 0, Seq.empty.toArray, substitutor)
          if (!signatures.contains(sign)) {
            signatures += sign
            candidatesSet += new ScalaResolveResult(named)
          }
        }
        case _ => {
          candidatesSet += new ScalaResolveResult(named)
        }
      }
    }
    return true
  }
}
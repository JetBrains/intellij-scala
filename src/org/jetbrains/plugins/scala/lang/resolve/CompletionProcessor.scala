package org.jetbrains.plugins.scala.lang.resolve

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, Signature, ScSubstitutor}
import com.intellij.psi.scope._
import com.intellij.psi._

import _root_.scala.collection.Set
import _root_.scala.collection.mutable.HashSet
import psi.api.base.patterns.{ScPattern, ScReferencePattern, ScBindingPattern}
class CompletionProcessor(override val kinds: Set[ResolveTargets.Value]) extends BaseProcessor(kinds) {
  private val signatures = new HashSet[Signature]
  private val names = new HashSet[String]

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    def substitutor: ScSubstitutor = {
      state.get(ScSubstitutor.key) match {
        case null => ScSubstitutor.empty
        case x => x
      }
    }

    element match {
      case named: PsiNamedElement => {
        if (kindMatches(element)) {
          element match {
            case method: PsiMethod => {
              val sign = new PhysicalSignature(method, substitutor)
              if (!signatures.contains(sign)) {
                signatures += sign
                candidatesSet += new ScalaResolveResult(named, getCurrentContext)
              }
            }
            case patt: ScBindingPattern => {
              import Suspension._
              val sign = new Signature(patt.getName, Seq.empty, 0, Seq.empty.toArray, substitutor)
              if (!signatures.contains(sign)) {
                signatures += sign
                candidatesSet += new ScalaResolveResult(named, getCurrentContext)
              }
            }
            case _ => {
              if (!names.contains(named.getName)) {
                candidatesSet += new ScalaResolveResult(named, getCurrentContext)
                names += named.getName
              }
            }
          }
        }
      }
      case pat : ScPattern => for (b <- pat.bindings) execute(b, state)
      case _ => // Is it really a case?
    }
    return true
  }
}
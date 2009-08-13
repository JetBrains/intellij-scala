package org.jetbrains.plugins.scala
package lang
package resolve

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, Signature, ScSubstitutor}
import com.intellij.psi._

import _root_.scala.collection.Set
import collection.mutable.HashSet
import psi.api.base.patterns.{ScPattern, ScBindingPattern}

import psi.api.toplevel.typedef.ScTypeDefinition
import psi.ScalaPsiUtil
class CompletionProcessor(override val kinds: Set[ResolveTargets.Value]) extends BaseProcessor(kinds) {
  private val signatures = new HashSet[Signature]
  private val names = new HashSet[String]

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    lazy val substitutor: ScSubstitutor = {
      state.get(ScSubstitutor.key) match {
        case null => ScSubstitutor.empty
        case x => x
      }
    }

    lazy val isRenamed: Option[String] = {
      state.get(ResolverEnv.nameKey) match {
        case null => None
        case x: String => Some(x)
      }
    }

    element match {
      case td: ScTypeDefinition if !names.contains(td.getName) => {
        if (kindMatches(td)) candidatesSet += new ScalaResolveResult(td, substitutor, nameShadow = isRenamed)
        ScalaPsiUtil.getCompanionModule(td) match {
          case Some(td: ScTypeDefinition) if kindMatches(td)=> candidatesSet += new ScalaResolveResult(td, substitutor, nameShadow = isRenamed)
          case _ =>
        }
      }
      case named: PsiNamedElement => {
        if (kindMatches(element)) {
          element match {
            case method: PsiMethod => {
              val sign = new PhysicalSignature(method, substitutor)
              if (!signatures.contains(sign)) {
                signatures += sign
                candidatesSet += new ScalaResolveResult(named, substitutor, nameShadow = isRenamed)
              }
            }
            case bindingPattern: ScBindingPattern => {
              val sign = new Signature(isRenamed.getOrElse(bindingPattern.getName), Seq.empty, 0, substitutor)
              if (!signatures.contains(sign)) {
                signatures += sign
                candidatesSet += new ScalaResolveResult(named, substitutor, nameShadow = isRenamed)
              }
            }
            case _ => {
              if (!names.contains(named.getName)) {
                candidatesSet += new ScalaResolveResult(named, substitutor, nameShadow = isRenamed)
                names += isRenamed.getOrElse(named.getName)
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

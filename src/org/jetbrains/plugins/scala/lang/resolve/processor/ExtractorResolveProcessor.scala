package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import psi.api.base.ScReferenceElement
import psi.api.statements._
import com.intellij.psi._
import params.ScParameter
import psi.types._

import result.TypingContext
import scala._
import collection.mutable.HashSet
import collection.Set
import psi.api.base.patterns.ScBindingPattern
import psi.api.toplevel.typedef.ScObject

class ExtractorResolveProcessor(ref: ScReferenceElement,
                                refName: String,
                                kinds: Set[ResolveTargets.Value],
                                expected: Option[ScType])
        extends ResolveProcessor(kinds, ref, refName) {

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
      named match {
        case o: ScObject if o.isPackageObject => return true
        case obj: ScObject =>
          var seq = obj.signaturesByName("unapply").map {sign =>
            val m = sign.method
            val subst = sign.substitutor
            new ScalaResolveResult(m, getSubst(state).followed(subst), getImports(state),
              fromType = getFromType(state), parentElement = Some(obj))
          }
          addResults(seq)
          //unapply has bigger priority then unapplySeq
          if (candidatesSet.isEmpty)
          seq = obj.signaturesByName("unapplySeq").map {sign =>
            val m = sign.method
            val subst = sign.substitutor
            new ScalaResolveResult(m, getSubst(state).followed(subst), getImports(state),
              fromType = getFromType(state), parentElement = Some(obj))
          }
          addResults(seq)
          return true
        case bind: ScBindingPattern =>
          addResult(new ScalaResolveResult(bind, getSubst(state), getImports(state), fromType = getFromType(state)))
        case param: ScParameter =>
          addResult(new ScalaResolveResult(param, getSubst(state), getImports(state), fromType = getFromType(state)))
        case _ => return true
      }
    }
    return true
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    val candidates: HashSet[ScalaResolveResult] = candidatesSet ++ levelSet
    expected match {
      case Some(tp) =>
        def isApplicable(r: ScalaResolveResult): Boolean = {
          r.element match {
            case fun: ScFunction =>
              val clauses = fun.paramClauses.clauses
              if (clauses.length != 0 && clauses.apply(0).parameters.length == 1) {
                for (paramType <- clauses(0).parameters.apply(0).getType(TypingContext.empty)
                     if tp conforms r.substitutor.subst(paramType)) return true
              }
              return false
            case _ => return true
          }
        }
        val filtered = candidates.filter(t => isApplicable(t))
        if (filtered.size == 0) return candidates
        else if (filtered.size == 1) return filtered
        else {
          new MostSpecificUtil(ref, 1).mostSpecificForResolveResult(filtered) match {
            case Some(r) => return HashSet(r)
            case None => return candidates
          }
        }
      case _ => return candidates
    }
  }
}

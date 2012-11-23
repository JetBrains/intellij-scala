package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import psi.api.base.ScReferenceElement
import psi.api.statements._
import com.intellij.psi._
import psi.types._

import result.TypingContext
import scala._
import collection.mutable.HashSet
import collection.Set
import psi.api.toplevel.typedef.ScObject
import psi.api.toplevel.ScTypedDefinition

class ExtractorResolveProcessor(ref: ScReferenceElement,
                                refName: String,
                                kinds: Set[ResolveTargets.Value],
                                expected: Option[ScType])
        extends ResolveProcessor(kinds, ref, refName) {

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      val accessible = isAccessible(named, ref)
      if (accessibility && !accessible) return true

      def resultsForTypedDef(obj: ScTypedDefinition) {
        def resultsFor(unapplyName: String) = {
          val typeResult = obj.getType(TypingContext.empty)
          val processor = new CollectMethodsProcessor(ref, unapplyName)
          typeResult.foreach(t => processor.processType(t, ref))
          val sigs = processor.candidatesS.flatMap {
            case ScalaResolveResult(meth: PsiMethod, subst) => Some((meth, subst, Some(obj)))
            case _ => None
          }.toSeq
          addResults(sigs.map {
            case (m, subst, parent) =>
              new ScalaResolveResult(m, getSubst(state).followed(subst), getImports(state),
                fromType = getFromType(state), parentElement = parent, isAccessible = accessible)
          })
        }
        resultsFor("unapply")
        if (candidatesSet.isEmpty) // unapply has higher priority then unapplySeq
          resultsFor("unapplySeq")
      }

      named match {
        case o: ScObject if o.isPackageObject =>
        case td: ScTypedDefinition => resultsForTypedDef(td)
        case _ =>
      }
    }
    true
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    val candidates: Set[ScalaResolveResult] = super.candidatesS
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

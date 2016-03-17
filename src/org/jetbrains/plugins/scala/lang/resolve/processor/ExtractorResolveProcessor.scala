package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.FakeCompanionClassOrCompanionClass
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_11

import scala.collection.{Set, mutable}

class ExtractorResolveProcessor(ref: ScReferenceElement,
                                refName: String,
                                kinds: Set[ResolveTargets.Value],
                                expected: Option[ScType])
                               (implicit override val typeSystem: TypeSystem)
        extends ResolveProcessor(kinds, ref, refName) {

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      val accessible = isAccessible(named, ref)
      if (accessibility && !accessible) return true

      def resultsForTypedDef(obj: ScTypedDefinition) {
        def resultsFor(unapplyName: String) = {
          val typeResult = getFromType(state) match {
            case Some(tp) => Success(ScProjectionType(tp, obj, superReference = false), Some(obj))
            case _ => obj.getType(TypingContext.empty)
          }
          val processor = new CollectMethodsProcessor(ref, unapplyName)
          typeResult.foreach(t => processor.processType(t, ref))
          val sigs = processor.candidatesS.flatMap {
            case ScalaResolveResult(meth: PsiMethod, subst) => Some((meth, subst, Some(obj)))
            case _ => None
          }.toSeq
          addResults(sigs.map {
            case (m, subst, parent) =>
              val resolveToMethod = new ScalaResolveResult(m, subst, getImports(state),
                fromType = getFromType(state), parentElement = parent, isAccessible = accessible)
              val resolveToNamed = new ScalaResolveResult(named, subst, getImports(state),
                fromType = getFromType(state), parentElement = parent, isAccessible = accessible)

              resolveToMethod.copy(innerResolveResult = Option(resolveToNamed))
          })
        }
        resultsFor("unapply")
        if (candidatesSet.isEmpty) {
          // unapply has higher priority then unapplySeq
          resultsFor("unapplySeq")
        }
        if (candidatesSet.isEmpty) {
          obj match {
            case FakeCompanionClassOrCompanionClass(cl: ScClass)
              if cl.tooBigForUnapply && cl.scalaLanguageLevel.exists(_ >= Scala_2_11) =>
                addResult(new ScalaResolveResult(named, ScSubstitutor.empty, getImports(state),
                  fromType = getFromType(state), parentElement = Option(obj), isAccessible = accessible))
            case _ =>
          }
        }
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
              false
            case _ => true
          }
        }
        val filtered = candidates.filter(t => isApplicable(t))
        if (filtered.size == 0) candidates
        else if (filtered.size == 1) filtered
        else {
          new MostSpecificUtil(ref, 1).mostSpecificForResolveResult(filtered, expandInnerResult = false) match {
            case Some(r) => mutable.HashSet(r)
            case None => candidates
          }
        }
      case _ => candidates
    }
  }
}

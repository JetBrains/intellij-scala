package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.FakeCompanionClassOrCompanionClass
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_11

import scala.collection.{Set, mutable}

class ExtractorResolveProcessor(ref: ScReference,
                                refName: String,
                                kinds: Set[ResolveTargets.Value],
                                expected: Option[ScType])
        extends ResolveProcessor(kinds, ref, refName) {

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {
    if (nameMatches(namedElement)) {
      val accessible = isAccessible(namedElement, ref)
      if (accessibility && !accessible) return true

      def resultsForTypedDef(obj: ScTypedDefinition): Unit = {
        def resultsFor(unapplyName: String) = {
          val typeResult = state.fromType match {
            case Some(tp) => Right(ScProjectionType(tp, obj))
            case _        => obj.`type`()
          }

          val processor = new CollectMethodsProcessor(ref, unapplyName)
          typeResult.foreach(t => processor.processType(t, ref))

          val sigs = processor.candidatesS.flatMap {
            case ScalaResolveResult(meth: PsiMethod, subst) => Some((meth, subst, Some(obj)))
            case _                                          => None
          }.toSeq

          addResults(sigs.map {
            case (m, subst, parent) =>
              val resolveToMethod = new ScalaResolveResult(m, subst, state.importsUsed,
                fromType = state.fromType, parentElement = parent, isAccessible = accessible)
              val resolveToNamed = new ScalaResolveResult(namedElement, subst, state.importsUsed,
                fromType = state.fromType, parentElement = parent, isAccessible = accessible)

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
              addResult(new ScalaResolveResult(namedElement, ScSubstitutor.empty, state.importsUsed,
                  fromType = state.fromType, parentElement = Option(obj), isAccessible = accessible))
            case _ =>
          }
        }
      }

      namedElement match {
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
              val undefSubst = ScSubstitutor.bind(fun.typeParameters)(UndefinedType(_))
              val subst      = r.substitutor.followed(undefSubst)
              val clauses = fun.paramClauses.clauses
              if (clauses.nonEmpty && clauses.head.parameters.length == 1) {
                val paramTpe = clauses.head.parameters.head.`type`()
                paramTpe.exists(t => tp.conforms(subst(t)))
              } else false
            case _ => true
          }
        }

        val filtered = candidates.filter(isApplicable)

        if (filtered.isEmpty)        candidates
        else if (filtered.size == 1) filtered
        else MostSpecificUtil(ref, 1).mostSpecificForResolveResult(filtered, expandInnerResult = false) match {
          case Some(r) => mutable.HashSet(r)
          case None    => candidates
        }
      case _ => candidates
    }
  }
}

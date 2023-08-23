package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScConstructorPattern, ScInfixPattern, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult, ScalaResolveState, StdKinds}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt

import scala.collection.mutable.ArrayBuffer

/**
 * This class is useful for finding actual methods for unapply or unapplySeq, in case for values:
 * <code>
 *   val a: Regex
 *   z match {
 *     case a() =>
 *   }
 * </code>
 * This class cannot be used for actual resolve, because reference to value should work to this value, not to
 * invoked unapply method.
 */
class ExpandedExtractorResolveProcessor(
  ref:      ScReference,
  refName:  String,
  kinds:    Set[ResolveTargets.Value],
  expected: Option[ScType]
) extends ExtractorResolveProcessor(ref, refName, kinds, expected) {

  override protected def execute(namedElement: PsiNamedElement)(implicit state: ResolveState): Boolean = {
    if (nameMatches(namedElement)) {
      val accessible = isAccessible(namedElement, ref)
      if (accessibility && !accessible) return true
      namedElement match {
        case bind: ScTypedDefinition =>
          val parentSubst = state.substitutor
          val parentImports = state.importsUsed
          val typez = state.fromType match {
            case Some(tp) => ScProjectionType(tp, bind)
            case _        => bind.`type`().getOrAny
          }

          var seq    = false
          val buffer = new ArrayBuffer[ScalaResolveResult]
          val proc   = new BaseProcessor(StdKinds.methodRef) {

            override protected def execute(namedElement: PsiNamedElement)(implicit state: ResolveState): Boolean = {
              namedElement match {
                case fun: ScFunction if fun.name == "unapply" || (seq && fun.name == "unapplySeq") =>
                  buffer += new ScalaResolveResult(
                    fun,
                    parentSubst.followed(state.substitutor),
                    parentImports,
                    parentElement = Some(bind),
                    isAccessible = accessible
                  )
                case _ =>
              }
              true
            }
          }
          proc.processType(parentSubst(typez), ref, ScalaResolveState.empty)
          addResults(buffer)

          if (candidatesSet.isEmpty && levelSet.isEmpty) {
            buffer.clear()
            seq = true
            proc.processType(parentSubst(typez), ref, ScalaResolveState.empty)
            addResults(buffer)
          }

        case _ => return super.execute(namedElement)
      }
    }
    true
  }
}

object ExpandedExtractorResolveProcessor {
  def resolveActualUnapply(patRef: ScStableCodeReference): Option[ScalaResolveResult] =
    patRef.bind() match {
      case Some(ScalaResolveResult(_: ScBindingPattern | _: ScParameter, _)) =>
        val resolve =
          patRef.doResolve(
            new ExpandedExtractorResolveProcessor(
              patRef,
              patRef.refName,
              patRef.getKinds(incomplete = false),
              patRef.getContext match {
                case inf: ScInfixPattern          => inf.expectedType
                case constr: ScConstructorPattern => constr.expectedType
                case _                            => None
              }
            )
          )

        resolve match {
          case Array(r) => Some(r)
          case _        => None
        }
      case m => m
    }
}

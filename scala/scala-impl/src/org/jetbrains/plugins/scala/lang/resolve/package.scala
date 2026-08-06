package org.jetbrains.plugins.scala.lang

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScGenericCall, ScParenthesisedExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction

import scala.annotation.tailrec

package object resolve {
  @tailrec
  private[lang] def referenceTargetDeep(expr: ScExpression): Option[ScReferenceExpression] = expr match {
    case ref: ScReferenceExpression => Some(ref)
    case gen: ScGenericCall         => referenceTargetDeep(gen.referencedExpr)
    case inv: MethodInvocation      => referenceTargetDeep(inv.getEffectiveInvokedExpr)
    case paren: ScParenthesisedExpr =>
      paren.innerElement match {
        case Some(inner) => referenceTargetDeep(inner)
        case None        => None
      }
    case _ => None
  }

  implicit class ScalaResolveResultUtils(private val srr: ScalaResolveResult) extends AnyVal {
    def elementHasParameters: Boolean =
      srr.element match {
        case synthetic: ScSyntheticFunction         => synthetic.paramClauses.nonEmpty
        case fn: ScFunction if !srr.isExtensionCall => fn.parameterClausesWithExtension().nonEmpty
        case m: PsiMethod                           => m.hasParameters
        case _                                      => false
      }

    def elementHasTypeParameters: Boolean =
      srr.element match {
        case synthetic: ScSyntheticFunction         => synthetic.typeParameters.nonEmpty
        case fn: ScFunction if !srr.isExtensionCall => fn.typeParametersWithExtension().nonEmpty
        case m: PsiMethod                           => m.hasTypeParameters
        case _                                      => false
      }

    def functionParamClauses: Seq[ScParameterClause] =
      srr.element match {
        case fun: ScFunction =>
          if (srr.shouldDropExtensionClauses) fun.paramClauses.clauses
          else                                fun.parameterClausesWithExtension(srr.exportedInExtension)
        case cons: ScMethodLike => cons.effectiveParameterClauses
        case _ => Seq.empty
      }
  }
}

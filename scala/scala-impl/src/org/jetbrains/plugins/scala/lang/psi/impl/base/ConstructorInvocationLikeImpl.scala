package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cached}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ConstructorInvocationLike, JavaConstructor, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

trait ConstructorInvocationLikeImpl extends ConstructorInvocationLike {
  override def matchedParameters: Seq[(ScExpression, Parameter)] = matchedParametersByClauses.flatten

  @Nullable
  protected def resolveConstructor(): PsiElement

  def matchedParametersByClauses: Seq[Seq[(ScExpression, Parameter)]] = _matchedParametersByClauses()

  private val _matchedParametersByClauses = cached("matchedParametersByClauses", BlockModificationTracker(this), () => {
    val paramClauses = resolveConstructor() match {
      case ScalaConstructor(constr) => constr.effectiveParameterClauses.map(_.effectiveParameters)
      case JavaConstructor(constr)  => Seq(constr.parameters)
      case _                        => Seq.empty
    }
    (for {
      (paramClause, argList) <- paramClauses.zip(arguments)
    } yield {
      for ((arg, idx) <- argList.exprs.zipWithIndex) yield
        arg match {
          case ScAssignment(refToParam: ScReferenceExpression, Some(expr)) =>
            val param = paramClause.find(_.name == refToParam.refName)
              .orElse(refToParam.resolve().asOptionOf[ScParameter])
            param.map(p => (expr, Parameter(p))).toSeq
          case expr =>
            val paramIndex = Math.min(idx, paramClause.size - 1)
            paramClause.lift(paramIndex).map(p => (expr, Parameter(p))).toSeq
        }
    }).map(_.flatten)
  })
}

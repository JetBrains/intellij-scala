package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ConstructorInvocationLike, JavaConstructor, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.macroAnnotations.Cached

trait ConstructorInvocationLikeImpl extends ConstructorInvocationLike {
  override def matchedParameters: collection.Seq[(ScExpression, Parameter)] = matchedParametersByClauses.flatten

  @Nullable
  protected def resolveConstructor(): PsiElement

  @Cached(BlockModificationTracker(this), this)
  def matchedParametersByClauses: collection.Seq[collection.Seq[(ScExpression, Parameter)]] = {
    val paramClauses = resolveConstructor() match {
      case ScalaConstructor(constr) => constr.effectiveParameterClauses.map(_.effectiveParameters)
      case JavaConstructor(constr)  => collection.Seq(constr.parameters)
      case _                        => collection.Seq.empty
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
  }
}

package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cached}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ConstructorInvocationLike, JavaConstructor, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

trait ConstructorInvocationLikeImpl extends ConstructorInvocationLike {
  override def matchedParameters: Seq[(ScExpression, Parameter)] = matchedParametersByClauses.flatten

  @Nullable
  protected def resolveConstructor(): PsiElement

  def matchedParametersByClauses: Seq[Seq[(ScExpression, Parameter)]] = _matchedParametersByClauses()

  private val _matchedParametersByClauses = cached("matchedParametersByClauses", BlockModificationTracker(this), () => {
    resolveConstructor() match {
      case ScalaConstructor(constr) =>
        val paramClauses = constr.effectiveParameterClauses
        val argsByClause = arguments.map(_.exprs)

        argsByClause.mapWithIndex {
          case (args, argClauseIdx) =>
            val maybeParamClause =
              Compatibility.correspondingParamClause(paramClauses, argsByClause, argClauseIdx)

            for {
              paramClause         <- maybeParamClause.toSeq
              params              = paramClause.effectiveParameters
              (arg, argIdx)       <- args.zipWithIndex
              (param, matchedArg) <- arg match {
                case ScAssignment(refToParam: ScReferenceExpression, Some(expr)) =>
                  val param =
                    params
                      .find(_.name == refToParam.refName)
                      .orElse(refToParam.resolve().asOptionOf[ScParameter])

                  param.map(p => (expr, Parameter(p))).toSeq
                case expr =>
                  val paramIndex = Math.min(argIdx, params.size - 1)
                  params.lift(paramIndex).map(p => (expr, Parameter(p))).toSeq
              }
            } yield (param, matchedArg)
        }
      case JavaConstructor(constr)  =>
        val parameters = constr.parameters.map(Parameter(_))
        val args       = arguments.headOption.toSeq.flatMap(_.exprs)
        Seq(args.zip(parameters))
      case _ => Seq.empty
    }
  })
}

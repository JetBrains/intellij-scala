package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}

import scala.language.implicitConversions

private abstract class DepMethodParamSubstitution extends SimpleUpdate {

  def substitutedType(parameter: ScParameter): Option[ScType]

  override def apply(tpe: ScType): AfterUpdate = tpe match {
    case proj @ ScProjectionType(_, p: ScParameter) => ReplaceWith(substitutedType(p).getOrElse(proj))
    case d @ ScDesignatorType(p: ScParameter)       => ReplaceWith(substitutedType(p).getOrElse(d))
    case _                                          => ProcessSubtypes
  }
}

private case class ParamsToExprs(params: Seq[Parameter], exprs: Seq[Expression], useExpected: Boolean)
  extends DepMethodParamSubstitution {

  override def substitutedType(parameter: ScParameter): Option[ScType] = {
    val idx = params.indexWhere(_.paramInCode.contains(parameter))
    val expr = exprs.lift(idx)
    val expectedType = if (useExpected) params.lift(idx).map(_.expectedType) else None

    expr.map(_.getTypeAfterImplicitConversion(checkImplicits = true, isShape = false, expectedType).tr.getOrAny)
  }
}

private case class ParamToParam(fromParams: Seq[ScParameter], toParams: Seq[ScParameter]) extends DepMethodParamSubstitution {
  override def substitutedType(parameter: ScParameter): Option[ScType] = {
    val idx = fromParams.indexOf(parameter)
    toParams.lift(idx).map(ScDesignatorType(_))
  }
}

private case class ParamToType(params: Seq[Parameter], types: Seq[ScType]) extends DepMethodParamSubstitution {
  override def substitutedType(parameter: ScParameter): Option[ScType] = {
    val idx = params.indexWhere(_.paramInCode.contains(parameter))
    types.lift(idx)
  }
}
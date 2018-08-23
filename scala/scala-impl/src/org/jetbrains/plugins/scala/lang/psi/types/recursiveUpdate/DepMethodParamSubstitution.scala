package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

import scala.language.implicitConversions

/**
  * Nikolay.Tropin
  * 01-Feb-18
  */
private abstract class DepMethodParamSubstitution extends Substitution {

  def substitutedType(parameter: ScParameter): Option[ScType]

  override protected val subst: PartialFunction[ScType, ScType] = {
    case d @ ScDesignatorType(p: ScParameter) => substitutedType(p).getOrElse(d)
  }
}

private case class ParamsToExprs(params: Seq[Parameter], exprs: Seq[Expression], useExpected: Boolean)
  extends DepMethodParamSubstitution {

  override def substitutedType(parameter: ScParameter): Option[ScType] = {
    val idx = params.indexWhere(_.paramInCode.contains(parameter))
    val expr = exprs.lift(idx)
    val expectedType = if (useExpected) params.lift(idx).map(_.expectedType) else None

    expr.map(_.getTypeAfterImplicitConversion(checkImplicits = true, isShape = false, expectedType)._1.getOrAny)
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
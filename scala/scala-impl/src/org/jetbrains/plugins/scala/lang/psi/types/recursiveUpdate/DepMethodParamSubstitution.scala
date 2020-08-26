package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.{LeafType, ScCompoundType, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

import scala.language.implicitConversions

/**
  * Nikolay.Tropin
  * 01-Feb-18
  */
private abstract class DepMethodParamSubstitution extends LeafSubstitution {

  def substitutedType(parameter: ScParameter): Option[ScType]

  override protected val subst: PartialFunction[LeafType, ScType] = {
    case d @ ScDesignatorType(p: ScParameter) => substitutedType(p).getOrElse(d)
  }
}

private case class ParamsToExprs(params: collection.Seq[Parameter], exprs: collection.Seq[Expression], useExpected: Boolean)
  extends DepMethodParamSubstitution {

  override def substitutedType(parameter: ScParameter): Option[ScType] = {
    val idx = params.indexWhere(_.paramInCode.contains(parameter))
    val expr = exprs.lift(idx)
    val expectedType = if (useExpected) params.lift(idx).map(_.expectedType) else None

    expr.map(_.getTypeAfterImplicitConversion(checkImplicits = true, isShape = false, expectedType).tr.getOrAny)
  }
}

private case class ParamToParam(fromParams: collection.Seq[ScParameter], toParams: collection.Seq[ScParameter]) extends DepMethodParamSubstitution {
  override def substitutedType(parameter: ScParameter): Option[ScType] = {
    val idx = fromParams.indexOf(parameter)
    toParams.lift(idx).map(ScDesignatorType(_))
  }
}

private case class ParamToType(params: collection.Seq[Parameter], types: collection.Seq[ScType]) extends DepMethodParamSubstitution {
  override def substitutedType(parameter: ScParameter): Option[ScType] = {
    val idx = params.indexWhere(_.paramInCode.contains(parameter))
    types.lift(idx)
  }
}
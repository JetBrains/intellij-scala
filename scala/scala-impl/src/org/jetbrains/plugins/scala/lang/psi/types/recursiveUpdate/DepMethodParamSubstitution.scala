package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.DepMethodParamSubstitution.LazyDepMethodTypes

import scala.language.implicitConversions

/**
  * Nikolay.Tropin
  * 01-Feb-18
  */
private case class DepMethodParamSubstitution(depMethodTypes: LazyDepMethodTypes) extends Substitution {

  //todo
  override def toString: String = getClass.getSimpleName

  override protected val subst: PartialFunction[ScType, ScType] = {
    case d @ ScDesignatorType(p: ScParameter) =>
      val depMethodParamType = depMethodTypes.value.collectFirst {
        case (parameter: Parameter, tp: ScType) if parameter.paramInCode.contains(p) => tp
      }
      depMethodParamType.getOrElse(d)
  }
}

private object DepMethodParamSubstitution {
  class LazyDepMethodTypes(private var fun: () => Map[Parameter, ScType]) {
    lazy val value: Map[Parameter, ScType] = {
      val res = fun()
      fun = null
      res
    }
  }

  def fromFunction(dependentMethodTypes: () => Map[Parameter, ScType]): Substitution =
    new DepMethodParamSubstitution(new LazyDepMethodTypes(dependentMethodTypes))
}

package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition

case class ExtractMethodParameter(oldName: String, newName: String, fromElement: ScTypedDefinition, tp: ScType,
                                  passAsParameter: Boolean) {

  val isEmptyParamFunction: Boolean = fromElement match {
    case fun: ScFunction => fun.parameters.length == 0
    case _ => false
  }
  val isCallByNameParameter: Boolean = ScalaPsiUtil.nameContext(fromElement) match {
    case v: ScValue if v.hasModifierProperty("lazy") => true
    case p: ScParameter if p.isCallByNameParameter => true
    case _ => false
  }
  val isFunction: Boolean = fromElement.isInstanceOf[ScFunction]
}

object ExtractMethodParameter {

  def from(variableData: ScalaVariableData): ExtractMethodParameter = {
    val element = variableData.element
    ExtractMethodParameter(
      oldName = element.name,
      newName = variableData.name,
      fromElement = element,
      tp = variableData.scType,
      passAsParameter = variableData.passAsParameter
    )
  }

}


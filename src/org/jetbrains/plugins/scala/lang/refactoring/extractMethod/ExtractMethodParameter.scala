package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.util.duplicates.ScalaVariableData
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.03.2010
 */

case class ExtractMethodParameter(oldName: String, newName: String, isRef: Boolean, tp: ScType,
                                  needMirror: Boolean, passAsParameter: Boolean, isFunction: Boolean,
                                  isEmptyParamFunction: Boolean, isCallByNameParameter: Boolean)

object ExtractMethodParameter {

  def from(variableData: ScalaVariableData): ExtractMethodParameter = {
    val element = variableData.element
    val isEmptyParamFun = element match {
      case fun: ScFunction => fun.parameters.length == 0
      case _ => false
    }
    val isCallByName = ScalaPsiUtil.nameContext(element) match {
      case v: ScValue if v.hasModifierProperty("lazy") => true
      case p: ScParameter if p.isCallByNameParameter => true
      case _ => false
    }
    ExtractMethodParameter(
      oldName = element.name,
      newName = variableData.name,
      isRef = false,
      tp = variableData.scType,
      needMirror = variableData.isMutable,
      passAsParameter = variableData.passAsParameter,
      isFunction = element.isInstanceOf[ScFunction],
      isEmptyParamFunction = isEmptyParamFun,
      isCallByNameParameter = isCallByName
    )
  }

}


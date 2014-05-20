package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.util.duplicates.ScalaVariableData
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue}

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.03.2010
 */

case class ExtractMethodOutput(paramName: String, returnType: ScType, needNewDefinition: Boolean,
                               isVal: Boolean)

object ExtractMethodOutput {

  def from(variableData: ScalaVariableData) = {
    val element = variableData.element
    val isVal = ScalaPsiUtil.nameContext(element) match {
      case _: ScValue | _: ScFunction => true
      case _ => false
    }
    ExtractMethodOutput(element.name, variableData.scType, variableData.isInsideOfElements, isVal)
  }
}
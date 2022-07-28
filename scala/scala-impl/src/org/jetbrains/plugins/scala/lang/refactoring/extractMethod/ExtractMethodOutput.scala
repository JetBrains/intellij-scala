package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition

case class ExtractMethodOutput(paramName: String, returnType: ScType, needNewDefinition: Boolean, fromElement: ScTypedDefinition) {

  val isVal: Boolean = ScalaPsiUtil.nameContext(fromElement) match {
    case _: ScValue | _: ScFunction => true
    case _ => false
  }
}

object ExtractMethodOutput {

  def from(variableData: ScalaVariableData): ExtractMethodOutput = {
    val element = variableData.element
    ExtractMethodOutput(element.name, variableData.scType, variableData.isInsideOfElements, element)
  }
}
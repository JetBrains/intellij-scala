package org.jetbrains.plugins.scala.lang.structureView.element

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter

private class ScalaValOrVarParameterStructureViewElement(parameter: ScClassParameter, inherited: Boolean)
  extends ScalaStructureViewElement(parameter, inherited) {

  override def location: Option[String] =
    Option(parameter.containingClass).map(_.name)

  override def getPresentableText: String =
    ScalaItemPresentation.withSimpleNames(parameter.name + parameter.paramType.map(t => ": " + t.getText).mkString)
}

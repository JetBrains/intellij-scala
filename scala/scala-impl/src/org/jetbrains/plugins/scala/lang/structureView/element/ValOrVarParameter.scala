package org.jetbrains.plugins.scala.lang.structureView.element

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter

private class ValOrVarParameter(parameter: ScClassParameter, inherited: Boolean) extends AbstractTreeElement(parameter, inherited) {
  override def location: Option[String] =
    Option(parameter.containingClass).map(_.name)

  override def getPresentableText: String =
    AbstractItemPresentation.withSimpleNames(parameter.name + parameter.paramType.map(t => ": " + t.getText).mkString)
}

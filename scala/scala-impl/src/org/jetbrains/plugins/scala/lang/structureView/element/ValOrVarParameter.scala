package org.jetbrains.plugins.scala.lang.structureView.element

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.structureView.element.AbstractItemPresentation.withSimpleNames

private class ValOrVarParameter(parameter: ScClassParameter, inherited: Boolean)
  extends AbstractTreeElementDelegatingChildrenToPsi(parameter, inherited)
    with InheritedLocationStringItemPresentation {

  override def location: Option[String] =
    Option(parameter.containingClass).map(_.name)

  override def getPresentableText: String =
    withSimpleNames(parameter.name + parameter.paramType.map(t => ": " + t.getText).mkString)
}

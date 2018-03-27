package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.ScalaValOrVarParameterStructureViewElement.Presentation

class ScalaValOrVarParameterStructureViewElement(parameter: ScClassParameter, inherited: Boolean)
  extends ScalaStructureViewElement(parameter, inherited) {

  override def getPresentation: ItemPresentation = new Presentation(parameter, inherited)
}

private object ScalaValOrVarParameterStructureViewElement {
  class Presentation(parameter: ScClassParameter, inherited: Boolean) extends ScalaItemPresentation(parameter, inherited) {

    override def location: Option[String] =
      Option(parameter.containingClass).map(_.name)

    override def getPresentableText: String =
      ScalaItemPresentation.withSimpleNames(parameter.name + parameter.paramType.map(t => ": " + t.getText).mkString)
  }
}
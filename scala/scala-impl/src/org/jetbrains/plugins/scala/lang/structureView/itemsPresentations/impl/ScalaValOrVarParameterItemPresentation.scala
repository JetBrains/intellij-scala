package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.ScalaItemPresentation

class ScalaValOrVarParameterItemPresentation(parameter: ScClassParameter, inherited: Boolean) extends ScalaItemPresentation(parameter) {
  override def getPresentableText: String =
    parameter.name + parameter.paramType.map(t => ": " + t.getText).mkString

  override def getTextAttributesKey: TextAttributesKey =
    if (inherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
}

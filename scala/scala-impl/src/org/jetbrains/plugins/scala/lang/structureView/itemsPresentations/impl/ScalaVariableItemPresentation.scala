package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import javax.swing.Icon

import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.ScalaItemPresentation

/**
* @author Alexander Podkhalyuzin
* Date: 05.05.2008
*/

class ScalaVariableItemPresentation(element: ScNamedElement, inherited: Boolean) extends ScalaItemPresentation(element) {
  override def getPresentableText: String =
    element.nameId.getText + variable.flatMap(_.typeElement.map(_.getText)).map(": " + _).mkString

  override def getIcon(open: Boolean): Icon =
    variable.map(_.getIcon(0)).orNull

  private def variable = element.parentsInFile.findByType[ScVariable]

  override def getTextAttributesKey: TextAttributesKey =
    if (inherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
}
package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import javax.swing.Icon

import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.structureView.ScalaElementPresentation
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.ScalaItemPresentation

/**
* @author Alexander Podkhalyuzin
* Date: 05.05.2008
*/

class ScalaVariableItemPresentation(element: ScNamedElement, inherited: Boolean) extends ScalaItemPresentation(element) {
  override def getPresentableText: String =
    ScalaElementPresentation.getPresentableText(element.nameId)

  override def getIcon(open: Boolean): Icon =
    element.parentsInFile.findByType[ScVariable].map(_.getIcon(0)).orNull

  override def getTextAttributesKey: TextAttributesKey =
    if (inherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
}
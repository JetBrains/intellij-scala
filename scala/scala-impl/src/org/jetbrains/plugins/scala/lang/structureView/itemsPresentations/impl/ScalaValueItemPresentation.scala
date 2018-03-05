package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import javax.swing.Icon

import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.ScalaItemPresentation

/**
* @author Alexander Podkhalyuzin
* Date: 08.05.2008
*/

class ScalaValueItemPresentation(element: ScNamedElement, inherited: Boolean) extends ScalaItemPresentation(element) {
  override def getPresentableText: String = {
    val typeAnnotation = value.flatMap(_.typeElement.map(_.getText))

    def inferredType = value.flatMap(_.`type`().toOption).map(ScTypePresentation.withoutAliases)

    element.nameId.getText + typeAnnotation.orElse(inferredType).map(": " + _).mkString
  }

  override def getIcon(open: Boolean): Icon =
    value.map(_.getIcon(0)).orNull

  private def value = element.parentsInFile.findByType[ScValue]

  override def getTextAttributesKey: TextAttributesKey =
    if (inherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
}

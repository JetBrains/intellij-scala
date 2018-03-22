package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import javax.swing.Icon

import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import com.intellij.openapi.util.Iconable
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.ScalaItemPresentation
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.ScalaItemPresentation.withSimpleNames

/**
* @author Alexander Podkhalyuzin
* Date: 05.05.2008
*/

class ScalaVariableItemPresentation(element: ScNamedElement, inherited: Boolean, showType: Boolean)
  extends ScalaItemPresentation(element, inherited) {

  override def location: Option[String] = variable.map(_.containingClass).map(_.name)

  override def getPresentableText: String = {
    val typeAnnotation = variable.flatMap(_.typeElement.map(_.getText))

    def inferredType = if (showType) variable.flatMap(_.`type`().toOption).map(ScTypePresentation.withoutAliases) else None

    withSimpleNames(element.nameId.getText + typeAnnotation.orElse(inferredType).map(": " + _).mkString)
  }

  override def getIcon(open: Boolean): Icon =
    variable.map(_.getIcon(Iconable.ICON_FLAG_VISIBILITY)).orNull

  private def variable = element.parentsInFile.findByType[ScVariable]

  override def getTextAttributesKey: TextAttributesKey =
    if (inherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
}
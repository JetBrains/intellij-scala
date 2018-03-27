package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.structureView.ScalaElementPresentation

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.07.2008
 */

private class ScalaTypeAliasStructureViewElement(alias: ScTypeAlias, inherited: Boolean) extends ScalaStructureViewElement(alias, inherited)  {
  override def location: Option[String] = Option(element.containingClass).map(_.name)

  override def getPresentableText: String = ScalaElementPresentation.getTypeAliasPresentableText(element)

  override def getTextAttributesKey: TextAttributesKey = if (inherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
}

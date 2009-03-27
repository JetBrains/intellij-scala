package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import com.intellij.openapi.editor.colors.{TextAttributesKey, CodeInsightColors}
import psi.api.statements.ScTypeAlias

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.07.2008
 */
 
class ScalaTypeAliasItemPresentation(private val element: ScTypeAlias, isInherited: Boolean) extends ScalaItemPresentation(element) {
  def getPresentableText() = ScalaElementPresentation.getTypeAliasPresentableText(myElement.asInstanceOf[ScTypeAlias])

  override def getTextAttributesKey(): TextAttributesKey = {
    return if(isInherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
  }
}
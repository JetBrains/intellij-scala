package org.jetbrains.plugins.scala
package lang
package structureView
package itemsPresentations
package impl

import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.07.2008
 */
 
class ScalaTypeAliasItemPresentation(private val element: ScTypeAlias, isInherited: Boolean) extends ScalaItemPresentation(element) {
  def getPresentableText: String = ScalaElementPresentation.getTypeAliasPresentableText(myElement.asInstanceOf[ScTypeAlias])

  override def getTextAttributesKey: TextAttributesKey = {
    if (isInherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
  }
}
package org.jetbrains.plugins.scala
package lang
package structureView
package itemsPresentations
package impl

import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import org.jetbrains.plugins.scala.lang.psi.api.statements._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFunctionItemPresentation(private val element: ScFunction, private val isInherited: Boolean) extends ScalaItemPresentation(element) {
  def getPresentableText: String = {
    ScalaElementPresentation.getMethodPresentableText(myElement.asInstanceOf[ScFunction])
  }
  override def getTextAttributesKey: TextAttributesKey = {
    if (isInherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
  }
}
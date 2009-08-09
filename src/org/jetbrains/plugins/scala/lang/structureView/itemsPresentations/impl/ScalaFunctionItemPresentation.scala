package org.jetbrains.plugins.scala
package lang
package structureView
package itemsPresentations
package impl

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFunctionItemPresentation(private val element: ScFunction, private val isInherited: Boolean) extends ScalaItemPresentation(element) {
  def getPresentableText(): String = {
    return ScalaElementPresentation.getMethodPresentableText(myElement.asInstanceOf[ScFunction])
  }
  override def getTextAttributesKey(): TextAttributesKey = {
    return if(isInherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
  }
}
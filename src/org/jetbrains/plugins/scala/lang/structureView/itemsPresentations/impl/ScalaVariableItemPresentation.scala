package org.jetbrains.plugins.scala
package lang
package structureView
package itemsPresentations
package impl

import javax.swing._

import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import com.intellij.psi._
import org.jetbrains.plugins.scala.icons.Icons

/**
* @author Alexander Podkhalyuzin
* Date: 05.05.2008
*/

class ScalaVariableItemPresentation(private val element: PsiElement, isInherited: Boolean) extends ScalaItemPresentation(element) {
  def getPresentableText: String = {
    ScalaElementPresentation.getPresentableText(myElement)
  }

  override def getIcon(open: Boolean): Icon = {
    Icons.VAR
  }

  override def getTextAttributesKey: TextAttributesKey = {
    if(isInherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
  }
}
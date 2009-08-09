package org.jetbrains.plugins.scala
package lang
package structureView
package itemsPresentations
package impl

import com.intellij.openapi.editor.colors.{TextAttributesKey, CodeInsightColors}
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.psi._
import org.jetbrains.plugins.scala.icons.Icons

import javax.swing._;

/**
* @author Alexander Podkhalyuzin
* Date: 05.05.2008
*/

class ScalaVariableItemPresentation(private val element: PsiElement, isInherited: Boolean) extends ScalaItemPresentation(element) {
  def getPresentableText(): String = {
    return ScalaElementPresentation.getPresentableText(myElement)
  }

  override def getIcon(open: Boolean): Icon = {
    Icons.VAR
  }

  override def getTextAttributesKey(): TextAttributesKey = {
    return if(isInherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
  }
}
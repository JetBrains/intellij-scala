package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import com.intellij.openapi.editor.colors.{TextAttributesKey, CodeInsightColors}
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.psi._
import org.jetbrains.plugins.scala.icons.Icons

import javax.swing._;

/**
* @author Alexander Podkhalyuzin
* Date: 08.05.2008
*/

class ScalaValueItemPresentation(private val element: PsiElement, isInherited: Boolean) extends ScalaItemPresentation(element) {
  def getPresentableText(): String = {
    return ScalaElementPresentation.getPresentableText(myElement)
  }

  override def getIcon(open: Boolean): Icon = {
    Icons.VAL
  }

  override def getTextAttributesKey(): TextAttributesKey = {
    return if(isInherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
  }
}

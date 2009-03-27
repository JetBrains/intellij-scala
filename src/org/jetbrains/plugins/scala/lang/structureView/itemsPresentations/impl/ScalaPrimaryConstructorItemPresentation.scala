package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.icons.Icons

import javax.swing._;

/**
* @author Alexander Podkhalyuzin
* Date: 16.05.2008
*/

class ScalaPrimaryConstructorItemPresentation(private val element: ScPrimaryConstructor) extends ScalaItemPresentation(element) {
  def getPresentableText(): String = {
    return ScalaElementPresentation.getPrimaryConstructorPresentableText(myElement.asInstanceOf[ScPrimaryConstructor])
  }

  override def getIcon(open: Boolean): Icon = {
    Icons.FUNCTION
  }
}
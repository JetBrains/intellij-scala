package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements._

/**
* @author Alexander Podkhalyuzin
* Date: 05.05.2008
*/

class ScalaVariablePresentation(private val element: ScalaPsiElement) extends ScalaItemPresentation(element) {
  def getPresentableText(): String = {
    return ScalaElementPresentation.getVariablePresentableText(myElement.asInstanceOf[ScalaPsiElement])
  }
}
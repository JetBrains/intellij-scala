package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import org.jetbrains.plugins.scala.lang.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFileItemPresentation(private val element: ScalaFile) extends ScalaItemPresentation(element) {
  def getPresentableText(): String = {      
    return ScalaElementPresentation.getFilePresentableText(myElement.asInstanceOf[ScalaFile])
  }
}
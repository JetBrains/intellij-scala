package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import psi._
import psi.api.ScalaFile

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFileItemPresentation(private val element: ScalaFile) extends ScalaItemPresentation(element) {
  def getPresentableText(): String = {      
    return ScalaElementPresentation.getFilePresentableText(myElement.asInstanceOf[ScalaFile])
  }
}
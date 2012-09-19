package org.jetbrains.plugins.scala
package lang
package structureView
package itemsPresentations
package impl

import psi.api.ScalaFile

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFileItemPresentation(private val element: ScalaFile) extends ScalaItemPresentation(element) {
  def getPresentableText: String = {
    ScalaElementPresentation.getFilePresentableText(myElement.asInstanceOf[ScalaFile])
  }
}
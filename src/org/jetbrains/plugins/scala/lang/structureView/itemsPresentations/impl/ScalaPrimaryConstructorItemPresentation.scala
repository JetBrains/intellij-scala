package org.jetbrains.plugins.scala
package lang
package structureView
package itemsPresentations
package impl

import javax.swing._

import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.base._;

/**
* @author Alexander Podkhalyuzin
* Date: 16.05.2008
*/

class ScalaPrimaryConstructorItemPresentation(private val element: ScPrimaryConstructor) extends ScalaItemPresentation(element) {
  def getPresentableText: String = {
    ScalaElementPresentation.getPrimaryConstructorPresentableText(myElement.asInstanceOf[ScPrimaryConstructor])
  }

  override def getIcon(open: Boolean): Icon = {
    Icons.FUNCTION
  }
}
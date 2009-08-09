package org.jetbrains.plugins.scala
package lang
package structureView
package itemsPresentations
package impl

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaPackagingItemPresentation(private val element: ScPackaging) extends ScalaItemPresentation(element) {
  def getPresentableText(): String = {
    return ScalaElementPresentation.getPackagingPresentableText(myElement.asInstanceOf[ScPackaging])
  }
}
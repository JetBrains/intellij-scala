package org.jetbrains.plugins.scala
package lang
package structureView
package itemsPresentations
package impl

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

/**
 * @author Alexander Podkhalyuzin
 * @deprecated
 * Date : 04.05.2008
 */

class ScalaPackagingItemPresentation(private val element: ScPackaging) extends ScalaItemPresentation(element) {
  def getPresentableText: String = {
    ScalaElementPresentation.getPackagingPresentableText(myElement.asInstanceOf[ScPackaging])
  }
}
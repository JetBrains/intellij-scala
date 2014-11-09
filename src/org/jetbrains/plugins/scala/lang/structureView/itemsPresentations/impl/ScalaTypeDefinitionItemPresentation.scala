package org.jetbrains.plugins.scala
package lang
package structureView
package itemsPresentations
package impl

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaTypeDefinitionItemPresentation(private val element: ScTypeDefinition) extends ScalaItemPresentation(element) {
  def getPresentableText: String = {
    ScalaElementPresentation.getTypeDefinitionPresentableText(myElement.asInstanceOf[ScTypeDefinition])
  }
}
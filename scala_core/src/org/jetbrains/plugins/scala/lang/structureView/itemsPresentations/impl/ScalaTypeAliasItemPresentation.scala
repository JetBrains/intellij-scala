package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import psi.api.statements.ScTypeAlias

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.07.2008
 */
 
class ScalaTypeAliasItemPresentation(private val element: ScTypeAlias) extends ScalaItemPresentation(element) {
  def getPresentableText(): String = {
    return ScalaElementPresentation.getTypeAliasPresentableText(myElement.asInstanceOf[ScTypeAlias])
  }
}
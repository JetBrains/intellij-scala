package org.jetbrains.plugins.scala.lang.psi.api.base.types

import psi.ScalaPsiElement
import statements.{ScDeclaration, ScTypeAlias}

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScRefinement extends ScalaPsiElement {
  def declarations() = findChildrenByClass(classOf[ScDeclaration])
  def types() = findChildrenByClass(classOf[ScTypeAlias])
}
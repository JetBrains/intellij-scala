package org.jetbrains.plugins.scala.lang.psi.api.base.types

import psi.ScalaPsiElement
import statements.ScDeclaration

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScExistentialClause extends ScalaPsiElement {
  def declarations : Seq[ScDeclaration] = Seq(findChildrenByClass(classOf[ScDeclaration]): _*)
}
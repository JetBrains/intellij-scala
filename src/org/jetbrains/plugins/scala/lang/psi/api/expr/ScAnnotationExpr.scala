package org.jetbrains.plugins.scala.lang.psi.api.expr

import base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScAnnotationExpr extends ScalaPsiElement {
  def constr = findChildByClass(classOf[ScConstructor])
  def getAttributes: Seq[ScNameValuePair] = Seq(findChildrenByClass(classOf[ScNameValuePair]): _*)
}
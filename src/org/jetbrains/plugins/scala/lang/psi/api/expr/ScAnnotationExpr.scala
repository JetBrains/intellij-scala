package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScAnnotationExpr extends ScalaPsiElement {
  def constr = findChildByClassScala(classOf[ScConstructor])
  def getAttributes: Seq[ScNameValuePair] = collection.immutable.Sequence(findChildrenByClassScala(classOf[ScNameValuePair]).toSeq: _*)
}
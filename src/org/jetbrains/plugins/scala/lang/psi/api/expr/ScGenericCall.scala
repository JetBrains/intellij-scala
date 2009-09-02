package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import base.types.{ScTypeElement, ScTypeArgs}

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScGenericCall extends ScExpression {

  def referencedExpr = findChildByClass(classOf[ScExpression])

  def typeArgs = findChild(classOf[ScTypeArgs])

  def arguments : Seq[ScTypeElement] = (for (t <- typeArgs) yield t.typeArgs) match {
    case Some(x) => x
    case _ => Nil
  }

}
package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import base.types.{ScTypeElement, ScTypeArgs}
import types.result.TypeResult
import types.ScType

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScGenericCall extends ScExpression {

  def referencedExpr = findChildByClassScala(classOf[ScExpression])

  def typeArgs = findChild(classOf[ScTypeArgs])

  def arguments : Seq[ScTypeElement] = (for (t <- typeArgs) yield t.typeArgs) match {
    case Some(x) => x
    case _ => Nil
  }

  def shapeType: TypeResult[ScType]

  def shapeMultiType: Array[TypeResult[ScType]]

  def multiType: Array[TypeResult[ScType]]
}
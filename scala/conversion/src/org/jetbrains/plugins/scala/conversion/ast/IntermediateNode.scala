package org.jetbrains.plugins.scala
package conversion
package ast

import scala.collection.mutable

abstract class IntermediateNode {

  import IntermediateNode._

  val comments: Comments = Comments()

  def setComments(c: Comments): Unit = {
    comments.beforeComments ++= c.beforeComments
    comments.afterComments ++= c.afterComments
    comments.latestComments ++= c.latestComments
  }
}

object IntermediateNode {

  case class Comments(
    beforeComments: mutable.ArrayBuffer[LiteralExpression] = mutable.ArrayBuffer.empty[LiteralExpression],
    afterComments: mutable.ArrayBuffer[LiteralExpression] = mutable.ArrayBuffer.empty[LiteralExpression],
    latestComments: mutable.ArrayBuffer[LiteralExpression] = mutable.ArrayBuffer.empty[LiteralExpression]
  )

}


case class EmptyConstruction() extends IntermediateNode

trait TypedElement {
  def getType: TypeConstruction
}
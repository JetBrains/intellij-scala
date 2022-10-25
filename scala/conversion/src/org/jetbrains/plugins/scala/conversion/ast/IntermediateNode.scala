package org.jetbrains.plugins.scala.conversion.ast

import scala.annotation.unused
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

abstract class ExpressionsHolderNode extends IntermediateNode {
  def children: Seq[IntermediateNode]
}

abstract class ExpressionsHolderNodeBase(override val children: Seq[IntermediateNode])
  extends ExpressionsHolderNode

object IntermediateNode {
  case class Comments(
    beforeComments: mutable.ArrayBuffer[LiteralExpression] = mutable.ArrayBuffer.empty[LiteralExpression],
    afterComments: mutable.ArrayBuffer[LiteralExpression] = mutable.ArrayBuffer.empty[LiteralExpression],
    latestComments: mutable.ArrayBuffer[LiteralExpression] = mutable.ArrayBuffer.empty[LiteralExpression]
  ) {
    //For debugging purposes mainly
    @unused
    def nonEmpty: Boolean =
      beforeComments.nonEmpty || afterComments.nonEmpty || latestComments.nonEmpty
  }
}

case class EmptyConstruction() extends IntermediateNode

abstract class TypeNode extends IntermediateNode with TypedElement

case class EmptyTypeNode() extends TypeNode {
  override def getType: TypeConstruction =
    throw new UnsupportedOperationException("getType shouldn't be called for EmptyTypeNode")
}

trait TypedElement {
  def getType: TypeConstruction
}
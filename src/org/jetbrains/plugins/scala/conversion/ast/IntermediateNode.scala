package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina
  * on 10/21/15
  */

abstract class IntermediateNode {
  def escapeKeyword(name: String): String = if (ScalaNamesUtil.isKeyword(name)) "`" + name + "`" else name

  val comments = Comments.apply

  def setComments(c: Comments): Unit = {
    comments.beforeComments ++= c.beforeComments
    comments.afterComments ++= c.afterComments
    comments.latestCommtets ++= c.latestCommtets
  }

  def setAfterComments(c: ArrayBuffer[LiteralExpression]) = {
    comments.afterComments ++= c
  }

  def setBeforeComments(c: ArrayBuffer[LiteralExpression]) = {
    comments.beforeComments ++= c
  }

  override def hashCode(): Int = System.identityHashCode(this)

  override def equals(other: Any) =
    other.isInstanceOf[IntermediateNode] && this.eq(other.asInstanceOf[IntermediateNode])
}


case class EmptyConstruction() extends IntermediateNode

trait TypedElement {
  def getType: IntermediateNode
}

trait TypedElementWithAssociations extends TypedElement{
  val assocoationMap = ArrayBuffer[(IntermediateNode, Option[String])]()

  def getAssociations = assocoationMap.collect { case (n, Some(value)) => (n, value) }
}
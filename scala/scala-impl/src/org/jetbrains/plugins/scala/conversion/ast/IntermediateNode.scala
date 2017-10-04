package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina
  * on 10/21/15
  */

abstract class IntermediateNode {
  def escapeKeyword(name: String): String = ScalaNamesUtil.escapeKeyword(name)

  val comments: Comments = Comments.apply

  def setComments(c: Comments): Unit = {
    comments.beforeComments ++= c.beforeComments
    comments.afterComments ++= c.afterComments
    comments.latestCommtets ++= c.latestCommtets
  }

  def setAfterComments(c: Seq[LiteralExpression]): Unit = {
    comments.afterComments ++= c
  }

  def setBeforeComments(c: Seq[LiteralExpression]): Unit = {
    comments.beforeComments ++= c
  }
}


case class EmptyConstruction() extends IntermediateNode

trait TypedElement {
  def getType: TypeConstruction
}
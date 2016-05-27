package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * Created by Kate Ustyuzhanina
  * on 10/21/15
  */

abstract class IntermediateNode {
  def escapeKeyword(name: String): String = ScalaNamesUtil.escapeKeyword(name)
}


case class EmptyConstruction() extends IntermediateNode

trait TypedElement {
  def getType: TypeConstruction
}
package org.jetbrains.plugins.scala.conversion.ast

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina
  * on 10/22/15
  */
case class BlockConstruction(statements: Seq[IntermediateNode]) extends IntermediateNode {
  def addStatementBefore(statement: IntermediateNode): beforeStatements.type = {
    beforeStatements += statement
  }

  val beforeStatements: ArrayBuffer[IntermediateNode] = new ArrayBuffer[IntermediateNode]
}

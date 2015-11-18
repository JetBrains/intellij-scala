package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.conversion.PrettyPrinter

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina
  * on 10/22/15
  */
case class BlockConstruction(statements: Seq[IntermediateNode]) extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append("{\n")
    printer.append(beforeStatements.toSeq, "\n", "", "\n", beforeStatements.nonEmpty)
    printer.append(statements, "\n", "", "\n", statements.nonEmpty)
    printer.append("}")
  }

  def addStatementBefore(statement: IntermediateNode) = {
    beforeStatements += statement
  }

  val beforeStatements = new ArrayBuffer[IntermediateNode]
}

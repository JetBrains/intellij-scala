package org.jetbrains.plugins.scala.conversion.ast

case class BlockConstruction(statements: Seq[IntermediateNode])
  extends ExpressionsHolderNodeBase(statements)
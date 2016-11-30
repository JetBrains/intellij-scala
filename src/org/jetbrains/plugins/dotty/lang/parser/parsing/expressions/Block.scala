package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

/**
  * @author adkozlov
  */
object Block extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Block {
  override protected def blockStat = BlockStat
  override protected def resultExpr = ResultExpr
}

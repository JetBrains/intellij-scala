package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

/**
  * @author adkozlov
  */
object Block extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Block {
  override protected val blockStat = BlockStat
  override protected val resultExpr = ResultExpr
}

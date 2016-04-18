package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.{BlockStat, SelfInvocation}

/**
  * @author adkozlov
  */
object ConstrBlock extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.ConstrBlock {
  override protected val selfInvocation = SelfInvocation
  override protected val blockStat = BlockStat
}

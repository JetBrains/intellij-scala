package org.jetbrains.plugins.dotty.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.parser.parsing.types.RefineStat

/**
  * @author adkozlov
  */
object RefinedStatSeq extends org.jetbrains.plugins.scala.lang.parser.parsing.types.RefineStatSeq {
  override protected val refineStat = RefineStat
}

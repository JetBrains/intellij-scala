package org.jetbrains.plugins.dotty.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.parser.parsing.types.RefineStatSeq

/**
  * @author adkozlov
  */
object Refinement extends org.jetbrains.plugins.scala.lang.parser.parsing.types.Refinement {
  override protected val refineStatSeq = RefineStatSeq
}

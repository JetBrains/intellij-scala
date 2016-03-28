package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.AnnotType

/**
  * @author adkozlov
  */
object MixinParents extends org.jetbrains.plugins.scala.lang.parser.parsing.top.MixinParents {
  override protected val annotType = AnnotType
}

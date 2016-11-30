package org.jetbrains.plugins.dotty.lang.parser.parsing.top.template

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.AnnotType

/**
  * @author adkozlov
  */
object MixinParents extends org.jetbrains.plugins.scala.lang.parser.parsing.top.template.MixinParents {
  override protected def annotType = AnnotType
}

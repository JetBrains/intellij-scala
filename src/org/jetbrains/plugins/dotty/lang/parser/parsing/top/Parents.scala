package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.dotty.lang.parser.parsing.types.AnnotType
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.ClassParents

/**
  * @author adkozlov
  */
object Parents extends ClassParents {
  override protected val constructor = Constructor
  override protected val annotType = AnnotType
}

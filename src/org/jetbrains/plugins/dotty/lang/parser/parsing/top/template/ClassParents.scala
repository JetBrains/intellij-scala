package org.jetbrains.plugins.dotty.lang.parser.parsing.top.template

import org.jetbrains.plugins.dotty.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.dotty.lang.parser.parsing.types.AnnotType

/**
  * @author adkozlov
  */
object ClassParents extends org.jetbrains.plugins.scala.lang.parser.parsing.top.template.ClassParents {
  override protected val constructor = Constructor
  override protected val annotType = AnnotType
}

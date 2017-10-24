package org.jetbrains.plugins.dotty.lang.parser.parsing.top.template

import org.jetbrains.plugins.dotty.lang.parser.parsing.base.Constructor
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */
object ClassParents extends org.jetbrains.plugins.scala.lang.parser.parsing.top.template.ClassParents {
  override protected def constructor = Constructor

  override protected def parseParent(builder: ScalaPsiBuilder): Boolean =
    parseFirstParent(builder)
}

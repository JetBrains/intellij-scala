package org.jetbrains.plugins.dotty.lang.parser

import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ElementTypes

/**
  * @author adkozlov
  */
object DottyElementTypes extends ElementTypes {
  val REFINED_TYPE = new ScalaElementType("refined type")
  val WITH_TYPE = new ScalaElementType("with type")
  val TYPE_ARGUMENT_NAME = new ScalaElementType("type argument name")
}

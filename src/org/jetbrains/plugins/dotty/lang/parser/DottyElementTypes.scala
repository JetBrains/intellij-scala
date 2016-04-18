package org.jetbrains.plugins.dotty.lang.parser

import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType

/**
  * @author adkozlov
  */
object DottyElementTypes {
  val REFINED_TYPE = new ScalaElementType("refined type")
  val WITH_TYPE = new ScalaElementType("with type")
}

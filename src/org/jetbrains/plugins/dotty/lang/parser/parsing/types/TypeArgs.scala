package org.jetbrains.plugins.dotty.lang.parser.parsing.types

/**
  * @author adkozlov
  */

/*
 * TypeArgs ::= `[' ArgTypes `]'
 */
object TypeArgs extends org.jetbrains.plugins.scala.lang.parser.parsing.types.TypeArgs {
  override protected val `type` = Type
}

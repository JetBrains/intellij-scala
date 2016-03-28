package org.jetbrains.plugins.dotty.lang.parser.parsing.types

/**
  * @author adkozlov
  */

/*
 * ArgTypes ::= ArgType {`,' ArgType}
 */
object ArgTypes extends org.jetbrains.plugins.scala.lang.parser.parsing.types.Types {
  override protected val `type` = ParamType
}

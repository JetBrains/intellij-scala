package org.jetbrains.plugins.dotty.lang.parser.parsing.types

/**
  * @author adkozlov
  */

/*
 * ParamType ::= [`=>'] ParamValueType
 */
object ParamType extends org.jetbrains.plugins.scala.lang.parser.parsing.types.ParamType {
  override protected val `type` = Type
}

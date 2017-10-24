package org.jetbrains.plugins.dotty.lang.parser.parsing.types

/**
  * @author adkozlov
  */

/*
 * Type ::= FunArgTypes `=>' Type | InfixType
 */
object Type extends org.jetbrains.plugins.scala.lang.parser.parsing.types.Type {
  override protected def infixType = InfixType
}

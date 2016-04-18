package org.jetbrains.plugins.dotty.lang.parser.parsing.patterns

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.{InfixType, Type}

/**
  * @author adkozlov
  */
object TypePattern extends org.jetbrains.plugins.scala.lang.parser.parsing.patterns.TypePattern {
  override protected val `type` = Type
  override protected val infixType = InfixType
}

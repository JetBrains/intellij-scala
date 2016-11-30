package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.ParamType

/**
  * @author adkozlov
  */
object Binding extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Binding {
  override protected def paramType = ParamType
}

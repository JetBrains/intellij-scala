package org.jetbrains.plugins.dotty.lang.parser.parsing.params

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.{Annotation, Expr}
import org.jetbrains.plugins.dotty.lang.parser.parsing.types.ParamType

/**
  * @author adkozlov
  */
object Param extends org.jetbrains.plugins.scala.lang.parser.parsing.params.Param {
  override protected val expr = Expr
  override protected val annotation = Annotation
  override protected val paramType = ParamType
}

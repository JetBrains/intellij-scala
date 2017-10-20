package org.jetbrains.plugins.dotty.lang.parser.parsing.params

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.{Annotation, Expr}
import org.jetbrains.plugins.dotty.lang.parser.parsing.types.ParamType

/**
  * @author adkozlov
  */
object Param extends org.jetbrains.plugins.scala.lang.parser.parsing.params.Param {
  override protected def expr = Expr
  override protected def annotation = Annotation
  override protected def paramType = ParamType
}

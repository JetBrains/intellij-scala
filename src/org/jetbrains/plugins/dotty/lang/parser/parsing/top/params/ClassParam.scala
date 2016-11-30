package org.jetbrains.plugins.dotty.lang.parser.parsing.top.params

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.{Annotation, Expr}
import org.jetbrains.plugins.dotty.lang.parser.parsing.types.ParamType

/**
  * @author adkozlov
  */
object ClassParam extends org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ClassParam {
  override protected def expr = Expr
  override protected def annotation = Annotation
  override protected def paramType = ParamType
}

package org.jetbrains.plugins.dotty.lang.parser.parsing.params

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation
import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object TypeParam extends org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParam {
  override protected val annotation = Annotation
  override protected val `type` = Type
  override protected val typeParamClause = TypeParamClause
}

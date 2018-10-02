package org.jetbrains.plugins.dotty.lang.parser.parsing.params

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object TypeParam extends org.jetbrains.plugins.scala.lang.parser.parsing.params.TypeParam {
  override protected def `type` = Type
  override protected def typeParamClause = TypeParamClause
}

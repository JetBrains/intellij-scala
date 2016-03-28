package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.params.TypeParamClause
import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object TypeDcl extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.TypeDcl {
  override protected val `type` = Type
  override protected val typeParamClause = TypeParamClause
}

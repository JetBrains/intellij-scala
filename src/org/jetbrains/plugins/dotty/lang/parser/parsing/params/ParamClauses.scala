package org.jetbrains.plugins.dotty.lang.parser.parsing.params

/**
  * @author adkozlov
  */
object ParamClauses extends org.jetbrains.plugins.scala.lang.parser.parsing.params.ParamClauses {
  override protected val paramClause = ParamClause
  override protected val implicitParamClause = ImplicitParamClause
}

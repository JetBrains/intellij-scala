package org.jetbrains.plugins.dotty.lang.parser.parsing.top.params

/**
  * @author adkozlov
  */
object ClassParamClauses extends org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ClassParamClauses {
  override protected val classParamClause = ClassParamClause
  override protected val implicitClassParamClause = ImplicitClassParamClause
}

package org.jetbrains.plugins.dotty.lang.parser.parsing.top.params

/**
  * @author adkozlov
  */
object ClassParamClauses extends org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ClassParamClauses {
  override protected def classParamClause = ClassParamClause
  override protected def implicitClassParamClause = ImplicitClassParamClause
}

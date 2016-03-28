package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.params.ParamClauses

/**
  * @author adkozlov
  */
object FunSig extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.FunSig {
  override protected val paramClauses = ParamClauses
}

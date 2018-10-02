package org.jetbrains.plugins.dotty.lang.parser.parsing.params

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.ParamType

/**
  * @author adkozlov
  */
object Param extends org.jetbrains.plugins.scala.lang.parser.parsing.params.Param {
  override protected def paramType = ParamType
}

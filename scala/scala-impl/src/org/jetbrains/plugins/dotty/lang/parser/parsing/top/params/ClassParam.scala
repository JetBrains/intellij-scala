package org.jetbrains.plugins.dotty.lang.parser.parsing.top.params

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.ParamType

/**
  * @author adkozlov
  */
object ClassParam extends org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ClassParam {
  override protected def paramType = ParamType
}

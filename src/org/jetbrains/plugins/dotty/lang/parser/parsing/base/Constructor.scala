package org.jetbrains.plugins.dotty.lang.parser.parsing.base

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.ArgumentExprs
import org.jetbrains.plugins.dotty.lang.parser.parsing.types.{AnnotType, SimpleType}

/**
  * @author adkozlov
  */
object Constructor extends org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor {
  override protected val argumentExprs = ArgumentExprs
  override protected val simpleType = SimpleType
  override protected val annotType = AnnotType
}

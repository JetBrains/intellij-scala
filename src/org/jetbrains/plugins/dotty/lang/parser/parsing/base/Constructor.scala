package org.jetbrains.plugins.dotty.lang.parser.parsing.base

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.ArgumentExprs
import org.jetbrains.plugins.dotty.lang.parser.parsing.types.{AnnotType, SimpleType}

/**
  * @author adkozlov
  */
object Constructor extends org.jetbrains.plugins.scala.lang.parser.parsing.base.Constructor {
  override protected def argumentExprs = ArgumentExprs
  override protected def simpleType = SimpleType
  override protected def annotType = AnnotType
}

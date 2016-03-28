package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.base.Constructor

/**
  * @author adkozlov
  */
object AnnotationExpr extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.AnnotationExpr {
  override protected val constructor = Constructor
  override protected val nameValuePair = NameValuePair
}

package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.Type

/**
  * @author adkozlov
  */
object Ascription extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Ascription {
  override protected val annotation = Annotation
  override protected val `type` = Type
}

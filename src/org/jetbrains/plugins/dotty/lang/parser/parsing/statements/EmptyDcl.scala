package org.jetbrains.plugins.dotty.lang.parser.parsing.statements

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation

/**
  * @author adkozlov
  */
object EmptyDcl extends org.jetbrains.plugins.scala.lang.parser.parsing.statements.EmptyDcl {
  override protected val annotation = Annotation
}

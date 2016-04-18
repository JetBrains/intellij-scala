package org.jetbrains.plugins.dotty.lang.parser.parsing.types

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation

/**
  * @author adkozlov
  */

/*
 * AnnotType ::= SimpleType {Annotation}
 */
object AnnotType extends org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType {
  override protected val annotation = Annotation
  override protected val simpleType = SimpleType
}

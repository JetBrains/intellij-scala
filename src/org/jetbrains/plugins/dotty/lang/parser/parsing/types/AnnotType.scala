package org.jetbrains.plugins.dotty.lang.parser.parsing.types

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.Annotation

/**
  * @author adkozlov
  */

/*
 * AnnotType ::= SimpleType {Annotation}
 */
object AnnotType extends org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType {
  override protected def annotation = Annotation
  override protected def simpleType = SimpleType
}

package org.jetbrains.plugins.dotty.lang.parser.parsing.types

/**
  * @author adkozlov
  */

/*
 * AnnotType ::= SimpleType {Annotation}
 */
object AnnotType extends org.jetbrains.plugins.scala.lang.parser.parsing.types.AnnotType {
  override protected def simpleType = SimpleType
}

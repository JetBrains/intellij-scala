package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._

/*
 * AnnotType ::= {Annotation} SimpleType
 */
object AnnotType extends AnnotType {
  override protected def simpleType: SimpleType = SimpleType
}

trait AnnotType {
  protected def simpleType: SimpleType

  final def apply(isPattern: Boolean, multipleSQBrackets: Boolean = true)(implicit builder: ScalaPsiBuilder): Boolean = {
    val annotMarker = builder.mark()
    var isAnnotation = false
    //parse Simple type
    if (simpleType(isPattern, multipleSQBrackets)) {
      val annotationsMarker = builder.mark()
      while (!builder.newlineBeforeCurrentToken && Annotation(countLinesAfterAnnotation = false)) {isAnnotation = true}

      if (isAnnotation) annotationsMarker.done(ScalaElementType.ANNOTATIONS) else annotationsMarker.drop()
      if (isAnnotation) annotMarker.done(ScalaElementType.ANNOT_TYPE) else annotMarker.drop()
      true
    } else {
      annotMarker.rollbackTo()
      false
    }
  }
}
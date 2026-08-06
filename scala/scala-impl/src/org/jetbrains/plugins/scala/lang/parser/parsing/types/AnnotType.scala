package org.jetbrains.plugins.scala.lang.parser.parsing.types

import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._

/*
 * AnnotType ::= {Annotation} SimpleType
 */
object AnnotType {
  final def apply(isPattern: Boolean, multipleSQBrackets: Boolean = true)(implicit builder: ScalaPsiBuilder): Boolean = {
    val annotMarker = builder.mark()
    var isAnnotation = false
    //parse Simple type
    if (SimpleType(isPattern, multipleSQBrackets)) {
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
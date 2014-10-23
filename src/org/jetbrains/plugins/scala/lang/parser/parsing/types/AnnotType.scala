package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * AnnotType ::= {Annotation} SimpleType
 */

object AnnotType {
  def parse(builder: ScalaPsiBuilder, isPattern: Boolean): Boolean = {
    val annotMarker = builder.mark
    var isAnnotation = false
    //parse Simple type
    if (SimpleType.parse(builder, isPattern)) {
      val annotationsMarker = builder.mark
      while (!builder.newlineBeforeCurrentToken && Annotation.parse(builder,
        countLinesAfterAnnotation = false)) {isAnnotation = true}

      if (isAnnotation) annotationsMarker.done(ScalaElementTypes.ANNOTATIONS) else annotationsMarker.drop()
      if (isAnnotation) annotMarker.done(ScalaElementTypes.ANNOT_TYPE) else annotMarker.drop()
      true
    } else {
      annotMarker.rollbackTo()
      false
    }
  }
}